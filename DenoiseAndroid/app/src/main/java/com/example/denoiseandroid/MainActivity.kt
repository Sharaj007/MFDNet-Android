package com.example.denoiseandroid

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.denoiseandroid.ml.MfdnetFloat16
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.abs // Added for your new math
import kotlin.math.ceil

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DenoiseScreen()
        }
    }
}

@Composable
fun DenoiseScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State
    var inputBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rawOutputBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var displayedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var inputRoughness by remember { mutableStateOf("...") }
    var outputRoughness by remember { mutableStateOf("...") }

    var timeTaken by remember { mutableLongStateOf(0L) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var enlargedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var hdrEnabled by remember { mutableStateOf(false) }

    // --- EFFECT: Handle HDR Toggle ---
    LaunchedEffect(rawOutputBitmap, hdrEnabled) {
        if (rawOutputBitmap != null) {
            displayedBitmap = if (hdrEnabled) {
                withContext(Dispatchers.Default) { applySmartHDR(rawOutputBitmap!!) }
            } else {
                rawOutputBitmap
            }
        }
    }

    fun loadNewImage(bitmap: Bitmap) {
        val scaled = scaleBitmapDown(bitmap, 2048)
        inputBitmap = scaled
        rawOutputBitmap = null
        displayedBitmap = null
        progress = 0f
        timeTaken = 0L
        outputRoughness = "..."
        inputRoughness = "Calculating..."
        hdrEnabled = false

        scope.launch(Dispatchers.Default) {
            val r = calculateRoughness(scaled)
            withContext(Dispatchers.Main) { inputRoughness = r }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val stream = context.contentResolver.openInputStream(it)
            val original = BitmapFactory.decodeStream(stream)
            loadNewImage(original)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { loadNewImage(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // HEADER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Image Denoiser", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { showInfoDialog = true }) {
                Icon(Icons.Outlined.Info, contentDescription = "About")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // INPUT
        LabelWithRoughness("Original", inputRoughness)

        Box(
            modifier = Modifier
                .height(200.dp)
                .fillMaxWidth()
                .background(Color(0xFFEEEEEE), RoundedCornerShape(8.dp))
                .clickable { if (inputBitmap != null) enlargedBitmap = inputBitmap },
            contentAlignment = Alignment.Center
        ) {
            if (inputBitmap != null) {
                Image(
                    bitmap = inputBitmap!!.asImageBitmap(),
                    contentDescription = "Input",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text("No Image", color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text("⬇️")
        Spacer(modifier = Modifier.height(5.dp))

        // OUTPUT
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Result ", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if(displayedBitmap != null) {
                    Text("(Roughness: $outputRoughness)", fontSize = 12.sp, color = Color.Gray)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HDR Boost", fontSize = 12.sp, color = if(hdrEnabled) Color(0xFF6200EE) else Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = hdrEnabled,
                    onCheckedChange = { hdrEnabled = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF6200EE)),
                    modifier = Modifier.scale(0.8f)
                )
            }
        }

        Box(
            modifier = Modifier
                .height(200.dp)
                .fillMaxWidth()
                .background(Color(0xFFEEEEEE), RoundedCornerShape(8.dp))
                .clickable { if (displayedBitmap != null) enlargedBitmap = displayedBitmap },
            contentAlignment = Alignment.Center
        ) {
            if (displayedBitmap != null) {
                Image(
                    bitmap = displayedBitmap!!.asImageBitmap(),
                    contentDescription = "Output",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else if (isProcessing) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Denoising...", color = Color.Blue)
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(progress = progress)
                    Text("${(progress * 100).toInt()}%")
                }
            }
        }

        Spacer(modifier = Modifier.height(25.dp))

        // CONTROLS
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) { Text("Gallery") }

            Button(
                onClick = { cameraLauncher.launch(null) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) { Text("Camera") }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = { isProcessing = true },
            enabled = inputBitmap != null && !isProcessing,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))
        ) { Text("Denoise") }

        Spacer(modifier = Modifier.height(10.dp))

        Button(
            onClick = { displayedBitmap?.let { saveToGallery(context, it) } },
            enabled = displayedBitmap != null && !isProcessing,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC5), contentColor = Color.Black)
        ) { Text("Save to Gallery 💾") }
    }

    // FULL SCREEN DIALOG
    if (enlargedBitmap != null) {
        Dialog(
            onDismissRequest = { enlargedBitmap = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { enlargedBitmap = null },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = enlargedBitmap!!.asImageBitmap(),
                    contentDescription = "Full Screen",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                Text("Tap anywhere to close", color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp))
            }
        }
    }

    // ABOUT DIALOG
    if (showInfoDialog) {
        val inR = inputRoughness.toFloatOrNull() ?: 0f
        val outR = outputRoughness.toFloatOrNull() ?: 0f
        val improvement = if(inR > 0) ((inR - outR) / inR) * 100 else 0f

        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text("About the App") },
            text = {
                Column {
                    Text("This app uses MFDNet (Float16) to clean noisy low-light photos.", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Roughness Scale (0 - 15):", fontWeight = FontWeight.Bold)
                    Text("• 0.0 - 3.0: Clean / Smooth", fontSize = 13.sp)
                    Text("• 3.0 - 7.0: Moderate Grain", fontSize = 13.sp)
                    Text("• 7.0 + : Heavy Noise", fontSize = 13.sp)

                    Spacer(modifier = Modifier.height(16.dp))

                    if(outR > 0) {
                        Text("Improvement: ${"%.1f".format(improvement)}%", fontWeight = FontWeight.Bold, color = Color(0xFF6200EE))
                    }

                    Text("Time Taken: ${if (timeTaken > 0) "$timeTaken ms" else "-"}")
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) { Text("Close") }
            }
        )
    }

    // MAIN LOGIC
    LaunchedEffect(isProcessing) {
        if (isProcessing && inputBitmap != null) {
            withContext(Dispatchers.Default) {
                val startTime = System.currentTimeMillis()
                val result = runTiledModel(context, inputBitmap!!) { p -> progress = p }
                val endTime = System.currentTimeMillis()

                val rScore = calculateRoughness(result)

                withContext(Dispatchers.Main) {
                    rawOutputBitmap = result
                    outputRoughness = rScore
                    timeTaken = endTime - startTime
                    isProcessing = false
                }
            }
        }
    }
}

// --- HELPERS ---

@Composable
fun LabelWithRoughness(title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)) { append(title) }
                append("  ")
                withStyle(style = SpanStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, color = Color.Gray)) { append("(Roughness: $value)") }
            }
        )
    }
}

fun applySmartHDR(bitmap: Bitmap): Bitmap {
    val bmp = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(bmp)
    val paint = Paint()
    val matrix = ColorMatrix()

    // 1. Boost Saturation (Color Pop)
    matrix.setSaturation(1.25f)

    // 2. Brightness Boost (Lifts shadows safely)
    val brightness = 20f
    val brightnessMatrix = floatArrayOf(
        1f, 0f, 0f, 0f, brightness,
        0f, 1f, 0f, 0f, brightness,
        0f, 0f, 1f, 0f, brightness,
        0f, 0f, 0f, 1f, 0f
    )
    matrix.postConcat(ColorMatrix(brightnessMatrix))

    paint.colorFilter = ColorMatrixColorFilter(matrix)
    canvas.drawBitmap(bmp, 0f, 0f, paint)
    return bmp
}

// NEW MATH (Perceptual + Edge Excluding)
// Total Variation (TV) Metric
// REPLACES the old function entirely.
// No thresholds, no brightness hacks. Works day and night.
fun calculateRoughness(bitmap: Bitmap): String {
    val width = bitmap.width
    val height = bitmap.height
    // Step 2 is a good balance of speed vs accuracy for a live demo
    val step = 2

    var totalDiff = 0.0
    var count = 0

    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

    for (y in 0 until height - step step step) {
        for (x in 0 until width - step step step) {
            val idx = y * width + x
            val c1 = pixels[idx]

            // 1. Get Brightness of Current Pixel
            val b1 = (0.299 * (c1 shr 16 and 0xFF) +
                    0.587 * (c1 shr 8 and 0xFF) +
                    0.114 * (c1 and 0xFF))

            // 2. Get Brightness of Right Neighbor
            val c2 = pixels[idx + step]
            val b2 = (0.299 * (c2 shr 16 and 0xFF) +
                    0.587 * (c2 shr 8 and 0xFF) +
                    0.114 * (c2 and 0xFF))

            // 3. Get Brightness of Bottom Neighbor
            val c3 = pixels[idx + step * width]
            val b3 = (0.299 * (c3 shr 16 and 0xFF) +
                    0.587 * (c3 shr 8 and 0xFF) +
                    0.114 * (c3 and 0xFF))

            // 4. Calculate Difference (The "Jitter")
            // NO THRESHOLDS. We count every single bit of variation.
            val diffH = Math.abs(b1 - b2)
            val diffV = Math.abs(b1 - b3)

            totalDiff += (diffH + diffV)
            count += 2
        }
    }

    // Prevent division by zero
    val score = if (count > 0) totalDiff / count else 0.0

    // Return format (No clamping, let the number go as high as it needs)
    return "%.1f".format(score)
}

fun runTiledModel(context: Context, fullImage: Bitmap, onProgress: (Float) -> Unit): Bitmap {
    val MODEL_SIZE = 256
    val VALID_SIZE = 240
    val OFFSET = (MODEL_SIZE - VALID_SIZE) / 2

    val width = fullImage.width
    val height = fullImage.height
    val resultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(resultBitmap)
    val model = MfdnetFloat16.newInstance(context)
    val imageProcessor = ImageProcessor.Builder().add(NormalizeOp(0f, 255f)).build()

    val cols = ceil(width.toDouble() / VALID_SIZE).toInt()
    val rows = ceil(height.toDouble() / VALID_SIZE).toInt()
    val totalTiles = cols * rows
    var processedTiles = 0

    val inputTile = Bitmap.createBitmap(MODEL_SIZE, MODEL_SIZE, Bitmap.Config.ARGB_8888)
    val tileCanvas = Canvas(inputTile)
    val paint = android.graphics.Paint()

    for (y in 0 until rows) {
        for (x in 0 until cols) {
            val targetX = x * VALID_SIZE
            val targetY = y * VALID_SIZE
            val sourceX = targetX - OFFSET
            val sourceY = targetY - OFFSET

            tileCanvas.drawColor(android.graphics.Color.BLACK)
            val srcLeft = maxOf(0, sourceX)
            val srcTop = maxOf(0, sourceY)
            val srcRight = minOf(width, sourceX + MODEL_SIZE)
            val srcBottom = minOf(height, sourceY + MODEL_SIZE)
            val dstLeft = srcLeft - sourceX
            val dstTop = srcTop - sourceY
            val dstRight = dstLeft + (srcRight - srcLeft)
            val dstBottom = dstTop + (srcBottom - srcTop)
            tileCanvas.drawBitmap(fullImage, Rect(srcLeft, srcTop, srcRight, srcBottom), Rect(dstLeft, dstTop, dstRight, dstBottom), paint)

            var tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(inputTile)
            tensorImage = imageProcessor.process(tensorImage)
            val inputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 3, 256, 256), DataType.FLOAT32)
            inputBuffer.loadBuffer(tensorImage.buffer)
            val outputs = model.process(inputBuffer)
            val outputFullTile = convertTensorToBitmap(outputs.outputFeature0AsTensorBuffer)

            val validTile = Bitmap.createBitmap(outputFullTile, OFFSET, OFFSET, VALID_SIZE, VALID_SIZE)
            val drawWidth = minOf(VALID_SIZE, width - targetX)
            val drawHeight = minOf(VALID_SIZE, height - targetY)
            canvas.drawBitmap(validTile, Rect(0, 0, drawWidth, drawHeight), Rect(targetX, targetY, targetX + drawWidth, targetY + drawHeight), paint)

            processedTiles++
            onProgress(processedTiles.toFloat() / totalTiles)
        }
    }
    model.close()
    return resultBitmap
}

fun convertTensorToBitmap(tensor: TensorBuffer): Bitmap {
    val floats = tensor.floatArray
    val size = 256
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            val baseIndex = (y * size + x) * 3
            val r = (floats[baseIndex] * 255).toInt().coerceIn(0, 255)
            val g = (floats[baseIndex + 1] * 255).toInt().coerceIn(0, 255)
            val b = (floats[baseIndex + 2] * 255).toInt().coerceIn(0, 255)
            pixels[y * size + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val originalWidth = bitmap.width
    val originalHeight = bitmap.height
    var resizedWidth = originalWidth
    var resizedHeight = originalHeight
    if (originalHeight > maxDimension || originalWidth > maxDimension) {
        if (originalWidth > originalHeight) {
            resizedWidth = maxDimension
            resizedHeight = (originalHeight * (maxDimension.toFloat() / originalWidth)).toInt()
        } else {
            resizedHeight = maxDimension
            resizedWidth = (originalWidth * (maxDimension.toFloat() / originalHeight)).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, true)
    }
    return bitmap
}

fun saveToGallery(context: Context, bitmap: Bitmap) {
    val filename = "Denoised_HD_${System.currentTimeMillis()}.jpg"
    var fos: OutputStream? = null
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
    }
    try {
        val resolver = context.contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        fos = imageUri?.let { resolver.openOutputStream(it) }
        fos?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
        Toast.makeText(context, "Saved to Gallery!", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to save", Toast.LENGTH_SHORT).show()
    }
}