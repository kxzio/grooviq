package com.example.groviq.backEnd.lyricsProducer

import com.example.groviq.backEnd.dataStructures.songData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

data class LrcLine(val timeMs: Long, val text: String)

fun parseLrc(lrc: String): List<LrcLine> =
    lrc.lines().mapNotNull { line ->
        val match = Regex("""\[(\d+):(\d+).(\d+)](.*)""").find(line)
        match?.let {
            val min = it.groupValues[1].toLongOrNull() ?: return@let null
            val sec = it.groupValues[2].toLongOrNull() ?: return@let null
            val ms = it.groupValues[3].padEnd(3, '0').toLongOrNull() ?: 0L
            val text = it.groupValues[4].trim()
            LrcLine(min * 60_000 + sec * 1000 + ms, text)
        }
    }.sortedBy { it.timeMs }

interface LrcLibApi {
    @GET("api/get")
    suspend fun getLyrics(
        @Query("artist_name") artist: String,
        @Query("track_name") track: String
    ): LrcResponse?


    @GET("api/get")
    suspend fun getLyricsByYoutubeId(
        @Query("youtube_id") youtubeId: String
    ): LrcResponse?
}

fun extractYoutubeId(link: String): String? {
    val regex = Regex("""(?:v=|youtu\.be/)([a-zA-Z0-9_-]{11})""")
    return regex.find(link)?.groupValues?.get(1)
}

data class LrcResponse(
    val id: Int?,
    val artistName: String?,
    val trackName: String?,
    val plainLyrics: String?,
    val syncedLyrics: String?
)

object LrcLibService {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://lrclib.net/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: LrcLibApi = retrofit.create(LrcLibApi::class.java)
}


suspend fun fetchLyricsForSong( song: songData): String? {

    val artist = song.artists.joinToString(",") { it.title }
    val title = song.title
    try {
        val response = LrcLibService.api.getLyrics(artist, title)
        return response?.syncedLyrics ?: response?.plainLyrics
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return null
}
