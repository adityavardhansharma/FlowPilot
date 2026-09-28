package dev.flowpilot.app.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.max

/** Something riding along with the next message, shown as a chip above the text. */
sealed interface Attachment {
    val id: String
    val name: String

    /** A photo from the phone, sent inline as a `data:` URI. */
    class Image(override val id: String, override val name: String, val mime: String, val dataUri: String, val preview: ImageBitmap) : Attachment

    /** A file on the computer, by its path relative to the chat's folder, sent as `file://` context. */
    data class File(val path: String) : Attachment {
        override val id get() = "f:$path"
        override val name get() = path.substringAfterLast('/')
    }
}

/**
 * Reads a picked photo, shrinks it so its long edge is at most [MAX_EDGE] px (what vision models downscale to
 * anyway, and it keeps the request small over Wi-Fi), and encodes it for the prompt. PNG stays PNG unless that
 * comes out large; everything else becomes JPEG. EXIF rotation is applied on Android 9 and later.
 */
suspend fun loadImageAttachment(context: Context, uri: Uri): Attachment.Image = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: "image"
    val sourceMime = resolver.getType(uri).orEmpty()
    val bitmap = decodeScaled(context, uri) ?: error("Couldn't read that image.")
    val png = sourceMime == "image/png" || sourceMime == "image/webp"
    var bytes = encode(bitmap, if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG)
    var mime = if (png) "image/png" else "image/jpeg"
    if (png && bytes.size > MAX_PNG_BYTES) {
        bytes = encode(bitmap, Bitmap.CompressFormat.JPEG)
        mime = "image/jpeg"
    }
    val scale = PREVIEW_EDGE.toFloat() / max(bitmap.width, bitmap.height)
    val preview = if (scale >= 1f) bitmap else Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
    Attachment.Image(
        id = UUID.randomUUID().toString(),
        name = name,
        mime = mime,
        dataUri = "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP),
        preview = preview.asImageBitmap(),
    )
}

private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
    val resolver = context.contentResolver
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_EDGE) {
                val s = MAX_EDGE.toFloat() / longest
                decoder.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
    val longest = max(decoded.width, decoded.height)
    if (longest <= MAX_EDGE) return decoded
    val s = MAX_EDGE.toFloat() / longest
    return Bitmap.createScaledBitmap(decoded, (decoded.width * s).toInt(), (decoded.height * s).toInt(), true)
}

private fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat): ByteArray =
    ByteArrayOutputStream().also { bitmap.compress(format, JPEG_QUALITY, it) }.toByteArray()

private const val MAX_EDGE = 1568
private const val PREVIEW_EDGE = 192
private const val JPEG_QUALITY = 85
private const val MAX_PNG_BYTES = 1_500_000

/** Most images a single message may carry. */
const val MAX_IMAGES = 6
