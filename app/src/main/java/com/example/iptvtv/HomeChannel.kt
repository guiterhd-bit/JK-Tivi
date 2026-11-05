package com.iptv.jktivi

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat

/** Hàng "JK Tivi" trên màn hình chính Android TV / Projectivy Launcher: 12 kênh có logo. */
object HomeChannel {
    const val EXTRA_KEY = "channel_key"

    private class Entry(val res: String, val title: String, val match: (String) -> Boolean)

    private fun vtv(n: Int) = Regex("(?<![a-z0-9])vtv\\s*0*$n(?![0-9])")

    private val entries = listOf(
        Entry("vtv1", "VTV1") { vtv(1).containsMatchIn(it) },
        Entry("vtv2", "VTV2") { vtv(2).containsMatchIn(it) },
        Entry("vtv3", "VTV3") { vtv(3).containsMatchIn(it) },
        Entry("vtv4", "VTV4") { vtv(4).containsMatchIn(it) },
        Entry("vtv5", "VTV5") { vtv(5).containsMatchIn(it) },
        Entry("vtv6", "VTV6") { vtv(6).containsMatchIn(it) },
        Entry("vtv7", "VTV7") { vtv(7).containsMatchIn(it) },
        Entry("vtv8", "VTV8") { vtv(8).containsMatchIn(it) },
        Entry("vtv9", "VTV9") { vtv(9).containsMatchIn(it) },
        Entry("vtv10", "VTV10") { vtv(10).containsMatchIn(it) },
        Entry("qpvn", "QPVN") { it.contains("qpvn") || it.contains("quốc phòng") },
        Entry("antv", "ANTV") { it.contains("antv") || it.contains("an ninh") }
    )

    /** Tìm vị trí kênh trong playlist ứng với khóa (vd "vtv1"); -1 nếu không có. */
    fun indexOf(list: List<Channel>, key: String): Int {
        val e = entries.firstOrNull { it.res == key } ?: return -1
        return list.indexOfFirst { e.match(it.name.lowercase()) }
    }

    @Synchronized
    fun publish(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val app = ctx.applicationContext
            val helper = PreviewChannelHelper(app)
            val prefs = app.getSharedPreferences("home_channel", Context.MODE_PRIVATE)

            var id = prefs.getLong("id", -1L)
            if (id != -1L && runCatching { helper.getPreviewChannel(id) }.getOrNull() == null) id = -1L

            if (id == -1L) {
                val logoRes = app.resources.getIdentifier("vtv1", "drawable", app.packageName)
                val ch = PreviewChannel.Builder()
                    .setDisplayName("JK Tivi")
                    .setAppLinkIntent(
                        Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    .setLogo(BitmapFactory.decodeResource(app.resources, logoRes))
                    .build()
                id = helper.publishChannel(ch)
                prefs.edit().putLong("id", id).apply()
                runCatching { TvContractCompat.requestChannelBrowsable(app, id) }
            } else {
                // Xóa chương trình cũ rồi đăng lại để luôn đủ 12 kênh
                app.contentResolver.delete(
                    TvContractCompat.buildPreviewProgramsUriForChannel(id), null, null
                )
            }

            for ((i, e) in entries.withIndex()) {
                val poster = Uri.parse("android.resource://${app.packageName}/drawable/${e.res}")
                val intent = Intent(app, MainActivity::class.java)
                    .putExtra(EXTRA_KEY, e.res)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val program = PreviewProgram.Builder()
                    .setChannelId(id)
                    .setType(TvContractCompat.PreviewPrograms.TYPE_CLIP)
                    .setTitle(e.title)
                    .setPosterArtUri(poster)
                    .setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
                    .setWeight(entries.size - i)
                    .setIntent(intent)
                    .build()
                helper.publishPreviewProgram(program)
            }
        }.onFailure { Log.e("JKTivi", "Không đăng được kênh trang chủ", it) }
    }
}
