package com.example.iddaaai

data class MatchConditions(
    // 0.0 = eksik yok, 1.0 = çok önemli eksik
    val homeAttackAbsence: Double = 0.0,
    val homeDefenseAbsence: Double = 0.0,
    val homeGoalkeeperAbsent: Double = 0.0,

    val awayAttackAbsence: Double = 0.0,
    val awayDefenseAbsence: Double = 0.0,
    val awayGoalkeeperAbsent: Double = 0.0,

    // -0.20 = gol beklentisini azaltır, 0.05 = hafif artırır
    val weatherGoalImpact: Double = 0.0,

    // 0.0 = nötr, 1.0 = çok güçlü kadro
    val homeLineupStrength: Double = 0.5,
    val awayLineupStrength: Double = 0.5,

    // -0.15 ile +0.15 arasında taktik avantajı
    val homeTacticalAdvantage: Double = 0.0,

    // Hakem verileri bilgilendirme amaçlıdır
    val refereePenaltiesPerMatch: Double? = null,
    val refereeYellowCardsPerMatch: Double? = null,
    val refereeRedCardsPerMatch: Double? = null,

    val lineupsConfirmed: Boolean = false
)
