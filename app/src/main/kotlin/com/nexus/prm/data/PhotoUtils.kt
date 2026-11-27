package com.nexus.prm.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

object PhotoUtils {
  private const val MAX_DIMENSION_PX = 1024
  private const val JPEG_QUALITY = 88

  fun copyAndCompressFromUri(context: Context, uri: Uri): ByteArray? {
    val bitmap = decodeScaledBitmap(context, uri) ?: return null
    return try {
      ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        out.toByteArray()
      }
    } finally {
      bitmap.recycle()
    }
  }

  private fun decodeScaledBitmap(context: Context, uri: Uri): Bitmap? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      val source = ImageDecoder.createSource(context.contentResolver, uri)
      ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val maxSide = max(info.size.width, info.size.height)
        if (maxSide > MAX_DIMENSION_PX) {
          val scale = MAX_DIMENSION_PX.toFloat() / maxSide.toFloat()
          decoder.setTargetSize(
            (info.size.width * scale).roundToInt().coerceAtLeast(1),
            (info.size.height * scale).roundToInt().coerceAtLeast(1),
          )
        }
        decoder.isMutableRequired = false
      }
    } else {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
      }
      val maxSide = max(bounds.outWidth, bounds.outHeight)
      var sampleSize = 1
      while (maxSide / sampleSize > MAX_DIMENSION_PX * 2) {
        sampleSize *= 2
      }
      val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
      context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, opts)
      }
    }
  }
}
