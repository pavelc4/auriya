package dev.auriya.app.data

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide cache for launcher app icons. Resolving a single icon
 * goes through PackageManager IPC + drawable rasterisation, which is
 * far too expensive to repeat on every recomposition. Cache once,
 * reuse forever (icons rarely change at runtime).
 *
 * Bitmap size is capped at 96px on the longest edge — Compose scales
 * down anyway, but a few apps ship 512px adaptive icons and rendering
 * them full-resolution into a list pushes the GPU heap noticeably.
 */
object AppIconCache {
    private const val MAX_PX = 96
    private val cache = ConcurrentHashMap<String, ImageBitmap>()
    private val misses = ConcurrentHashMap.newKeySet<String>()

    fun get(packageName: String): ImageBitmap? = cache[packageName]

    /** True when we've already tried and failed to load this package. */
    fun isMiss(packageName: String): Boolean = misses.contains(packageName)

    /** Load synchronously off-thread; safe to call from Dispatchers.IO. */
    fun load(pm: PackageManager, packageName: String): ImageBitmap? {
        cache[packageName]?.let { return it }
        if (misses.contains(packageName)) return null
        return try {
            val drawable = pm.getApplicationIcon(packageName)
            val srcW = drawable.intrinsicWidth.coerceAtLeast(1)
            val srcH = drawable.intrinsicHeight.coerceAtLeast(1)
            val scale = MAX_PX.toFloat() / maxOf(srcW, srcH).toFloat()
            val w = if (scale < 1f) (srcW * scale).toInt().coerceAtLeast(1) else srcW
            val h = if (scale < 1f) (srcH * scale).toInt().coerceAtLeast(1) else srcH
            val bmp = if (drawable is BitmapDrawable && drawable.bitmap != null && srcW <= MAX_PX && srcH <= MAX_PX) {
                drawable.bitmap
            } else {
                val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(out)
                drawable.setBounds(0, 0, w, h)
                drawable.draw(canvas)
                out
            }
            val ib = bmp.asImageBitmap()
            cache[packageName] = ib
            ib
        } catch (_: Throwable) {
            misses.add(packageName)
            null
        }
    }
}
