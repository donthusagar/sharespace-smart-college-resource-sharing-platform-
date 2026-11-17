package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.annotation.DrawableRes
import com.example.R
import java.io.ByteArrayOutputStream
import kotlin.math.max

object ImageUtils {

    @DrawableRes
    fun resolveDrawableRes(photoRef: String): Int? {
        return when (photoRef) {
            "res://img_textbooks_demo" -> R.drawable.img_textbooks_demo
            "res://img_calculator_demo" -> R.drawable.img_calculator_demo
            "res://img_lab_kit_demo" -> R.drawable.img_lab_kit_demo
            "res://img_campus_hero" -> R.drawable.img_campus_hero
            else -> null
        }
    }

    fun decodeBase64DataUri(dataUri: String): ByteArray? {
        if (!dataUri.startsWith("data:image")) return null
        val commaIdx = dataUri.indexOf(',')
        if (commaIdx == -1 || commaIdx >= dataUri.length - 1) return null
        return try {
            Base64.decode(dataUri.substring(commaIdx + 1), Base64.DEFAULT)
        } catch (_: Exception) {
            null
        }
    }

    fun encodeUriToCompactDataUri(context: Context, uri: Uri, maxDim: Int = 512): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val original = inputStream.use { BitmapFactory.decodeStream(it) } ?: return null
            val scale = max(original.width, original.height).toFloat() / maxDim.toFloat()
            val resized = if (scale > 1f) {
                val w = (original.width / scale).toInt().coerceAtLeast(1)
                val h = (original.height / scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(original, w, h, true)
            } else {
                original
            }
            val out = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, 72, out)
            val bytes = out.toByteArray()
            if (resized != original) resized.recycle()
            original.recycle()
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            "data:image/jpeg;base64,$b64"
        } catch (_: Exception) {
            null
        }
    }
}
