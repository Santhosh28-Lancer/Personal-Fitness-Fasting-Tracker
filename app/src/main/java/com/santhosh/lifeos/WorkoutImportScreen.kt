package com.santhosh.lifeos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

private fun loadWorkoutSheetBitmap(
    context: android.content.Context,
    uri: Uri
): Bitmap? {
    val mime = context.contentResolver.getType(uri).orEmpty().lowercase()
    return if (mime == "application/pdf" || uri.toString().lowercase().endsWith(".pdf")) {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        descriptor.use { parcel ->
            PdfRenderer(parcel).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    val width = (page.width * 2).coerceAtLeast(1)
                    val height = (page.height * 2).coerceAtLeast(1)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                    }
                }
            }
        }
    } else {
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        }
    }
}

/**
 * Removes a small amount of each cell's border, converts it to grayscale,
 * increases text contrast, and enlarges it for ML Kit. Users still review OCR
 * output because tiny/blurred print cannot be recovered reliably by software.
 */
private fun cropAndEnhanceCell(
    source: Bitmap,
    left: Int,
    top: Int,
    right: Int,
    bottom: Int
): Bitmap {
    val cellWidth = (right - left).coerceAtLeast(1)
    val cellHeight = (bottom - top).coerceAtLeast(1)
    val insetX = (cellWidth * 0.025f).toInt().coerceAtMost(cellWidth / 8)
    val insetY = (cellHeight * 0.025f).toInt().coerceAtMost(cellHeight / 8)
    val safeLeft = (left + insetX).coerceAtMost(right - 1)
    val safeTop = (top + insetY).coerceAtMost(bottom - 1)
    val safeRight = (right - insetX).coerceAtLeast(safeLeft + 1)
    val safeBottom = (bottom - insetY).coerceAtLeast(safeTop + 1)

    val crop = Bitmap.createBitmap(
        source,
        safeLeft,
        safeTop,
        (safeRight - safeLeft).coerceAtLeast(1),
        (safeBottom - safeTop).coerceAtLeast(1)
    )
    val scale = 2
    val enlarged = Bitmap.createBitmap(
        (crop.width * scale).coerceAtLeast(1),
        (crop.height * scale).coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(enlarged)
    val grayscale = ColorMatrix().apply { setSaturation(0f) }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(grayscale)
    }
    canvas.drawBitmap(
        crop,
        null,
        android.graphics.Rect(0, 0, enlarged.width, enlarged.height),
        paint
    )
    crop.recycle()
    return enlarged
}

private data class ImportedWorkoutDay(
    val day: Int,
    val text: String
)

@Composable
fun WorkoutImportScreen(
    onBack: () -> Unit,
    onImport: (List<Pair<Int, String>>) -> Unit
) {
    val context = LocalContext.current
    val recognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var rows by remember { mutableStateOf("6") }
    var columns by remember { mutableStateOf("5") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    val days = remember { mutableStateListOf<ImportedWorkoutDay>() }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            imageUri = uri
            days.clear()
            error = null
            status = "File selected. For a PDF, the first page will be analyzed. Tap Analyze sheet."
        }
    }

    DisposableEffect(recognizer) {
        onDispose { recognizer.close() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0D12))
            .padding(18.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("← Back to Fitness", color = Color(0xFFB8F36B))
        }

        Text(
            "Import workout sheet",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Upload a calendar image or PDF. Each cell is enlarged and enhanced before OCR; review every result before importing.",
            color = Color(0xFF9299A8)
        )
        Spacer(Modifier.height(18.dp))

        Button(
            onClick = { picker.launch(arrayOf("image/*", "application/pdf")) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (imageUri == null) "Choose workout image" else "Choose another image")
        }

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = rows,
                onValueChange = { rows = it.filter(Char::isDigit).take(2) },
                label = { Text("Grid rows") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedTextField(
                value = columns,
                onValueChange = { columns = it.filter(Char::isDigit).take(2) },
                label = { Text("Grid columns") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Set the row and column counts to match the sheet grid. For best results, crop away page margins before selecting the file. PDF support currently reads page 1.",
            color = Color(0xFF9299A8),
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                val uri = imageUri
                val r = rows.toIntOrNull()
                val c = columns.toIntOrNull()

                if (uri == null || r == null || c == null ||
                    r !in 1..12 || c !in 1..12
                ) {
                    error = "Choose an image and enter valid grid dimensions."
                    return@Button
                }

                isAnalyzing = true
                error = null
                days.clear()
                status = "Reading image..."

                try {
                    val bitmap = loadWorkoutSheetBitmap(context, uri)

                    if (bitmap == null) {
                        isAnalyzing = false
                        error = "Couldn't open this image."
                        return@Button
                    }

                    val total = r * c
                    var index = 0

                    fun analyzeNextCell() {
                        if (index >= total) {
                            isAnalyzing = false
                            status = "Analysis complete. Review every day before importing."
                            bitmap.recycle()
                            return
                        }

                        val cellIndex = index
                        val row = cellIndex / c
                        val col = cellIndex % c

                        val left = col * bitmap.width / c
                        val top = row * bitmap.height / r
                        val right = (col + 1) * bitmap.width / c
                        val bottom = (row + 1) * bitmap.height / r

                        val cell = cropAndEnhanceCell(
                            source = bitmap,
                            left = left,
                            top = top,
                            right = right,
                            bottom = bottom
                        )

                        recognizer.process(InputImage.fromBitmap(cell, 0))
                            .addOnSuccessListener { result ->
                                days.add(
                                    ImportedWorkoutDay(
                                        day = cellIndex + 1,
                                        text = result.text.trim()
                                    )
                                )
                            }
                            .addOnFailureListener {
                                days.add(
                                    ImportedWorkoutDay(
                                        day = cellIndex + 1,
                                        text = ""
                                    )
                                )
                            }
                            .addOnCompleteListener {
                                cell.recycle()
                                index++
                                status = "Analyzed $index of $total cells"
                                analyzeNextCell()
                            }
                    }

                    analyzeNextCell()
                } catch (e: Exception) {
                    isAnalyzing = false
                    error = "Could not analyze the image: ${e.localizedMessage ?: "unknown error"}"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = imageUri != null && !isAnalyzing
        ) {
            Text(if (isAnalyzing) "Analyzing..." else "Analyze sheet")
        }

        if (isAnalyzing) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (status.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(status, color = Color(0xFFB8F36B))
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color(0xFFFF8A80))
        }

        Spacer(Modifier.height(12.dp))

        if (days.isNotEmpty()) {
            Text(
                "Review extracted days",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(days, key = { it.day }) { day ->
                    var editedText by remember(day.day, days.size) {
                        mutableStateOf(day.text)
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF151923)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "DAY ${day.day}",
                                color = Color(0xFFB8F36B)
                            )
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = editedText,
                                onValueChange = { value ->
                                    editedText = value
                                    val position = days.indexOfFirst {
                                        it.day == day.day
                                    }
                                    if (position >= 0) {
                                        days[position] = day.copy(text = value)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                label = { Text("Exercises and instructions") }
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    onImport(days.map { it.day to it.text })
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isAnalyzing && days.isNotEmpty()
            ) {
                Text("Confirm and import ${days.size} days")
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}