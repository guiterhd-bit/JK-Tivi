package com.example.iptvtv

data class Channel(
    val name: String,
    val url: String,
    val group: String,
    val logo: String = "",
    val tvgId: String = "",
    val headers: Map<String, String> = emptyMap(),
    val manifestType: String = "",
    val drm: Boolean = false,
    val licKey: String = "",
    val licenseType: String = ""
)

data class PlaylistData(val channels: List<Channel>, val epgUrl: String?)
