package com.example.iptvtv

import android.content.Context
import java.io.File

object Playlist {
    /** Nguồn phát gắn sẵn. Sửa link ở đây khi cần đổi nguồn. */
    const val SOURCE_URL = "https://raw.githubusercontent.com/guiterhd-bit/direct-link/refs/heads/main/playlist.M3U"

    private val attr = Regex("""([\w-]+)="([^"]*)"""")
    private fun cacheFile(c: Context) = File(c.filesDir, "playlist.m3u")
    private val empty = PlaylistData(emptyList(), null)

    /** Đọc cache hoặc file gắn kèm trong assets: nhanh, không cần mạng. */
    fun cached(c: Context): PlaylistData {
        val f = cacheFile(c)
        if (f.exists()) return parse(f.readText())
        return runCatching { parse(c.assets.open("playlist.m3u").bufferedReader().use { it.readText() }) }
            .getOrDefault(empty)
    }

    /** Tải bản mới (tự theo redirect của tinyurl) và lưu cache. */
    fun download(c: Context): PlaylistData {
        val conn = Net.open(SOURCE_URL)
        try {
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val data = parse(text)
            if (data.channels.isNotEmpty()) cacheFile(c).writeText(text)
            return data
        } finally {
            conn.disconnect()
        }
    }

    fun parse(text: String): PlaylistData {
        val out = ArrayList<Channel>()
        var epg: String? = null
        var name = ""; var group = ""; var logo = ""; var id = ""
        var hdr = HashMap<String, String>()
        var mtype = ""; var drm = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXTM3U") -> {
                    val a = attr.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                    epg = (a["url-tvg"] ?: a["x-tvg-url"])?.split(',')?.firstOrNull()?.trim()
                }
                line.startsWith("#EXTINF") -> {
                    val a = attr.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                    group = a["group-title"].orEmpty().trim()
                    logo = a["tvg-logo"].orEmpty().trim()
                    id = a["tvg-id"].orEmpty().trim()
                    name = line.substringAfterLast(',').trim()
                }
                line.startsWith("#KODIPROP:", ignoreCase = true) -> {
                    // Chỉ đọc gợi ý định dạng; kênh khai báo DRM được đánh dấu để app báo rõ là không hỗ trợ
                    val kv = line.substringAfter(':').split('=', limit = 2)
                    if (kv.size == 2) when (kv[0].trim().lowercase()) {
                        "inputstream.adaptive.manifest_type" -> mtype = kv[1].trim().lowercase()
                        "inputstream.adaptive.license_type" -> drm = true
                    }
                }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val kv = line.substringAfter(':').split('=', limit = 2)
                    if (kv.size == 2) when (kv[0].trim().lowercase()) {
                        "http-user-agent" -> hdr["User-Agent"] = kv[1].trim()
                        "http-referrer", "http-referer" -> hdr["Referer"] = kv[1].trim()
                        "http-origin" -> hdr["Origin"] = kv[1].trim()
                    }
                }
                line.isNotEmpty() && !line.startsWith("#") -> {
                    // Hỗ trợ dạng link|User-Agent=...&Referer=...
                    val parts = line.split('|', limit = 2)
                    val url = parts[0].trim()
                    if (parts.size == 2) parts[1].split('&').forEach { pair ->
                        val kv = pair.split('=', limit = 2)
                        if (kv.size == 2 && kv[0].isNotBlank()) hdr[kv[0].trim()] = kv[1].trim()
                    }
                    if (name.isNotEmpty()) out.add(Channel(name, url, group.ifBlank { "Khác" }, logo, id, hdr, mtype, drm))
                    name = ""; group = ""; logo = ""; id = ""; hdr = HashMap(); mtype = ""; drm = false
                }
            }
        }
        return PlaylistData(out, epg)
    }
}
