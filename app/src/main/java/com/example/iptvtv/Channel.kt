package com.example.iptvtv

data class Channel(
    val name: String,
    val url: String,
    val group: String,
    val logo: String,
    val id: String,
    val headers: Map<String, String>,
    val manifestType: String,
    val isDrm: Boolean,
    val licKey: String = ""
)

data class PlaylistData(val channels: List<Channel>, val epgUrl: String?)
