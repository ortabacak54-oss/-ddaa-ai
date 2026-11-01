package com.example.iddaaai

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

private val Black = Color(0xFF0B0B0D)
private val SurfaceDark = Color(0xFF171719)
private val Surface2 = Color(0xFF222225)
private val Gold = Color(0xFFD6AE55)
private val GoldLight = Color(0xFFF0D58A)
private val TextMain = Color(0xFFF5F2EA)
private val TextMuted = Color(0xFFA5A19A)
private val Green = Color(0xFF65C99A)
private val Red = Color(0xFFE17C78)

data class Match(
    val id: String, val home: String, val away: String, val league: String,
    val kickoff: String, val homeForm: Double, val awayForm: Double,
    val homeGoals: Double, val awayGoals: Double, val isDemo: Boolean = true
)

data class Forecast(val home: Int, val draw: Int, val away: Int, val over25: Int, val btts: Int) {
    val pick: String get() = when (maxOf(home, draw, away)) {
        home -> "MS 1"; draw -> "MS X"; else -> "MS 2"
    }
    val confidence: Int get() = maxOf(home, draw, away)
}

data class Pick(val matchId: String, val fixture: String, val market: String, val odds: Double)
data class Tracked(val matchId: String, val fixture: String, val selection: String, val status: String)

private val sampleMatches = listOf(
    Match("m1", "Galatasaray", "Trabzonspor", "SÜPER LİG", "20:00", .82, .58, 2.1, 1.4),
    Match("m2", "Fenerbahçe", "Beşiktaş", "SÜPER LİG", "20:30", .76, .70, 1.9, 1.7),
    Match("m3", "Arsenal", "Chelsea", "PREMIER LEAGUE", "21:00", .78, .62, 2.0, 1.5),
    Match("m4", "Real Madrid", "Sevilla", "LA LIGA", "22:00", .85, .49, 2.3, 1.1),
    Match("m5", "Inter", "Roma", "SERIE A", "22:45", .79, .61, 2.0, 1.3)
)

private fun forecast(m: Match): Forecast {
    // Demo-only heuristic. Replace with a validated model and real match data before production.
    val h = m.homeForm * .55 + m.homeGoals * .45 + .12
    val a = m.awayForm * .55 + m.awayGoals * .45
    val gap = h - a
    var p1 = (42 + gap * 15).roundToInt().coerceIn(15, 75)
    var p2 = (30 - gap * 15).roundToInt().coerceIn(10, 70)
    var px = (100 - p1 - p2).coerceIn(8, 40)
    val sum = p1 + px + p2
    p1 = (p1 * 100.0 / sum).roundToInt()
    px = (px * 100.0 / sum).roundToInt()
    p2 = 100 - p1 - px
    val goals = m.homeGoals + m.awayGoals
    return Forecast(p1, px, p2, (48 + (goals - 2.5) * 13).roundToInt().coerceIn(18, 82),
        (45 + (goals - 2.0) * 12).roundToInt().coerceIn(20, 80))
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { IddaaApp(this) }
    }
}

@Composable
private fun IddaaApp(context: Context) {
    val prefs = remember { context.getSharedPreferences("iddaa_ai", Context.MODE_PRIVATE) }
    var tab by remember { mutableStateOf("Maçlar") }
    var filter by remember { mutableStateOf("Tümü") }
    var showFinished by remember { mutableStateOf(false) }
    var showLiveOnly by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var stakeText by remember { mutableStateOf("100") }
    var picks by remember { mutableStateOf(loadPicks(prefs)) }
    var tracked by remember { mutableStateOf(loadTracked(prefs)) }
    var notice by remember { mutableStateOf("") }

    var liveMatches by remember { mutableStateOf<List<LiveMatch>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var apiError by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                val freshMatches = withContext(Dispatchers.IO) {
                    LiveApi.getMatches()
                }
                liveMatches = freshMatches
                apiError = ""

                val updatedTracked = gradeTracked(tracked, freshMatches)
                if (updatedTracked != tracked) {
                    tracked = updatedTracked
                    saveTracked(prefs, tracked)
                }
            } catch (e: Exception) {
                apiError = e.message ?: "Bağlantı hatası"
            } finally {
                loading = false
            }

            kotlinx.coroutines.delay(60_000)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Gold, onPrimary = Black, background = Black, surface = SurfaceDark,
        onBackground = TextMain, onSurface = TextMain, secondary = GoldLight
    )) {
        Column(Modifier.fillMaxSize().background(Black)) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("İDDAA  /  AI", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
                        Text("Maç analiz merkezi", color = TextMain, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("BETA", color = Black, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Gold, CircleShape).padding(horizontal = 12.dp, vertical = 7.dp))
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(14.dp)).padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf("Maçlar", "Kupon", "Takip", "İstatistik").forEach { name ->
                        val active = tab == name
                        Box(Modifier.weight(1f).background(if (active) Gold else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable { tab = name }.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                            Text(name, color = if (active) Black else TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            if (notice.isNotBlank()) {
                Text(notice, color = GoldLight, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
            }
            when (tab) {
                "Maçlar" -> {
                    Column(Modifier.padding(horizontal = 18.dp)) {
                        Text("GERÇEK MAÇ FİKSTÜRÜ", color = TextMain,
                            fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            if (loading) "Fikstür yükleniyor..."
                            else "${liveMatches.size} maç bulundu • API-Football",
                            color = TextMuted, fontSize = 11.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Tümü", "Süper Lig", "Avrupa").forEach { f ->
                                FilterChip(
                                    selected = filter == f,
                                    onClick = { filter = f },
                                    label = { Text(f, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Gold,
                                        selectedLabelColor = Black
                                    )
                                )
                            }
                        }
                    }

                    Row(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = showFinished,
                        onClick = { showFinished = !showFinished },
                        label = { Text(if (showFinished) "Bitmiş maçlar gösteriliyor" else "Bitmiş maçları göster", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Gold,
                            selectedLabelColor = Black
                        )
                    )

                FilterChip(
                    selected = showLiveOnly,
                    onClick = { showLiveOnly = !showLiveOnly },
                    label = { Text("Canlı Maçları Gör", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF238636),
                        selectedLabelColor = Color.White
                    )
                )
            }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    placeholder = { Text("Takım veya lig ara...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = Surface2,
                        focusedTextColor = TextMain,
                        unfocusedTextColor = TextMain,
                        cursorColor = Gold
                    )
                )

                val shown = liveMatches.filter { match ->
                    val leagueMatch = filter == "Tümü" ||
                        (filter == "Süper Lig" && match.league.contains("Süper Lig", true)) ||
                        (filter == "Avrupa" && !match.league.contains("Süper Lig", true))

                    val queryMatch = searchQuery.isBlank() ||
                        match.home.contains(searchQuery.trim(), true) ||
                        match.away.contains(searchQuery.trim(), true) ||
                        match.league.contains(searchQuery.trim(), true)

                    val finished = match.status.uppercase() in listOf("FT", "AET", "PEN")
                    val live = match.status.uppercase() in listOf("1H", "2H", "HT", "ET", "BT", "P", "LIVE")
                    leagueMatch && queryMatch && (showFinished || !finished) && (!showLiveOnly || live)
                }

                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 18.dp, end = 18.dp, top = 8.dp, bottom = 18.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (loading) {
                            item { Text("Fikstür alınıyor...", color = GoldLight) }
                        } else if (apiError.isNotBlank()) {
                            item { EmptyPanel("Veri alınamadı", apiError) }
                        } else if (shown.isEmpty()) {
                            item {
                                EmptyPanel("Maç bulunamadı",
                                    "Seçilen filtrede şu anda fikstür bulunmuyor.")
                            }
                        } else {
                            items(shown, key = { it.id }) { m ->
                                LiveMatchCard(m) { market, odd ->
                            val pick = Pick(m.id, "${m.home} - ${m.away}", market, odd)
                            if (picks.none { it.matchId == pick.matchId && it.market == pick.market }) {
                                picks = picks + pick
                                savePicks(prefs, picks)
                                notice = "$market kupona eklendi."
                            } else {
                                notice = "Bu seçim zaten kuponda."
                            }
                        }
                            }
                        }
                        item { Disclaimer() }
                    }
                }
                "Kupon" -> CouponScreen(picks, stakeText, { stakeText = it }, { p ->
                    picks = picks.filterNot { it == p }; savePicks(prefs, picks)
                }, { p ->
                    tracked = tracked + Tracked(p.matchId, p.fixture, p.market, "Bekliyor")
                    saveTracked(prefs, tracked)
                    picks = emptyList(); savePicks(prefs, picks)
                    notice = "Kupon seçimleri takip listesine eklendi."
                })
                "Takip" -> TrackScreen(tracked, { index, status ->
                    tracked = tracked.mapIndexed { i, t -> if (i == index) t.copy(status = status) else t }
                    saveTracked(prefs, tracked)
                }, { tracked = emptyList(); saveTracked(prefs, tracked) })
                else -> StatsScreen(tracked)
            }
        }
    }
}


@Composable
private fun LiveMatchCard(m: LiveMatch, onAdd: (String, Double) -> Unit) {
    var odds: MatchOdds? by remember(m.id) { mutableStateOf<MatchOdds?>(null) }
    var oddsLoading: Boolean by remember(m.id) { mutableStateOf(false) }
    var oddsError: String by remember(m.id) { mutableStateOf("") }

    var homeForm by remember(m.id) { mutableStateOf<List<TeamFormMatch>>(emptyList()) }
    var awayForm by remember(m.id) { mutableStateOf<List<TeamFormMatch>>(emptyList()) }
    var formLoading by remember(m.id) { mutableStateOf(false) }
    var formError by remember(m.id) { mutableStateOf("") }
    var formLoaded by remember(m.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.league, color = Gold, fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Text(
                    if (m.minute.isNotBlank() && m.minute != "null")
                        "${m.minute}'"
                    else m.kickoff.take(16).replace("T", " "),
                    color = TextMuted, fontSize = 10.sp
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.home, color = TextMain, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(m.score, color = GoldLight,
                    fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text(m.away, color = TextMain, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f))
            }

            Spacer(Modifier.height(9.dp))
            val matchStatus = m.status.uppercase()
            val statusLabel = when (matchStatus) {
                "1H", "2H", "ET", "LIVE" -> "CANLI"
                "HT" -> "DEVRE ARASI"
                "FT", "AET", "PEN" -> "MAÇ BİTTİ"
                "NS" -> "BAŞLAMADI"
                else -> m.status
            }
            val statusColor = when (matchStatus) {
                "1H", "2H", "ET", "LIVE" -> Color(0xFF2ECC71)
                "FT", "AET", "PEN" -> Color(0xFFDA3633)
                else -> TextMuted
            }
            Text(
                "● $statusLabel",
                color = statusColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    scope.launch {
                        formLoading = true
                        formError = ""
                        try {
                            val results = withContext(Dispatchers.IO) {
                                Pair(
                                    LiveApi.getTeamForm(m.homeId),
                                    LiveApi.getTeamForm(m.awayId)
                                )
                            }
                            homeForm = results.first
                            awayForm = results.second
                            formLoaded = true
                        } catch (e: Exception) {
                            formError = "Form verisi alınamadı: ${e.message}"
                        } finally {
                            formLoading = false
                        }
                    }
                },
                enabled = !formLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Surface2,
                    contentColor = GoldLight
                )
            ) {
                Text(
                    "TAKIM FORMUNU GETİR",
                    fontWeight = FontWeight.Bold
                )
            }

            if (formError.isNotBlank()) {
                Text(formError, color = Red, fontSize = 11.sp)
            }

            if (formLoaded) {
                Spacer(Modifier.height(10.dp))
                TeamFormSection(m.home, homeForm)
                Spacer(Modifier.height(8.dp))
                TeamFormSection(m.away, awayForm)
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    scope.launch {
                        oddsLoading = true
                        oddsError = ""
                        try {
                            odds = withContext(Dispatchers.IO) {
                                LiveApi.getOdds(m.id)
                            }
                        } catch (e: Exception) {
                            oddsError = e.message ?: "Oranlar alınamadı"
                        } finally {
                            oddsLoading = false
                        }
                    }
                },
                enabled = !oddsLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gold,
                    contentColor = Black
                )
            ) {
                Text(
                    if (oddsLoading) "Oranlar yükleniyor..."
                    else if (odds != null) "Oranları Yenile"
                    else "Oranları Getir",
                    fontWeight = FontWeight.Bold
                )
            }

            if (oddsError.isNotBlank()) {
                Text(oddsError, color = Color.Red, fontSize = 11.sp)
            }

            odds?.let { o ->
                Spacer(Modifier.height(12.dp))
                Text("MAÇ SONUCU (1X2)", color = Gold,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
                OddsRow("MS 1", o.home, "X", o.draw, "MS 2", o.away)

                Spacer(Modifier.height(10.dp))
                Text("2.5 GOL ALT / ÜST", color = Gold,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
                OddsRow("Alt 2.5", o.under25, "Üst 2.5", o.over25)

                Spacer(Modifier.height(10.dp))
                Text("KARŞILIKLI GOL", color = Gold,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
                OddsRow("KG Var", o.bttsYes, "KG Yok", o.bttsNo)

                if (o.markets.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text("TÜM BAHİS PAZARLARI (${o.markets.size})",
                        color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    o.markets.forEach { market ->
                        var expanded by remember(m.id, market.name) {
                            mutableStateOf(false)
                        }
                        Column(
                            Modifier.fillMaxWidth()
                                .background(Surface2, RoundedCornerShape(10.dp))
                                .clickable { expanded = !expanded }
                                .padding(11.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(market.name, color = Color.White,
                                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (expanded) "−" else "+",
                                    color = Gold, fontSize = 16.sp)
                            }
                            if (expanded) {
                                Spacer(Modifier.height(8.dp))
                                market.options.forEach { option ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(option.selection, color = TextMuted, fontSize = 12.sp)
                                        Text(option.odd, color = Gold, fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }

                val prediction = PredictionEngine.calculate(o, homeForm, awayForm, m.home, m.away)

                    Spacer(Modifier.height(12.dp))
                    Text("KUPONA SEÇİM EKLE", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    @Composable
                    fun addMarket(label: String, rawOdds: String, modifier: Modifier) {
                        val odd = rawOdds.replace(",", ".").toDoubleOrNull()
                        if (odd != null && odd > 1.0) {
                            MarketButton(label, 0, odd, modifier, onAdd)
                        } else {
                            Text("$label: Oran yok", color = TextMuted, fontSize = 10.sp, modifier = modifier)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        addMarket("MS 1", o.home, Modifier.weight(1f))
                        addMarket("MS X", o.draw, Modifier.weight(1f))
                        addMarket("MS 2", o.away, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        addMarket("2.5 ALT", o.under25, Modifier.weight(1f))
                        addMarket("2.5 ÜST", o.over25, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        addMarket("KG VAR", o.bttsYes, Modifier.weight(1f))
                        addMarket("KG YOK", o.bttsNo, Modifier.weight(1f))
                    }


                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Surface2)

                Spacer(Modifier.height(12.dp))
                Text(
                    "IDDAA AI ANALİZİ",
                    color = GoldLight,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(Modifier.height(10.dp))
                Text("TAHMİN OLASILIKLARI",
                    color = TextMuted, fontSize = 10.sp)

                @Composable
                fun showProbability(label: String, value: Double?) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, color = TextMain,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f))
                        Text(
                            value?.let { "%.1f%%".format(java.util.Locale.US, it) }
                                ?: "Veri yok",
                            color = GoldLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                showProbability("MS 1", prediction.home)
                showProbability("Beraberlik", prediction.draw)
                showProbability("MS 2", prediction.away)
                showProbability("2.5 Alt", prediction.under25)
                showProbability("2.5 Üst", prediction.over25)
                showProbability("KG Var", prediction.bttsYes)
                showProbability("KG Yok", prediction.bttsNo)

                Spacer(Modifier.height(10.dp))
                Text(
                    "ORANLARA GÖRE EN YÜKSEK OLASILIK",
                    color = Gold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    prediction.bestPick +
                        (prediction.confidence?.let {
                            " • %.1f%%".format(java.util.Locale.US, it)
                        } ?: ""),
                    color = GoldLight,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    "İSTATİSTİKSEL SKOR TAHMİNİ",
                    color = Gold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    prediction.predictedScore,
                    color = GoldLight,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "TAKIMLARIN KARŞILIKLI MAÇLARI",
                    color = Gold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "En sık skor: ${prediction.mostCommonH2HScore} (${prediction.mostCommonH2HCount} kez)",
                    color = GoldLight,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (prediction.h2hScores.isEmpty()) "Son form verilerinde karşılıklı maç bulunamadı"
                    else prediction.h2hScores.joinToString("  • "),
                    color = TextMuted,
                    fontSize = 11.sp
                )

                Text(
                    "Beklenen gol: %.2f - %.2f • Skor olasılığı: %.1f%%".format(
                        java.util.Locale.US,
                        prediction.homeExpectedGoals,
                        prediction.awayExpectedGoals,
                        prediction.scoreProbability
                    ),
                    color = TextMuted,
                    fontSize = 11.sp
                )

                Text(
                    "Bu yüzdeler bahis oranlarından hesaplanan marjı düzeltilmiş piyasa olasılıklarıdır; garanti veya bağımsız AI tahmini değildir.",
                    color = TextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun TeamFormSection(teamName: String, matches: List<TeamFormMatch>) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Surface2, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Text(teamName, color = GoldLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (matches.isEmpty()) {
            Text("Form maçı bulunamadı.", color = TextMuted, fontSize = 10.sp)
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 7.dp)
            ) {
                matches.takeLast(5).forEach { match ->
                    val resultColor = when (match.result) {
                        "G" -> Green
                        "M" -> Red
                        else -> Gold
                    }
                    Text(
                        match.result,
                        color = Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(resultColor, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            Text(
                "Son ${matches.size} maç • Attığı ${matches.sumOf { it.goalsFor }} gol • Yediği ${matches.sumOf { it.goalsAgainst }} gol",
                color = TextMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun OddsRow(vararg values: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        values.toList().chunked(2).forEach { pair ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Surface2, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(pair[0], color = TextMuted, fontSize = 10.sp)
                Text(pair.getOrElse(1) { "-" }, color = GoldLight,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MatchCard(m: Match, added: Boolean, onAdd: (String, Double) -> Unit) {
    val f = remember(m) { forecast(m) }
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.league, color = Gold, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                Text(m.kickoff, color = TextMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.home, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text("VS", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(m.away, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(15.dp))
            Text("1X2 TAHMİN OLASILIĞI", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Probability("1", f.home, Modifier.weight(1f))
                Probability("X", f.draw, Modifier.weight(1f))
                Probability("2", f.away, Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Öne çıkan sonuç", color = TextMuted, fontSize = 10.sp)
                    Text(f.pick, color = GoldLight, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text("Model puanı %${f.confidence}", color = TextMuted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text("DİĞER PAZARLAR", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                MarketButton("2.5 ÜST", f.over25, 1.85, Modifier.weight(1f), onAdd)
                MarketButton("KG VAR", f.btts, 1.72, Modifier.weight(1f), onAdd)
                MarketButton("MS 1", f.home, 2.05, Modifier.weight(1f), onAdd)
            }
            Text(if (added) "✓ Kuponunda seçim var" else "Seçime dokunarak kupona ekle",
                color = if (added) Green else TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun Probability(label: String, value: Int, modifier: Modifier) {
    Column(modifier.background(Surface2, RoundedCornerShape(10.dp)).padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextMuted, fontSize = 10.sp)
        Text("%$value", color = GoldLight, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun MarketButton(label: String, chance: Int, odds: Double, modifier: Modifier, onAdd: (String, Double) -> Unit) {
    Column(modifier.background(Surface2, RoundedCornerShape(10.dp)).clickable { onAdd(label, odds) }.padding(vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextMain, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("${odds}x", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("%$chance", color = TextMuted, fontSize = 9.sp)
    }
}

@Composable
private fun CouponScreen(picks: List<Pick>, stakeText: String, onStake: (String) -> Unit,
                         onRemove: (Pick) -> Unit, onSave: (Pick) -> Unit) {
    val totalOdds = picks.fold(1.0) { acc, p -> acc * p.odds }
    val stake = stakeText.toDoubleOrNull() ?: 0.0
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Text("KUPON OLUŞTURUCU", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("Seçimlerini yönet ve olası getiriyi hesapla", color = TextMuted, fontSize = 11.sp)
        Spacer(Modifier.height(14.dp))
        if (picks.isEmpty()) {
            EmptyPanel("Kuponun henüz boş", "Maçlar sekmesinden bir market seçerek kuponuna ekleyebilirsin.")
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(picks) { p ->
                    Row(Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.fixture, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("${p.market}  •  ${p.odds} oran", color = Gold, fontSize = 11.sp)
                        }
                        TextButton(onClick = { onRemove(p) }) { Text("Sil", color = Red) }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Kupon oranı", color = TextMuted, fontSize = 11.sp)
        Text(if (picks.isEmpty()) "—" else String.format("%.2f", totalOdds), color = GoldLight, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = stakeText, onValueChange = onStake, label = { Text("Misli / tutar (₺)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Gold, focusedLabelColor = Gold, cursorColor = Gold))
        Text("Olası brüt ödeme: ${if (stake > 0 && picks.isNotEmpty()) String.format("%.2f", stake * totalOdds) else "—"} ₺",
            color = TextMain, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 10.dp))
        Button(onClick = { picks.forEach(onSave) }, enabled = picks.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Black)) {
            Text("Seçimleri takibe al", fontWeight = FontWeight.Bold)
        }
        Disclaimer()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun TrackScreen(records: List<Tracked>, onStatus: (Int, String) -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TAHMİN TAKİBİ", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear, enabled = records.isNotEmpty()) { Text("Temizle", color = Red) }
        }
        Text("Maç sonuçları otomatik takip edilir.", color = TextMuted, fontSize = 11.sp)
        Spacer(Modifier.height(12.dp))
        if (records.isEmpty()) EmptyPanel("Henüz takip edilen seçim yok", "Kuponundaki seçimleri takibe alarak burada sonuçlandırabilirsin.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(records.size) { i ->
                val item = records[i]
                Column(Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(13.dp)).padding(13.dp)) {
                    Text(item.fixture, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(item.selection, color = Gold, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                    item.status,
                    color = when (item.status) {
                        "Kazandı" -> Color(0xFF238636)
                        "Kaybetti" -> Color(0xFFDA3633)
                        else -> GoldLight
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                }
            }
        }
    }
}

@Composable
private fun StatsScreen(items: List<Tracked>) {
    val settled = items.filter { it.status == "Kazandı" || it.status == "Kaybetti" }
    val wins = settled.count { it.status == "Kazandı" }
    val rate = if (settled.isEmpty()) 0 else (wins * 100.0 / settled.size).roundToInt()
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("PERFORMANS ÖZETİ", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text("Yalnızca elle sonuçlandırılmış takip kayıtları", color = TextMuted, fontSize = 11.sp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatCard("TAKİP", "${items.size}", Modifier.weight(1f))
            StatCard("SONUÇLANAN", "${settled.size}", Modifier.weight(1f))
            StatCard("BAŞARI", "%$rate", Modifier.weight(1f))
        }
        Spacer(Modifier.height(15.dp))
        Text("Kazandı: $wins   •   Kaybetti: ${settled.size - wins}   •   Bekliyor: ${items.count { it.status == "Bekliyor" }}",
            color = TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        EmptyPanel("Veri dürüstlüğü", "Örnek fikstür ve sezgisel olasılıklar gerçek model performansı değildir. Başarı oranı, yalnızca senin sonuçlandırdığın kayıtlarla hesaplanır.")
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier) {
    Column(modifier.background(SurfaceDark, RoundedCornerShape(13.dp)).padding(12.dp)) {
        Text(title, color = TextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(value, color = GoldLight, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun EmptyPanel(title: String, body: String) {
    Column(Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(16.dp)).padding(18.dp)) {
        Text(title, color = GoldLight, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(body, color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun Disclaimer() {
    Text("Bilgilendirme: Bu sürüm demo veriler ve eğitilmemiş örnek bir sezgisel kullanır. Gerçek maç tahmini, canlı oran veya kazanç garantisi sunmaz. Bahis kayıp riski taşır.",
        color = TextMuted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
}

private fun savePicks(prefs: android.content.SharedPreferences, picks: List<Pick>) {
    val arr = JSONArray()
    picks.forEach { p -> arr.put(JSONObject().put("id", p.matchId).put("fixture", p.fixture).put("market", p.market).put("odds", p.odds)) }
    prefs.edit().putString("picks", arr.toString()).apply()
}
private fun loadPicks(prefs: android.content.SharedPreferences): List<Pick> = try {
    val arr = JSONArray(prefs.getString("picks", "[]"))
    (0 until arr.length()).map { i -> arr.getJSONObject(i).let { Pick(it.getString("id"), it.getString("fixture"), it.getString("market"), it.getDouble("odds")) } }
} catch (_: Exception) { emptyList() }

private fun gradeTracked(records: List<Tracked>, matches: List<LiveMatch>): List<Tracked> {
    return records.map { record ->
        if (record.status != "Bekliyor" || record.matchId.isBlank()) {
            record
        } else {
            val match = matches.firstOrNull { it.id == record.matchId }
            if (match == null || match.status.uppercase() !in listOf("FT", "AET", "PEN")) {
                record
            } else {
                val goals = Regex("\\d+").findAll(match.score).mapNotNull { it.value.toIntOrNull() }.toList()
                if (goals.size < 2) record
                else {
                    val home = goals[0]
                    val away = goals[1]
                    val total = home + away
                    val won = when (record.selection.uppercase().replace("İ", "I")) {
                        "MS 1" -> home > away
                        "MS X", "BERABERLIK" -> home == away
                        "MS 2" -> away > home
                        "2.5 ÜST", "2.5 UST" -> total >= 3
                        "2.5 ALT" -> total <= 2
                        "KG VAR" -> home > 0 && away > 0
                        "KG YOK" -> home == 0 || away == 0
                        else -> null
                    }
                    when (won) {
                        true -> record.copy(status = "Kazandı")
                        false -> record.copy(status = "Kaybetti")
                        null -> record
                    }
                }
            }
        }
    }
}

private fun saveTracked(prefs: android.content.SharedPreferences, items: List<Tracked>) {
    val arr = JSONArray()
    items.forEach { t -> arr.put(JSONObject().put("id", t.matchId).put("fixture", t.fixture).put("selection", t.selection).put("status", t.status)) }
    prefs.edit().putString("tracked", arr.toString()).apply()
}
private fun loadTracked(prefs: android.content.SharedPreferences): List<Tracked> = try {
    val arr = JSONArray(prefs.getString("tracked", "[]"))
    (0 until arr.length()).map { i -> arr.getJSONObject(i).let { Tracked(it.optString("id", ""), it.getString("fixture"), it.getString("selection"), it.getString("status")) } }
} catch (_: Exception) { emptyList() }
