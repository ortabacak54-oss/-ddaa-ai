from pathlib import Path

p = Path("app/src/main/java/com/example/iddaaai/MainActivity.kt")
s = p.read_text()

s = s.replace(
    "import androidx.compose.runtime.*",
    "import androidx.compose.runtime.*\nimport kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.withContext"
)

anchor = '    var notice by remember { mutableStateOf("") }'
state_code = '''
    var liveMatches by remember { mutableStateOf<List<LiveMatch>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var apiError by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            liveMatches = withContext(Dispatchers.IO) { LiveApi.getMatches() }
        } catch (e: Exception) {
            apiError = e.message ?: "Bağlantı hatası"
        } finally {
            loading = false
        }
    }
'''
if 'var liveMatches by remember' not in s:
    if anchor not in s:
        raise SystemExit("HATA: notice satırı bulunamadı")
    s = s.replace(anchor, anchor + "\n" + state_code, 1)

start = s.index('"Maçlar" -> {')
end = s.index('"Kupon" ->', start)

matches_code = '''"Maçlar" -> {
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

                    val shown = liveMatches.filter {
                        filter == "Tümü" ||
                        (filter == "Süper Lig" && it.league.contains("Süper Lig", true)) ||
                        (filter == "Avrupa" && !it.league.contains("Süper Lig", true))
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
                                LiveMatchCard(m)
                            }
                        }
                        item { Disclaimer() }
                    }
                }
                '''

s = s[:start] + matches_code + s[end:]

if 'private fun LiveMatchCard(' not in s:
    marker = 'private fun MatchCard('
    pos = s.index(marker)
    composable_pos = s.rfind('@Composable', 0, pos)

    card_code = '''@Composable
private fun LiveMatchCard(m: LiveMatch) {
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
                Text(m.score, color = GoldLight, fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp)
                Text(m.away, color = TextMain, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(9.dp))
            Text("Maç durumu: ${m.status}", color = TextMuted, fontSize = 10.sp)
            Text("Tahmin ve oran verisi henüz bağlanmadı.",
                color = Gold, fontSize = 10.sp,
                modifier = Modifier.padding(top = 7.dp))
        }
    }
}

'''
    s = s[:composable_pos] + card_code + s[composable_pos:]

p.write_text(s)
print("Düzenleme tamamlandı.")
