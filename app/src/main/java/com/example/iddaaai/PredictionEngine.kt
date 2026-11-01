package com.example.iddaaai

data class MarketPrediction(
    val home: Double?,
    val draw: Double?,
    val away: Double?,
    val under25: Double?,
    val over25: Double?,
    val bttsYes: Double?,
    val bttsNo: Double?,
    val bestPick: String,
    val confidence: Double?
)

object PredictionEngine {

    private fun probability(odd: String): Double? {
        val value = odd.toDoubleOrNull() ?: return null
        if (value <= 1.0) return null
        return 1.0 / value
    }

    private fun normalize(vararg values: Double?): List<Double?> {
        val total = values.filterNotNull().sum()
        if (total <= 0.0) return values.map { null }
        return values.map { it?.let { value -> value / total * 100.0 } }
    }


    fun calculate(
        odds: MatchOdds,
        homeForm: List<TeamFormMatch> = emptyList(),
        awayForm: List<TeamFormMatch> = emptyList()
    ): MarketPrediction {

        val result = normalize(
            probability(odds.home),
            probability(odds.draw),
            probability(odds.away)
        )

        val adjustedResult = result

        val goals = normalize(
            probability(odds.under25),
            probability(odds.over25)
        )

        val btts = normalize(
            probability(odds.bttsYes),
            probability(odds.bttsNo)
        )

        val candidates = listOf(
            "MS 1" to adjustedResult[0],
            "Beraberlik" to adjustedResult[1],
            "MS 2" to adjustedResult[2],
            "2.5 Alt" to goals[0],
            "2.5 Üst" to goals[1],
            "KG Var" to btts[0],
            "KG Yok" to btts[1]
        ).filter { it.second != null }

        val best = candidates.maxByOrNull { it.second!! }

        return MarketPrediction(
            home = adjustedResult[0],
            draw = adjustedResult[1],
            away = adjustedResult[2],
            under25 = goals[0],
            over25 = goals[1],
            bttsYes = btts[0],
            bttsNo = btts[1],
            bestPick = best?.first ?: "Yeterli oran yok",
            confidence = best?.second
        )
    }
}
