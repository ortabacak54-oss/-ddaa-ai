package com.example.iddaaai

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

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
    val scoreProbability: Double,
    val h2hScores: List<String> = emptyList(),
    val mostCommonH2HScore: String = "-",
    val mostCommonH2HCount: Int = 0
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
        awayForm: List<TeamFormMatch> = emptyList(),
    homeName: String = "",
    awayName: String = ""
    ,
        homeStats: TeamStatsAverage? = null,
        awayStats: TeamStatsAverage? = null
    ): MarketPrediction {

        val homeRecent = homeForm.take(10)
        val awayRecent = awayForm.take(10)

        val homeVenue = homeForm.filter { it.venue.contains("İç saha", true) }.take(10)
        val awayVenue = awayForm.filter { it.venue.contains("Deplasman", true) }.take(10)

        val homeAttack = blendedAverage(homeVenue.map { it.goalsFor }, homeRecent.map { it.goalsFor }, 1.35)
        val homeDefence = blendedAverage(homeVenue.map { it.goalsAgainst }, homeRecent.map { it.goalsAgainst }, 1.35)
        val awayAttack = blendedAverage(awayVenue.map { it.goalsFor }, awayRecent.map { it.goalsFor }, 1.15)
        val awayDefence = blendedAverage(awayVenue.map { it.goalsAgainst }, awayRecent.map { it.goalsAgainst }, 1.35)

        
        fun statFactor(data: TeamStatsAverage?): Double {
            if (data == null) return 1.0

            fun stat(vararg names: String): Double? {
                return data.stats.firstOrNull { item ->
                    names.any { name -> item.name.contains(name, ignoreCase = true) }
                }?.average
            }

            val xg = stat("Gol Beklentisi", "xG")
            val shotsOnTarget = stat("İsabetli Şut")
            val totalShots = stat("Toplam Şut")
            val corners = stat("Korner")

            val xgFactor = xg?.let { 1.0 + (it - 1.2) * 0.12 } ?: 1.0
            val targetFactor = shotsOnTarget?.let { 1.0 + (it - 4.5) * 0.025 } ?: 1.0
            val shotFactor = totalShots?.let { 1.0 + (it - 13.0) * 0.008 } ?: 1.0
            val cornerFactor = corners?.let { 1.0 + (it - 5.0) * 0.012 } ?: 1.0

            return (xgFactor * targetFactor * shotFactor * cornerFactor)
                .coerceIn(0.88, 1.12)
        }

        val homeXg = ((homeAttack + awayDefence) / 2.0 * statFactor(homeStats)).coerceIn(0.20, 4.0)
        val awayXg = ((awayAttack + homeDefence) / 2.0 * statFactor(awayStats)).coerceIn(0.20, 4.0)


        val scoreMatrix = (0..6).flatMap { h ->
            (0..6).map { a ->
                Triple(h, a, poisson(homeXg, h) * poisson(awayXg, a))
            }
        }

        fun goalsFromExpected(xg: Double): Int = when {
            xg < 0.75 -> 0
            xg < 1.50 -> 1
            xg < 2.30 -> 2
            xg < 3.00 -> 3
            else -> 4
        }

        val formHomeGoals = goalsFromExpected(homeXg)
        val formAwayGoals = goalsFromExpected(awayXg)

        val h2hScores = (homeForm
            .filter { it.opponent.trim().equals(awayName.trim(), true) }
            .map { Triple(it.date, it.goalsFor, it.goalsAgainst) } +
            awayForm
            .filter { it.opponent.trim().equals(homeName.trim(), true) }
            .map { Triple(it.date, it.goalsAgainst, it.goalsFor) })
            .distinct()

        val commonH2H = h2hScores.groupingBy { it.second to it.third }
            .eachCount().maxByOrNull { it.value }

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

        // Her pazarda en yüksek yüzdeye sahip seçeneği belirle.
        val selectedResult = adjustedResult.indices.maxByOrNull { adjustedResult[it] } ?: 0
        val selectedGoal = finalGoals.indices.maxByOrNull { finalGoals[it] } ?: 0
        val selectedBtts = finalBtts.indices.maxByOrNull { finalBtts[it] } ?: 0

        // Seçilen üç tahminle en fazla uyuşan, Poisson olasılığı en yüksek skoru bul.
        fun scoreMatches(score: Triple<Int, Int, Double>): Int {
            val h = score.first
            val a = score.second

            val resultMatch = when (selectedResult) {
                0 -> h > a
                1 -> h == a
                else -> h < a
            }

            val goalMatch = when (selectedGoal) {
                0 -> h + a <= 2
                else -> h + a >= 3
            }

            val bttsMatch = when (selectedBtts) {
                0 -> h > 0 && a > 0
                else -> h == 0 || a == 0
            }

            return listOf(resultMatch, goalMatch, bttsMatch).count { it }
        }

        val selectedScore = scoreMatrix.maxWithOrNull(
            compareBy<Triple<Int, Int, Double>> { scoreMatches(it) }
                .thenBy { it.third }
        )!!

        val predictedHomeGoals = selectedScore.first
        val predictedAwayGoals = selectedScore.second

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
            predictedScore = "$predictedHomeGoals-$predictedAwayGoals",
            homeExpectedGoals = homeXg,
            awayExpectedGoals = awayXg,
            scoreProbability = selectedScore.third * 100.0,
            h2hScores = h2hScores.map { "${it.second}-${it.third}" },
            mostCommonH2HScore = commonH2H?.key?.let { "${it.first}-${it.second}" } ?: "-",
            mostCommonH2HCount = commonH2H?.value ?: 0
        )
    }
}
