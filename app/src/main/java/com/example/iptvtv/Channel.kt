package com.example.iptvtv

data class Channel(
    val name: String,
    val url: String,
    val group: String,
    val logo: String = "",
    val tvgId: String = ""
)

data class PlaylistData(val channels: List<Channel>, val epgUrl: String?)
