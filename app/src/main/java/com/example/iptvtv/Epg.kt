package com.example.iptvtv

import android.content.Context
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.HashMap
import java.util.Locale
import java.util.zip.GZIPInputStream

/** EPG dạng XMLTV. Chỉ giữ các chương trình trong vài giờ tới để tiết kiệm RAM. */
object Epg {
    class Prog(val start: Long, val end: Long, val title: String)

    @Volatile private var progs: Map<String, List<Prog>> = emptyMap()
    @Volatile private var nameToId: Map<String, String> = emptyMap()

    private fun key(s: String): String {
        val d = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "").replace('đ', 'd')
        return buildString { d.forEach { if (it.isLetterOrDigit()) append(it) } }.removeSuffix("hd")
    }

    private fun idOf(ch: Channel): String? {
        if (ch.tvgId.isNotEmpty() && progs.containsKey(ch.tvgId)) return ch.tvgId
        return nameToId[key(ch.name)]
    }

    fun now(ch: Channel, t: Long): Prog? =
        idOf(ch)?.let { id -> progs[id]?.firstOrNull { t >= it.start && t < it.end } }

    fun next(ch: Channel, t: Long): Prog? =
        idOf(ch)?.let { id -> progs[id]?.firstOrNull { it.start > t } }

    /** Gọi trong luồng nền. Đọc cache trước, sau đó tải mới nếu cache cũ hơn 6 giờ. */
    @Synchronized
    fun load(ctx: Context, url: String?, onUpdate: () -> Unit) {
        if (url.isNullOrBlank()) return
        val f = File(ctx.filesDir, "epg.xml")
        var fresh = false
        if (f.exists()) {
            if (parseFile(f)) onUpdate()
            fresh = System.currentTimeMillis() - f.lastModified() < 6 * 3600_000L
        }
        if (!fresh) runCatching {
            val conn = Net.open(url, 20000)
            try {
                val tmp = File(f.path + ".tmp")
                conn.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                tmp.renameTo(f)
            } finally { conn.disconnect() }
            if (parseFile(f)) onUpdate()
        }
    }

    private fun parseFile(f: File): Boolean = runCatching {
        val raw = BufferedInputStream(FileInputStream(f), 65536)
        raw.mark(2)
        val b1 = raw.read(); val b2 = raw.read()
        raw.reset()
        val ins = if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(raw) else raw
        ins.use { parse(it) }
        true
    }.getOrDefault(false)

    private fun parseTime(fmt: SimpleDateFormat, s: String?): Long? {
        if (s == null) return null
        val str = if (s.length <= 14) "$s +0700" else s
        return runCatching { fmt.parse(str)?.time }.getOrNull()
    }

    private fun parse(ins: java.io.InputStream) {
        val p = Xml.newPullParser()
        p.setInput(ins, null)
        val now = System.currentTimeMillis()
        val lo = now - 2 * 3600_000L
        val hi = now + 12 * 3600_000L
        val map = HashMap<String, ArrayList<Prog>>()
        val names = HashMap<String, String>()
        val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        var chId: String? = null
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "channel" -> chId = p.getAttributeValue(null, "id")
                    "display-name" -> {
                        val t = p.nextText()
                        val id = chId
                        if (id != null) { val k = key(t); if (k.isNotEmpty()) names.putIfAbsent(k, id) }
                    }
                    "programme" -> {
                        val id = p.getAttributeValue(null, "channel")
                        val s = parseTime(fmt, p.getAttributeValue(null, "start"))
                        val e = parseTime(fmt, p.getAttributeValue(null, "stop"))
                        var title = ""
                        while (true) {
                            ev = p.next()
                            if (ev == XmlPullParser.END_DOCUMENT) break
                            if (ev == XmlPullParser.END_TAG && p.name == "programme") break
                            if (ev == XmlPullParser.START_TAG && p.name == "title" && title.isEmpty()) title = p.nextText()
                        }
                        if (id != null && s != null && e != null && e > lo && s < hi && title.isNotBlank())
                            map.getOrPut(id) { ArrayList() }.add(Prog(s, e, title.trim()))
                    }
                }
            } else if (ev == XmlPullParser.END_TAG && p.name == "channel") {
                chId = null
            }
            ev = p.next()
        }
        map.values.forEach { l -> l.sortBy { it.start } }
        progs = map
        nameToId = names
    }
}
