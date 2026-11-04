package com.example.iptvtv

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object Net {
    /** Mở kết nối và tự theo redirect (kể cả http -> https). */
    fun open(url: String, timeoutMs: Int = 8000): HttpURLConnection {
        var u = url
        repeat(5) {
            val c = URL(u).openConnection() as HttpURLConnection
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36")
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc == null) throw IOException("redirect without Location")
                u = URL(URL(u), loc).toString()
            } else return c
        }
        throw IOException("too many redirects")
    }
}
