package com.example.iptvtv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Trình tải logo nhỏ gọn: cache RAM + cache đĩa, không dùng thư viện ngoài. */
object Logos {
    private val ram = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 16).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
    private val pool = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val failed = ConcurrentHashMap.newKeySet<String>()

    fun load(ctx: Context, url: String, view: ImageView) {
        view.tag = url
        if (url.isBlank() || url in failed) { view.setImageDrawable(null); return }
        ram.get(url)?.let { view.setImageBitmap(it); return }
        view.setImageDrawable(null)
        val app = ctx.applicationContext
        pool.execute {
            val bmp = runCatching { fetch(app, url) }.getOrNull()
            if (bmp == null) { failed.add(url); return@execute }
            ram.put(url, bmp)
            main.post { if (view.tag == url) view.setImageBitmap(bmp) }
        }
    }

    private fun fetch(ctx: Context, url: String): Bitmap? {
        val dir = File(ctx.cacheDir, "logos").apply { mkdirs() }
        val f = File(dir, url.hashCode().toString())
        if (!f.exists()) {
            val conn = Net.open(url, 6000)
            try {
                val tmp = File(dir, f.name + ".tmp")
                conn.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                tmp.renameTo(f)
            } finally { conn.disconnect() }
        }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (o.outWidth / (s * 2) >= 160) s *= 2
        val bmp = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
        if (bmp == null) f.delete() // ví dụ file SVG không giải mã được
        return bmp
    }
}
