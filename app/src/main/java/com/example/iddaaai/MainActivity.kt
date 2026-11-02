package com.example.iddaaai

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll


import android.content.Context
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.scale
import androidx.compose.animation.core.*
import android.media.ToneGenerator
import android.media.AudioManager
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

data class Pick(val matchId: String, val fixture: String, val market: String, val odds: Double, val modelProbability: Double? = null)
data class Tracked(val matchId: String, val fixture: String, val selection: String, val status: String, val predictedScore: String = "", val modelProbability: Double? = null)

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
        setContent {
            var showIntro by remember { mutableStateOf(true) }
            if (showIntro) {
                IntroScreen { showIntro = false }
            } else {
                IddaaApp(this)
            }
        }
    }
}

@Composable
private fun IntroScreen(onFinished: () -> Unit) {
    var soundEnabled by remember { mutableStateOf(true) }

    val animation = rememberInfiniteTransition(label = "intro")
    val pulse by animation.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(1000, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pulse"
    )

    LaunchedEffect(Unit) {
        delay(350)
        if (soundEnabled) {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 450)
            delay(500)
            tone.release()
        }
        delay(2200)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF080D19),
                        Color(0xFF182238),
                        Color(0xFF080D19)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("⚽", fontSize = 66.sp, modifier = Modifier.scale(pulse))
            Spacer(Modifier.height(20.dp))
            Text(
                "IDDAA AI",
                color = GoldLight,
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 5.sp,
                modifier = Modifier.scale(pulse)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "AKILLI FUTBOL ANALİZİ",
                color = TextMuted,
                fontSize = 12.sp,
                letterSpacing = 3.sp
            )
            Spacer(Modifier.height(42.dp))
            CircularProgressIndicator(
                color = Gold,
                strokeWidth = 3.dp,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "TAHMİNLER HAZIRLANIYOR",
                color = GoldLight,
                fontSize = 11.sp,
                letterSpacing = 2.sp
            )
        }

        TextButton(
            onClick = { soundEnabled = !soundEnabled },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) {
            Text(
                if (soundEnabled) "SES AÇIK" else "SES KAPALI",
                color = GoldLight
            )
        }

        Text(
            "POWERED BY IDDAA AI",
            color = TextMuted,
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
        )
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
    var analysisRecords by remember { mutableStateOf(loadAnalysisRecords(prefs)) }
    var notice by remember { mutableStateOf("") }

    var showBulkDialog by remember { mutableStateOf(false) }
    var bulkRunning by remember { mutableStateOf(false) }
    var bulkProgress by remember { mutableIntStateOf(0) }
    var bulkTotal by remember { mutableIntStateOf(0) }
    var bulkErrors by remember { mutableIntStateOf(0) }
    val bulkScope = rememberCoroutineScope()


    var liveMatches by remember { mutableStateOf<List<LiveMatch>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    fun bulkFormSummary(form: List<TeamFormMatch>): String {
        val recent = form.take(5)
        val w = recent.count { it.goalsFor > it.goalsAgainst }
        val d = recent.count { it.goalsFor == it.goalsAgainst }
        val l = recent.count { it.goalsFor < it.goalsAgainst }
        val n = recent.size.coerceAtLeast(1)
        val gf = recent.sumOf { it.goalsFor }
        val ga = recent.sumOf { it.goalsAgainst }
        return "Son ${recent.size}: $w G / $d B / $l M | " +
            "Atılan ort. ${"%.2f".format(java.util.Locale.US, gf.toDouble()/n)} | " +
            "Yenilen ort. ${"%.2f".format(java.util.Locale.US, ga.toDouble()/n)}"
    }

    fun bulkStatsSummary(data: TeamStatsAverage): String {
        return "İncelenen ${data.games} maç | " +
            data.stats.joinToString(" • ") {
                "${it.name}: ${"%.1f".format(java.util.Locale.US, it.average)}"
            }.ifBlank { "İstatistik bulunamadı" }
    }

    fun startBulkAnalysis(category: String) {
        if (bulkRunning) return

        val selected = liveMatches.filter { m ->
            val status = m.status.uppercase()
            val finished = status in listOf("FT", "AET", "PEN", "FINISHED", "ENDED")
            val live = status in listOf("1H", "2H", "HT", "ET", "BT", "P", "LIVE")

            val categoryMatch = when (category) {
                "Süper Lig" -> m.league.contains("Süper Lig", true) && !finished
                "Avrupa" -> !m.league.contains("Süper Lig", true) && !finished
                "Canlı" -> live
                else -> false
            }

            categoryMatch &&
                m.id.isNotBlank() &&
                m.homeId.isNotBlank() &&
                m.awayId.isNotBlank()
        }

        bulkProgress = 0
        bulkTotal = selected.size
        bulkErrors = 0

        if (selected.isEmpty()) {
            notice = "Bu kategoride analiz edilecek maç bulunamadı."
            return
        }

        bulkRunning = true

        bulkScope.launch {
            for (m in selected) {
                try {
                    val forms = withContext(Dispatchers.IO) {
                        val home = LiveApi.getTeamForm(m.homeId)
                        val away = LiveApi.getTeamForm(m.awayId)
                        Pair(home, away)
                    }

                    val averages = withContext(Dispatchers.IO) {
                        val home = LiveApi.getTeamStatsAverage(m.homeId, 5)
                        val away = LiveApi.getTeamStatsAverage(m.awayId, 5)
                        Pair(home, away)
                    }

                    if (
                        forms.first.isEmpty() ||
                        forms.second.isEmpty() ||
                        averages.first.stats.isEmpty() ||
                        averages.second.stats.isEmpty()
                    ) {
                        bulkErrors++
                    } else {
                        // Bilerek MatchOdds() kullanılıyor: hiçbir oran API çağrısı yapılmıyor.
                        val result = PredictionEngine.calculate(
                            MatchOdds(),
                            forms.first,
                            forms.second,
                            m.home,
                            m.away,
                            averages.first,
                            averages.second
                        )

                        val old = analysisRecords.firstOrNull { it.matchId == m.id }

                        val record = AnalysisRecord(
                            id = old?.id ?: "${m.id}_${System.currentTimeMillis()}",
                            matchId = m.id,
                            fixture = "${m.home} - ${m.away}",
                            predictedScore = result.predictedScore,
                            actualScore = old?.actualScore ?: "",
                            ms1 = result.home ?: 0.0,
                            msx = result.draw ?: 0.0,
                            ms2 = result.away ?: 0.0,
                            over25 = result.over25 ?: 0.0,
                            under25 = result.under25 ?: 0.0,
                            bttsYes = result.bttsYes ?: 0.0,
                            bttsNo = result.bttsNo ?: 0.0,
                            homeFormSummary = bulkFormSummary(forms.first),
                            awayFormSummary = bulkFormSummary(forms.second),
                            homeStatsSummary = bulkStatsSummary(averages.first),
                            awayStatsSummary = bulkStatsSummary(averages.second)
                        )

                        analysisRecords =
                            analysisRecords.filterNot { it.matchId == m.id } + record

                        saveAnalysisRecords(prefs, analysisRecords)
                    }
                } catch (e: Exception) {
                    bulkErrors++
                }

                bulkProgress++
                delay(400)
            }

            bulkRunning = false
            notice = "Toplu analiz tamamlandı: " +
                "${bulkTotal - bulkErrors}/${bulkTotal} başarılı, $bulkErrors hatalı."
        }
    }

    var apiError by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                val freshMatches = withContext(Dispatchers.IO) {
                    LiveApi.getMatches()
                }
                liveMatches = freshMatches
            val updatedRecords = updateAnalysisResults(
                analysisRecords,
                freshMatches,
                { it.id },
                { it.status },
                { it.score }
            )
            if (updatedRecords != analysisRecords) {
                analysisRecords = updatedRecords
                saveAnalysisRecords(prefs, analysisRecords)
            }
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
                    listOf("Maçlar", "Kupon", "Takip", "Kayıtlar", "İstatistik").forEach { name ->
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

                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { showBulkDialog = true },
                            enabled = !bulkRunning,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("TOPLU ANALİZ")
                        }

                        if (bulkRunning) {
                            Text(
                                "Analiz edilen $bulkProgress / $bulkTotal • Hatalı: $bulkErrors",
                                color = GoldLight,
                                fontSize = 12.sp
                            )
                            LinearProgressIndicator(
                                progress = {
                                    if (bulkTotal > 0)
                                        bulkProgress.toFloat() / bulkTotal
                                    else 0f
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (showBulkDialog) {
                            AlertDialog(
                                onDismissRequest = { showBulkDialog = false },
                                title = { Text("Toplu Analiz") },
                                text = {
                                    Column {
                                        listOf("Avrupa", "Süper Lig", "Canlı").forEach { choice ->
                                            TextButton(
                                                onClick = {
                                                    showBulkDialog = false
                                                    startBulkAnalysis(choice)
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(choice)
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showBulkDialog = false }) {
                                        Text("Kapat")
                                    }
                                }
                            )
                        }
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
                                LiveMatchCard(m, onAutoAdd = { market, probability, score ->
                            val autoPick = Pick(m.id, "${m.home} - ${m.away}", market, 0.0, probability)
                            if (picks.none { it.matchId == m.id && it.market == market }) {
                                picks = picks + autoPick
                                savePicks(prefs, picks)
                            }
                            if (tracked.none { it.matchId == m.id && it.selection == market }) {
                                tracked = tracked + Tracked(m.id, "${m.home} - ${m.away}", market, "Bekliyor", score, probability)
                                saveTracked(prefs, tracked)
                            }
                            notice = "$market otomatik olarak kupona ve takibe eklendi."
                        }, onSaveAnalysis = { record ->
                            val oldRecord = analysisRecords.firstOrNull {
                                it.matchId == record.matchId
                            }
                            analysisRecords =
                                analysisRecords.filterNot {
                                    it.matchId == record.matchId
                                } + record.copy(
                                    actualScore = oldRecord?.actualScore ?: record.actualScore
                                )
                            saveAnalysisRecords(prefs, analysisRecords)
                            notice = "Tüm tahminler Kayıtlar sekmesine eklendi."
                        }) { market, odd ->
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
                "Kayıtlar" -> AnalysisRecordsScreen(analysisRecords)
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
private fun LiveMatchCard(m: LiveMatch, onAutoAdd: (String, Double, String) -> Unit, onSaveAnalysis: (AnalysisRecord) -> Unit, onAdd: (String, Double) -> Unit) {
    var odds: MatchOdds? by remember(m.id) { mutableStateOf<MatchOdds?>(null) }
    var oddsLoading: Boolean by remember(m.id) { mutableStateOf(false) }
    var oddsError: String by remember(m.id) { mutableStateOf("") }
    var oddsExpanded by remember(m.id) { mutableStateOf(false) }

    var homeForm by remember(m.id) { mutableStateOf<List<TeamFormMatch>>(emptyList()) }
    var awayForm by remember(m.id) { mutableStateOf<List<TeamFormMatch>>(emptyList()) }
    var formLoading by remember(m.id) { mutableStateOf(false) }
    var formError by remember(m.id) { mutableStateOf("") }
    var homeStatsAverage by remember(m.id) { mutableStateOf<TeamStatsAverage?>(null) }
    var awayStatsAverage by remember(m.id) { mutableStateOf<TeamStatsAverage?>(null) }
    var statsLoading by remember(m.id) { mutableStateOf(false) }
    var statsError by remember(m.id) { mutableStateOf("") }
    var formLoaded by remember(m.id) { mutableStateOf(false) }
      var analysisDone by remember(m.id) { mutableStateOf(false) }
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



                Button(
                    onClick = {
                        scope.launch {
                            statsLoading = true
                            statsError = ""
                            try {
                                val averages = withContext(Dispatchers.IO) {
                                    val home = LiveApi.getTeamStatsAverage(m.homeId, 5)
                                    val away = LiveApi.getTeamStatsAverage(m.awayId, 5)
                                    Pair(home, away)
                                }
                                homeStatsAverage = averages.first
                                awayStatsAverage = averages.second
                            } catch (e: Exception) {
                                statsError = e.message ?: "İstatistikler alınamadı"
                            } finally {
                                statsLoading = false
                            }
                        }
                    },
                    enabled = formLoaded && !formLoading && !statsLoading && homeForm.isNotEmpty() && awayForm.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Surface2,
                        contentColor = GoldLight
                    )
                ) {
                    Text(if (statsLoading) "SON 5 MAÇ ANALİZ EDİLİYOR..." else "SON 5 MAÇ İSTATİSTİK ORTALAMALARI")
                }

                if (statsError.isNotBlank()) {
                    Text(statsError, color = Red, fontSize = 11.sp)
                }

                @Composable
                fun AverageSection(title: String, data: TeamStatsAverage?) {
                    if (data == null) return
                    Text("$title (${data.games} maç)", color = Gold, fontWeight = FontWeight.Bold)
                    data.stats.forEach { stat ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stat.name, color = TextMain, fontSize = 11.sp)
                            Text(
                                String.format(java.util.Locale.US, "%.1f", stat.average),
                                color = GoldLight,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                AverageSection(m.home, homeStatsAverage)
                Spacer(Modifier.height(6.dp))
                AverageSection(m.away, awayStatsAverage)
                Spacer(Modifier.height(12.dp))
              Spacer(Modifier.height(12.dp))
              Button(
                  onClick = {
                      val result = PredictionEngine.calculate(
                          MatchOdds(), homeForm, awayForm, m.home, m.away,
                          homeStatsAverage, awayStatsAverage
                      )
                      val markets = listOf(
                          "MS 1" to (result.home ?: 0.0),
                          "MS X" to (result.draw ?: 0.0),
                          "MS 2" to (result.away ?: 0.0),
                          "2.5 Alt" to (result.under25 ?: 0.0),
                          "2.5 Üst" to (result.over25 ?: 0.0),
                          "KG Var" to (result.bttsYes ?: 0.0),
                          "KG Yok" to (result.bttsNo ?: 0.0)
                      )
                      val best = markets.maxByOrNull { it.second }
                      if (best != null) onAutoAdd(best.first, best.second, result.predictedScore)
                        onSaveAnalysis(
                            AnalysisRecord(
                                id = "${m.id}_${System.currentTimeMillis()}",
                                matchId = m.id,
                                fixture = "${m.home} - ${m.away}",
                                predictedScore = result.predictedScore,
                                ms1 = result.home ?: 0.0,
                                msx = result.draw ?: 0.0,
                                ms2 = result.away ?: 0.0,
                                over25 = result.over25 ?: 0.0,
                                under25 = result.under25 ?: 0.0,
                                bttsYes = result.bttsYes ?: 0.0,
                                bttsNo = result.bttsNo ?: 0.0
                            )
                        )
                      analysisDone = true
                  },
                  enabled = formLoaded && !statsLoading &&
                      homeStatsAverage?.stats?.isNotEmpty() == true &&
                      awayStatsAverage?.stats?.isNotEmpty() == true,
                  modifier = Modifier.fillMaxWidth(),
                  colors = ButtonDefaults.buttonColors(
                      containerColor = Gold,
                      contentColor = Color.Black
                  )
              ) {
                  Text("ANALİZ ET", fontWeight = FontWeight.Bold)
              }

              if (analysisDone &&
                  homeStatsAverage?.stats?.isNotEmpty() == true &&
                  awayStatsAverage?.stats?.isNotEmpty() == true
              ) {
                  val prediction = PredictionEngine.calculate(
                      MatchOdds(), homeForm, awayForm, m.home, m.away,
                      homeStatsAverage, awayStatsAverage
                  )
                  Spacer(Modifier.height(14.dp))
                  Text("MAÇ TAHMİNİ", color = GoldLight,
                      fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                  Text("Tahmini skor: ${prediction.predictedScore}",
                      color = GoldLight, fontSize = 27.sp,
                      fontWeight = FontWeight.ExtraBold)
                  Text("Beklenen gol: %.2f - %.2f".format(
                      java.util.Locale.US,
                      prediction.homeExpectedGoals,
                      prediction.awayExpectedGoals
                  ), color = TextMuted, fontSize = 12.sp)
                  Spacer(Modifier.height(8.dp))
                  Text("Maç sonucu olasılıkları", color = Gold,
                      fontWeight = FontWeight.Bold)
                  Text("MS 1: %.1f%%   X: %.1f%%   MS 2: %.1f%%".format(
                      java.util.Locale.US,
                      prediction.home ?: 0.0,
                      prediction.draw ?: 0.0,
                      prediction.away ?: 0.0
                  ), color = TextMain, fontSize = 12.sp)
                  Text("2.5 Alt: %.1f%%   2.5 Üst: %.1f%%".format(
                      java.util.Locale.US,
                      prediction.under25 ?: 0.0,
                      prediction.over25 ?: 0.0
                  ), color = TextMain, fontSize = 12.sp)
                  Text("KG Var: %.1f%%   KG Yok: %.1f%%".format(
                      java.util.Locale.US,
                      prediction.bttsYes ?: 0.0,
                      prediction.bttsNo ?: 0.0
                  ), color = TextMain, fontSize = 12.sp)
                  Text("En yüksek model olasılığı: ${prediction.bestPick}",
                      color = GoldLight, fontWeight = FontWeight.Bold)
                  Text("Tahmin yalnızca takım formu ve son maç istatistiklerinden hesaplanır; kesin sonuç garantisi değildir.",
                      color = TextMuted, fontSize = 10.sp)
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
    val hasModelPick = picks.any { it.modelProbability != null }
    val totalOdds = picks.filter { it.modelProbability == null }.fold(1.0) { acc, p -> acc * p.odds }
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
                            Text(
                                if (p.modelProbability != null)
                                    "${p.market}  •  Model: %.1f%%".format(java.util.Locale.US, p.modelProbability)
                                else "${p.market}  •  ${p.odds} oran",
                                color = Gold, fontSize = 11.sp
                            )
                        }
                        TextButton(onClick = { onRemove(p) }) { Text("Sil", color = Red) }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Kupon oranı", color = TextMuted, fontSize = 11.sp)
        Text(if (picks.isEmpty() || hasModelPick) "Gerçek oran mevcut değil" else String.format("%.2f", totalOdds), color = GoldLight, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = stakeText, onValueChange = onStake, label = { Text("Misli / tutar (₺)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Gold, focusedLabelColor = Gold, cursorColor = Gold))
        Text("Olası brüt ödeme: ${if (stake > 0 && picks.isNotEmpty() && !hasModelPick) String.format("%.2f", stake * totalOdds) else "—"} ₺",
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
                        if (item.predictedScore.isNotBlank()) {
                            Text("Tahmini skor: ${item.predictedScore}", color = TextMuted, fontSize = 11.sp)
                        }
                        item.modelProbability?.let {
                            Text("Model olasılığı: %.1f%%".format(java.util.Locale.US, it), color = TextMuted, fontSize = 11.sp)
                        }
                    Spacer(Modifier.height(8.dp))
                    Text(
                    item.status,
                    color = when (item.status) {
                        "Kazandı" -> Color(0xFF238636)
                        "Kaybetti" -> Color(0xFFDA3633)
                        else -> Color(0xFF9CA3AF)
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
        Text("Tamamlanan maçların sonuçları otomatik değerlendirilir.", color = TextMuted, fontSize = 11.sp)
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
    picks.forEach { p -> arr.put(JSONObject().put("id", p.matchId).put("fixture", p.fixture).put("market", p.market).put("odds", p.odds).put("modelProbability", p.modelProbability)) }
    prefs.edit().putString("picks", arr.toString()).apply()
}
private fun loadPicks(prefs: android.content.SharedPreferences): List<Pick> = try {
    val arr = JSONArray(prefs.getString("picks", "[]"))
    (0 until arr.length()).map { i -> arr.getJSONObject(i).let { Pick(it.getString("id"), it.getString("fixture"), it.getString("market"), it.getDouble("odds"), if (it.has("modelProbability") && !it.isNull("modelProbability")) it.getDouble("modelProbability") else null) } }
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
    items.forEach { t -> arr.put(JSONObject().put("id", t.matchId).put("fixture", t.fixture).put("selection", t.selection).put("status", t.status).put("predictedScore", t.predictedScore).put("modelProbability", t.modelProbability)) }
    prefs.edit().putString("tracked", arr.toString()).apply()
}
private fun loadTracked(prefs: android.content.SharedPreferences): List<Tracked> = try {
    val arr = JSONArray(prefs.getString("tracked", "[]"))
    (0 until arr.length()).map { i -> arr.getJSONObject(i).let { Tracked(it.optString("id", ""), it.getString("fixture"), it.getString("selection"), it.getString("status"), it.optString("predictedScore", ""), if (it.has("modelProbability") && !it.isNull("modelProbability")) it.getDouble("modelProbability") else null) } }
} catch (_: Exception) { emptyList() }



data class AnalysisRecord(
    val id: String,
    val matchId: String,
    val fixture: String,
    val predictedScore: String,
    val actualScore: String = "",
    val ms1: Double,
    val msx: Double,
    val ms2: Double,
    val over25: Double,
    val under25: Double,
    val bttsYes: Double,
    val bttsNo: Double,
    val homeFormSummary: String = "",
    val awayFormSummary: String = "",
    val homeStatsSummary: String = "",
    val awayStatsSummary: String = ""
)

private fun saveAnalysisRecords(
    prefs: android.content.SharedPreferences,
    records: List<AnalysisRecord>
) {
    val array = JSONArray()
    records.forEach { r ->
        array.put(JSONObject().apply {
            put("id", r.id)
            put("matchId", r.matchId)
            put("fixture", r.fixture)
            put("predictedScore", r.predictedScore)
            put("actualScore", r.actualScore)
            put("ms1", r.ms1)
            put("msx", r.msx)
            put("ms2", r.ms2)
            put("over25", r.over25)
            put("under25", r.under25)
            put("bttsYes", r.bttsYes)
            put("bttsNo", r.bttsNo)
            put("homeFormSummary", r.homeFormSummary)
            put("awayFormSummary", r.awayFormSummary)
            put("homeStatsSummary", r.homeStatsSummary)
            put("awayStatsSummary", r.awayStatsSummary)
        })
    }
    prefs.edit().putString("analysis_records", array.toString()).apply()
}

private fun loadAnalysisRecords(
    prefs: android.content.SharedPreferences
): List<AnalysisRecord> {
    return try {
        val array = JSONArray(prefs.getString("analysis_records", "[]"))
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            AnalysisRecord(
                id = o.optString("id"),
                matchId = o.optString("matchId"),
                fixture = o.optString("fixture"),
                predictedScore = o.optString("predictedScore"),
                actualScore = o.optString("actualScore"),
                ms1 = o.optDouble("ms1"),
                msx = o.optDouble("msx"),
                ms2 = o.optDouble("ms2"),
                over25 = o.optDouble("over25"),
                under25 = o.optDouble("under25"),
                bttsYes = o.optDouble("bttsYes"),
                bttsNo = o.optDouble("bttsNo"),
                homeFormSummary = o.optString("homeFormSummary", ""),
                awayFormSummary = o.optString("awayFormSummary", ""),
                homeStatsSummary = o.optString("homeStatsSummary", ""),
                awayStatsSummary = o.optString("awayStatsSummary", "")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun <T> updateAnalysisResults(
    records: List<AnalysisRecord>,
    matches: List<T>,
    getId: (T) -> String,
    getStatus: (T) -> String,
    getScore: (T) -> String
): List<AnalysisRecord> {
    return records.map { record ->
        if (record.actualScore.isNotBlank()) return@map record

        val match = matches.firstOrNull { getId(it) == record.matchId }
            ?: return@map record

        val finished = getStatus(match).uppercase() in
            listOf("FT", "AET", "PEN", "FINISHED", "ENDED")

        val numbers = Regex("\\d+").findAll(getScore(match))
            .map { it.value }.toList()

        if (finished && numbers.size >= 2) {
            record.copy(actualScore = "${numbers[0]}-${numbers[1]}")
        } else {
            record
        }
    }
}
private fun analysisMarketWon(market: String, score: String): Boolean? {
    val n = Regex("\\d+").findAll(score).mapNotNull { it.value.toIntOrNull() }.toList()
    if (n.size < 2) return null

    val h = n[0]
    val a = n[1]
    return when (market) {
        "MS 1" -> h > a
        "MS X" -> h == a
        "MS 2" -> h < a
        "2.5 Üst" -> h + a >= 3
        "2.5 Alt" -> h + a <= 2
        "KG Var" -> h > 0 && a > 0
        "KG Yok" -> h == 0 || a == 0
        else -> null
    }
}

private fun exactAnalysisScore(record: AnalysisRecord): Boolean? {
    if (record.actualScore.isBlank()) return null
    val predicted = Regex("\\d+").findAll(record.predictedScore).map { it.value }.toList()
    val actual = Regex("\\d+").findAll(record.actualScore).map { it.value }.toList()
    if (predicted.size < 2 || actual.size < 2) return false
    return predicted[0] == actual[0] && predicted[1] == actual[1]
}


@Composable
private fun AnalysisRecordsScreen(records: List<AnalysisRecord>) {
    val filters = remember {
        androidx.compose.runtime.mutableStateListOf("", "", "", "", "", "", "", "", "", "")
    }
    val headers = listOf(
        "Maç", "Tahmin Skor", "Gerçek Skor", "MS1 %", "MSX %",
        "MS2 %", "2.5 Üst %", "2.5 Alt %", "KG Var %", "KG Yok %"
    )
    val widths = listOf(190.dp, 105.dp, 105.dp, 75.dp, 75.dp, 75.dp, 90.dp, 90.dp, 85.dp, 85.dp)

    fun cells(r: AnalysisRecord) = listOf(
        r.fixture, r.predictedScore, r.actualScore,
        "%.1f".format(java.util.Locale.US, r.ms1),
        "%.1f".format(java.util.Locale.US, r.msx),
        "%.1f".format(java.util.Locale.US, r.ms2),
        "%.1f".format(java.util.Locale.US, r.over25),
        "%.1f".format(java.util.Locale.US, r.under25),
        "%.1f".format(java.util.Locale.US, r.bttsYes),
        "%.1f".format(java.util.Locale.US, r.bttsNo)
    )

    val filtered = records.filter { r ->
        val row = cells(r)
        filters.indices.all { i ->
            filters[i].isBlank() || row[i].contains(filters[i].trim(), true)
        }
    }.reversed()

    Column(Modifier.fillMaxSize().background(Black).padding(8.dp)) {
        Text("MAÇ ANALİZ KAYITLARI", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text("${filtered.size} / ${records.size} kayıt", color = TextMuted, fontSize = 12.sp)

        if (records.isEmpty()) {
            Text(
                "Henüz kayıt yok. Maçlar sekmesinde ANALİZ ET'e basınca kayıt oluşacak.",
                color = TextMuted,
                modifier = Modifier.padding(12.dp)
            )
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                Column(Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())) {
                    Row {
                        headers.forEachIndexed { i, title ->
                            Box(Modifier.width(widths[i]).background(Surface2).padding(8.dp)) {
                                Text(title, color = GoldLight, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Row {
                        filters.forEachIndexed { i, value ->
                            OutlinedTextField(
                                value = value,
                                onValueChange = { filters[i] = it },
                                modifier = Modifier.width(widths[i]).padding(2.dp),
                                singleLine = true,
                                placeholder = { Text("Ara...", fontSize = 10.sp) },
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, color = TextMain)
                            )
                        }
                    }
                    filtered.forEach { r ->
                        val row = cells(r)
                        Row(Modifier.padding(vertical = 2.dp).background(SurfaceDark)) {
                            row.forEachIndexed { i, value ->
                                val color = when {
                                    i == 0 -> TextMain
                                    r.actualScore.isBlank() -> TextMuted
                                    i == 1 -> if (exactAnalysisScore(r) == true) Green else Red
                                    i == 2 -> GoldLight
                                    else -> {
                                        val market = listOf(
                                            "", "", "", "MS 1", "MS X", "MS 2",
                                            "2.5 Üst", "2.5 Alt", "KG Var", "KG Yok"
                                        )[i]
                                        if (analysisMarketWon(market, r.actualScore) == true) Green else Red
                                    }
                                }
                                Box(Modifier.width(widths[i]).padding(8.dp)) {
                                    Text(value.ifBlank { "—" }, color = color, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
