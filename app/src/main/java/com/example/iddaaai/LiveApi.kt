package com.example.iddaaai

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class LiveMatch(
    val id: String,
    val league: String,
    val home: String,
    val away: String,
    val kickoff: String,
    val status: String,
    val minute: String,
    val score: String
)

object LiveApi {
    private const val API_URL =
        "https://script.google.com/macros/s/AKfycbzDzTZeS67QAxqd5jkDohrsmfvWjLJWoT3z5KtXjyPU2RXznvw5eYy7Tts41_X2Rxd2/exec"

    fun getMatches(): List<LiveMatch> {
        val connection = URL(API_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 20000

        try {
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(response)

            if (json.optString("durum") != "aktif") {
                throw Exception(json.optString("mesaj", "API veri hatası"))
            }

            val array = json.optJSONArray("maclar") ?: return emptyList()

            return (0 until array.length()).map { i ->
                val item = array.getJSONObject(i)
                val homeScore = item.opt("evGol")
                val awayScore = item.opt("deplasmanGol")

                LiveMatch(
                    id = item.optString("id"),
                    league = item.optString("lig"),
                    home = item.optString("ev"),
                    away = item.optString("deplasman"),
                    kickoff = item.optString("tarih"),
                    status = item.optString("durum"),
                    minute = item.opt("dakika")?.toString() ?: "",
                    score = if (homeScore == null || homeScore == "null" ||
                        awayScore == null || awayScore == "null") {
                        "- : -"
                    } else {
                        "$homeScore : $awayScore"
                    }
                )
            }
        } finally {
            connection.disconnect()
        }
    }
}
