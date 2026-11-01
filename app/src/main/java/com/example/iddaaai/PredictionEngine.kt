package com.example.iddaaai

import kotlin.math.exp
import kotlin.math.pow

data class MarketPrediction(
    val home: Double?,
    val draw: Double?,
    val away: Double?,
    val under25: Double?,
    val over25: Double?,
    val bttsYes: Double?,
    val bttsNo: Double?,
    val bestPick: String,
    val confidence: Double?,
    val predictedScore: String,
    val homeExpectedGoals: Double,
    val awayExpectedGoals: Double,
    val scoreProbability: Double
)

object PredictionEngine {

    private fun probability(odd: String): Double? {
        val value = odd.replace(",", ".").toDoubleOrNull() ?: return null
        if (value <= 1.0) return null
        return 1.0 / value
    }

    private fun normalize(vararg values: Double?): List<Double?> {
        val total = values.filterNotNull().sum()
        if (total <= 0.0) return values.map { null }
        return values.map { it?.let { value -> value / total * 100.0 } }
    }

    private fun average(values: List<Int>, fallback: Double): Double =
        if (values.isEmpty()) fallback else values.average()

    private fun blendedAverage(
        venueValues: List<Int>,
        allValues: List<Int>,
        fallback: Double
    ): Double {
        val general = average(allValues, fallback)
        val venue = average(venueValues, general)
        val venueWeight = (venueValues.size / 10.0).coerceIn(0.0, 0.65)
        return general * (1.0 - venueWeight) + venue * venueWeight
    }

    private fun poisson(lambda: Double, goals: Int): Double {
        var factorial = 1.0
        for (i in 1..goals) factorial *= i
        return exp(-lambda) * lambda.pow(goals) / factorial
    }

    fun calculate(
        odds: MatchOdds,
        homeForm: List<TeamFormMatch> = emptyList(),
        awayForm: List<TeamFormMatch> = emptyList()
    ): MarketPrediction {

        val homeRecent = homeForm.take(10)
        val awayRecent = awayForm.take(10)

        val homeVenue = homeForm.filter { it.venue.contains("İç saha", true) }.take(10)
        val awayVenue = awayForm.filter { it.venue.contains("Deplasman", true) }.take(10)

        val homeAttack = blendedAverage(homeVenue.map { it.goalsFor }, homeRecent.map { it.goalsFor }, 1.35)
        val homeDefence = blendedAverage(homeVenue.map { it.goalsAgainst }, homeRecent.map { it.goalsAgainst }, 1.35)
        val awayAttack = blendedAverage(awayVenue.map { it.goalsFor }, awayRecent.map { it.goalsFor }, 1.15)
        val awayDefence = blendedAverage(awayVenue.map { it.goalsAgainst }, awayRecent.map { it.goalsAgainst }, 1.35)

        val homeXg = ((homeAttack + awayDefence) / 2.0).coerceIn(0.20, 4.0)
        val awayXg = ((awayAttack + homeDefence) / 2.0).coerceIn(0.20, 4.0)

        val scoreMatrix = (0..6).flatMap { h ->
            (0..6).map { a ->
                Triple(h, a, poisson(homeXg, h) * poisson(awayXg, a))
            }
        }

        val bestScore = scoreMatrix.maxByOrNull { it.third }!!
        val totalScoreProbability = scoreMatrix.sumOf { it.third }.coerceAtLeast(0.000001)

        val modelHome = scoreMatrix.filter { it.first > it.second }.sumOf { it.third } / totalScoreProbability
        val modelDraw = scoreMatrix.filter { it.first == it.second }.sumOf { it.third } / totalScoreProbability
        val modelAway = scoreMatrix.filter { it.first < it.second }.sumOf { it.third } / totalScoreProbability

        val marketResult = normalize(
            probability(odds.home),
            probability(odds.draw),
            probability(odds.away)
        )

        val adjustedResult = if (marketResult.all { it != null }) {
            normalize(
                marketResult[0]!! * 0.65 + modelHome * 100.0 * 0.35,
                marketResult[1]!! * 0.65 + modelDraw * 100.0 * 0.35,
                marketResult[2]!! * 0.65 + modelAway * 100.0 * 0.35
            )
        } else {
            normalize(modelHome * 100.0, modelDraw * 100.0, modelAway * 100.0)
        }

        val modelUnder = scoreMatrix.filter { it.first + it.second <= 2 }.sumOf { it.third } / totalScoreProbability * 100.0
        val modelOver = 100.0 - modelUnder
        val modelBtts = scoreMatrix.filter { it.first > 0 && it.second > 0 }.sumOf { it.third } / totalScoreProbability * 100.0

        val goals = normalize(probability(odds.under25), probability(odds.over25))
        val btts = normalize(probability(odds.bttsYes), probability(odds.bttsNo))

        val finalGoals = if (goals.all { it != null }) goals else listOf(modelUnder, modelOver)
        val finalBtts = if (btts.all { it != null }) btts else listOf(modelBtts, 100.0 - modelBtts)

        val candidates = listOf(
            "MS 1" to adjustedResult[0],
            "Beraberlik" to adjustedResult[1],
            "MS 2" to adjustedResult[2],
            "2.5 Alt" to finalGoals[0],
            "2.5 Üst" to finalGoals[1],
            "KG Var" to finalBtts[0],
            "KG Yok" to finalBtts[1]
        ).filter { it.second != null }

        val best = candidates.maxByOrNull { it.second!! }

        return MarketPrediction(
            home = adjustedResult[0],
            draw = adjustedResult[1],
            away = adjustedResult[2],
            under25 = finalGoals[0],
            over25 = finalGoals[1],
            bttsYes = finalBtts[0],
            bttsNo = finalBtts[1],
            bestPick = best?.first ?: "Yeterli veri yok",
            confidence = best?.second,
            predictedScore = "${bestScore.first}-${bestScore.second}",
            homeExpectedGoals = homeXg,
            awayExpectedGoals = awayXg,
            scoreProbability = bestScore.third * 100.0
        )
    }
}
