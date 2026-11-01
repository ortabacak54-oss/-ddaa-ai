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
    val score: String,
    val homeId: String = "",
    val awayId: String = ""
)

data class TeamFormMatch(
    val id: String,
    val date: String,
    val league: String,
    val opponent: String,
    val venue: String,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val result: String
)

data class MatchOdds(
    val home: String = "-",
    val draw: String = "-",
    val away: String = "-",
    val over25: String = "-",
    val under25: String = "-",
    val bttsYes: String = "-",
    val bttsNo: String = "-",
    val markets: List<OddsMarket> = emptyList()
)

data class OddsMarket(
    val name: String,
    val options: List<OddsOption>
)

data class OddsOption(
    val selection: String,
    val odd: String
)

object LiveApi {
    private const val API_URL =
        "https://script.google.com/macros/s/AKfycbzNP8OrklfyhUAfat0hVzalNhNtKmM-rVu-r8Wd0qVm3KOMO1DJvEbR2lDQdXRV-OSs/exec"

    private fun request(url: String): JSONObject {
        val separator = if (url.contains("?")) "&" else "?"
        val freshUrl = "$url${separator}_ts=${System.currentTimeMillis()}"
        val connection = URL(freshUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.instanceFollowRedirects = true
        connection.useCaches = false
        connection.setRequestProperty("Cache-Control", "no-cache, no-store")
        connection.setRequestProperty("Pragma", "no-cache")

        try {
            val response = connection.inputStream.bufferedReader().use {
                it.readText()
            }
            return JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    fun getMatches(): List<LiveMatch> {
        val json = request(API_URL)

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
                score = if (
                    homeScore == null || homeScore == "null" ||
                    awayScore == null || awayScore == "null"
                ) "- : -" else "$homeScore : $awayScore",
                    
                homeId = item.optString("evId"),
                awayId = item.optString("deplasmanId")
            )
        }
    }


    fun getTeamForm(teamId: String): List<TeamFormMatch> {
        val json = request("$API_URL?action=form&team=$teamId")

        if (json.optString("durum") != "aktif") {
            throw Exception(json.optString("mesaj", "Form verisi alınamadı"))
        }

        val array = json.optJSONArray("maclar") ?: return emptyList()

        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)

            TeamFormMatch(
                id = item.optString("id"),
                date = item.optString("tarih"),
                league = item.optString("lig"),
                opponent = item.optString("rakip"),
                venue = item.optString("saha"),
                goalsFor = item.optInt("atilanGol"),
                goalsAgainst = item.optInt("yenilenGol"),
                result = item.optString("sonuc")
            )
        }
    }

    fun getOdds(fixtureId: String): MatchOdds {
        val json = request("$API_URL?action=odds&fixture=$fixtureId")

        if (json.optString("durum") != "aktif") {
            throw Exception(json.optString("mesaj", "Oran verisi alınamadı"))
        }

        val marketsJson = json.optJSONArray("pazarlar")
        val markets = mutableListOf<OddsMarket>()
        val result = mutableMapOf<String, String>()

        if (marketsJson != null) {
            for (i in 0 until marketsJson.length()) {
                val market = marketsJson.getJSONObject(i)
                val name = market.optString("pazar")
                val optionsJson = market.optJSONArray("secenekler")
                val options = mutableListOf<OddsOption>()

                if (optionsJson != null) {
                    for (j in 0 until optionsJson.length()) {
                        val option = optionsJson.getJSONObject(j)
                        val selection = option.optString("secim")
                        val odd = option.optString("oran")
                        options.add(OddsOption(selection, odd))

                        if (name == "Maç Sonucu") {
                            when (selection) {
                                "1" -> result.putIfAbsent("home", odd)
                                "X" -> result.putIfAbsent("draw", odd)
                                "2" -> result.putIfAbsent("away", odd)
                            }
                        }
                        if (name.contains("2,5 Alt/Üst", true) ||
                            name.contains("2.5 Alt/Üst", true)) {
                            when (selection) {
                                "Alt" -> result.putIfAbsent("under25", odd)
                                "Üst" -> result.putIfAbsent("over25", odd)
                            }
                        }
                        if (name.contains("Karşılıklı Gol", true)) {
                            when (selection) {
                                "Var" -> result.putIfAbsent("bttsYes", odd)
                                "Yok" -> result.putIfAbsent("bttsNo", odd)
                            }
                        }
                    }
                }
                if (options.isNotEmpty()) {
                    markets.add(OddsMarket(name, options))
                }
            }
        }

        return MatchOdds(
            home = result["home"] ?: "-",
            draw = result["draw"] ?: "-",
            away = result["away"] ?: "-",
            over25 = result["over25"] ?: "-",
            under25 = result["under25"] ?: "-",
            bttsYes = result["bttsYes"] ?: "-",
            bttsNo = result["bttsNo"] ?: "-",
            markets = markets
        )
    }
}
