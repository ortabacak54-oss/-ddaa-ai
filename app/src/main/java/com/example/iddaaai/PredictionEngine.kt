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

    private fun formStrength(matches: List<TeamFormMatch>): Double? {
        val recent = matches
            .sortedBy { it.date }
            .takeLast(5)

        if (recent.isEmpty()) return null

        val points = recent.fold(0.0) { total, match ->
            total + when (match.result) {
                "G" -> 3.0
                "B" -> 1.0
                else -> 0.0
            }
        }

        val pointsRate = points / (recent.size * 3.0)
        val goalDifference = recent.sumOf {
            it.goalsFor - it.goalsAgainst
        }.toDouble() / recent.size

        return (pointsRate * 0.7 +
            ((goalDifference / 2.0).coerceIn(-1.0, 1.0) * 0.3))
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

        val homeStrength = formStrength(homeForm)
        val awayStrength = formStrength(awayForm)

        val adjustedResult = if (
            homeStrength != null && awayStrength != null &&
            result.all { it != null }
        ) {
            val difference = homeStrength - awayStrength

            val homeFactor = kotlin.math.exp(0.35 * difference + 0.10)
            val drawFactor = kotlin.math.exp(-0.10 * kotlin.math.abs(difference))
            val awayFactor = kotlin.math.exp(-0.35 * difference)

            normalize(
                result[0]!! * homeFactor,
                result[1]!! * drawFactor,
                result[2]!! * awayFactor
            )
        } else {
            result
        }

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
