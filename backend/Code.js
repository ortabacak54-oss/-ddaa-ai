function json(data) {
  return ContentService
    .createTextOutput(JSON.stringify(data))
    .setMimeType(ContentService.MimeType.JSON);
}

function doGet(e) {
  var action = (e.parameter.action || "fixtures").toLowerCase();
  var date = e.parameter.date ||
    Utilities.formatDate(new Date(), "Europe/Istanbul", "yyyy-MM-dd");

  if (action === "testodds") {
    try {
      var testUrl = "https://www.mackolik.com/mac/amed-sk-vs-eyupspor/iddaa/cu0297mak4x94q9kas1d1uk2c";
      var testResponse = UrlFetchApp.fetch(testUrl, {
        muteHttpExceptions: true,
        headers: {"User-Agent": "Mozilla/5.0"}
      });
      var testHtml = testResponse.getContentText();
      return json({
        durum: "test",
        http: testResponse.getResponseCode(),
        htmlUzunluk: testHtml.length,
        oranVar: testHtml.indexOf("widget-iddaa-markets__value") !== -1,
        baslangic: testHtml.substring(0, 300)
      });
    } catch (err) {
      return json({durum: "hata", mesaj: String(err)});
    }
  }

  if (action === "odds") {
    try {
      var fixtureId = e.parameter.fixture || "";
      if (!fixtureId) return json({durum:"hata", mesaj:"fixture parametresi eksik"});

      var fixtureUrl = "https://www.mackolik.com/perform/p0/ajax/components/competition/livescores/json?sports%5B%5D=Soccer&matchDate=" + date;
      var fixtureResponse = UrlFetchApp.fetch(fixtureUrl, {
        headers: {"User-Agent":"Mozilla/5.0"},
        muteHttpExceptions:true
      });
      var fixtureData = JSON.parse(fixtureResponse.getContentText());
      var fixtureSource = fixtureData.data && fixtureData.data.matches ? fixtureData.data.matches : {};
      var found = null;

      Object.keys(fixtureSource).some(function(id) {
        var m = fixtureSource[id];
        if (String(m.id || id) === String(fixtureId)) {
          found = m;
          return true;
        }
        return false;
      });

      if (!found) return json({durum:"hata", mesaj:"Maç bulunamadı. Tarih parametresini kontrol et."});
      if (!found.matchSlug) return json({durum:"hata", mesaj:"Maç sayfası adresi bulunamadı."});

      var detailUrl = "https://www.mackolik.com/mac/" + found.matchSlug + "/iddaa/" + found.id;
      var page = UrlFetchApp.fetch(detailUrl, {
        headers: {"User-Agent":"Mozilla/5.0"},
        muteHttpExceptions:true
      });

      if (page.getResponseCode() !== 200) {
        return json({durum:"hata", http:page.getResponseCode(), mesaj:"Oran sayfası açılamadı"});
      }

      var html = page.getContentText();
      var blocks = html.match(/<li class="widget-iddaa-markets__market-item"[\s\S]*?<\/li>\s*<\/ul>/g) || [];
      var markets = [];

      function cleanOddsText(v) {
        return v.replace(/<[^>]*>/g, " ")
          .replace(/&nbsp;/g, " ")
          .replace(/&amp;/g, "&")
          .replace(/&#39;/g, "'")
          .replace(/&quot;/g, '"')
          .replace(/\s+/g, " ").trim();
      }

      blocks.forEach(function(block) {
        var titleMatch = block.match(/widget-iddaa-markets__header-text[^>]*>([\s\S]*?)<\/span>/);
        if (!titleMatch) return;

        var title = cleanOddsText(titleMatch[1]);
        var listMatch = block.match(/<ul class="widget-iddaa-markets__option-list[^"]*"[^>]*>([\s\S]*?)<\/ul>/);
        if (!listMatch) return;

        var options = [];
        var optionRegex = /<li class="widget-iddaa-markets__option"[^>]*>([\s\S]*?)<\/li>/g;
        var opt;
        while ((opt = optionRegex.exec(listMatch[1])) !== null) {
          var labelMatch = opt[1].match(/widget-iddaa-markets__label[^>]*>([\s\S]*?)<\/span>/);
          var valueMatch = opt[1].match(/widget-iddaa-markets__value[^>]*>([\s\S]*?)<\/span>/);
          if (labelMatch && valueMatch) {
            options.push({
              secim: cleanOddsText(labelMatch[1]),
              oran: cleanOddsText(valueMatch[1])
            });
          }
        }

        if (options.length) markets.push({pazar:title, secenekler:options});
      });

      var main = markets.filter(function(m) { return m.pazar === "Maç Sonucu"; })[0];
      var normalized = {};
      if (main) {
        main.secenekler.forEach(function(o) {
          if (o.secim === "1") normalized.ms1 = o.oran;
          if (o.secim === "X") normalized.msx = o.oran;
          if (o.secim === "2") normalized.ms2 = o.oran;
        });
      }

      return json({
        durum: markets.length ? "aktif" : "oran_yok",
        macId:String(found.id),
        macAdi:found.matchName || "",
        iddaaKodu:found.iddaaCode || "",
        oranlar:normalized,
        pazarSayisi:markets.length,
        pazarlar:markets
      });
    } catch (err) {
      return json({durum:"hata", mesaj:String(err)});
    }
  }

  if (action === "form") {
    try {
      var teamId = String(e.parameter.team || "").trim();
      if (!teamId) return json({durum:"hata", mesaj:"Takım ID eksik"});

      var teamUrl = "https://www.mackolik.com/index.php/takim/x/ma%C3%A7lar/" + encodeURIComponent(teamId);
      var teamResponse = UrlFetchApp.fetch(teamUrl, {
        muteHttpExceptions: true,
        headers: {"User-Agent":"Mozilla/5.0"}
      });

      if (teamResponse.getResponseCode() !== 200) {
        return json({durum:"hata", mesaj:"Mackolik takım sayfası açılamadı"});
      }

      var html = teamResponse.getContentText();
      var rowRegex = /<tr class="p0c-team-matches__row" data-match-type="([^"]+)"[\s\S]*?<\/tr>/g;
      var matchRows = [];
      var rowMatch;

      while ((rowMatch = rowRegex.exec(html)) !== null) {
        var row = rowMatch[0];
        var names = [];
        var nameRegex = /class="p0c-team-matches__team-full-name"[^>]*>([\s\S]*?)<\/[^>]+>/g;
        var nm;
        while ((nm = nameRegex.exec(row)) !== null) {
          names.push(nm[1].replace(/<[^>]*>/g, "").replace(/&amp;/g, "&").trim());
        }

        var scores = [];
        var scoreRegex = /class="p0c-team-matches__score"[^>]*>([\s\S]*?)<\/[^>]+>/g;
        var sm;
        while ((sm = scoreRegex.exec(row)) !== null) {
          scores.push(sm[1].replace(/<[^>]*>/g, "").trim());
        }

        var dateMatch = row.match(/data-start-timestamp="(\d+)"/);
        if (names.length < 2 || scores.length < 2 || !/^\d+$/.test(scores[0]) || !/^\d+$/.test(scores[1])) continue;

        var homeScore = Number(scores[0]);
        var awayScore = Number(scores[1]);
        var isHome = rowMatch[1] === "home";
        var teamScore = isHome ? homeScore : awayScore;
        var opponentScore = isHome ? awayScore : homeScore;
        var result = teamScore > opponentScore ? "G" : teamScore < opponentScore ? "M" : "B";

        matchRows.push({
          id: dateMatch ? dateMatch[1] : String(matchRows.length),
          tarih: dateMatch ? new Date(Number(dateMatch[1]) * 1000).toISOString().slice(0,10) : "",
          lig: "",
          rakip: isHome ? names[1] : names[0],
          saha: isHome ? "İç saha" : "Deplasman",
          atilanGol: teamScore,
          yenilenGol: opponentScore,
          sonuc: result
        });
      }

      matchRows.sort(function(a,b) { return b.tarih.localeCompare(a.tarih); });
      return json({durum:"aktif", takimId:teamId, toplam:matchRows.length, maclar:matchRows});
    } catch (err) {
      return json({durum:"hata", mesaj:String(err)});
    }
  }

  if (action !== "fixtures" && action !== "live") {
    return json({durum: "hata", mesaj: "Bilinmeyen işlem"});
  }


  try {
    var url = "https://www.mackolik.com/perform/p0/ajax/components/competition/livescores/json?sports%5B%5D=Soccer&matchDate=" + date;

    var response = UrlFetchApp.fetch(url, {
      method: "get",
      headers: {
        "User-Agent": "Mozilla/5.0",
        "Accept": "application/json"
      },
      muteHttpExceptions: true
    });

    if (response.getResponseCode() !== 200) {
      return json({
        durum: "api_hatasi",
        http: response.getResponseCode(),
        mesaj: "Maçkolik bağlantısı başarısız"
      });
    }

    var data = JSON.parse(response.getContentText());
    var source = data.data && data.data.matches
      ? data.data.matches : {};

    var matches = Object.keys(source).map(function(id) {
      var x = source[id];
      var score = x.score || {};
      var state = x.state || "";
      var substate = x.substate || "";
      var status = "NS";

      if (state === "live") {
        status = "LIVE";
      } else if (substate === "fullTime" || state === "post") {
        status = "FT";
      }

      return {
        id: String(x.id || id),
        tarih: x.mstUtc
          ? Utilities.formatDate(
              new Date(Number(x.mstUtc)),
              "Europe/Istanbul",
              "yyyy-MM-dd'T'HH:mm:ss"
            )
          : "",
        durum: status,
        dakika: "",
        lig: "",
        ulke: "",
        ev: x.homeTeam ? x.homeTeam.name : "",
        evId: x.homeTeam ? String(x.homeTeam.id || "") : "",
        deplasman: x.awayTeam ? x.awayTeam.name : "",
        deplasmanId: x.awayTeam ? String(x.awayTeam.id || "") : "",
        evGol: score.home,
        deplasmanGol: score.away
      };
    });

    var result = {
      durum: "aktif",
      islem: action,
      tarih: date,
      toplam: matches.length,
      maclar: matches
    };

    var output = JSON.stringify(result);

    return ContentService.createTextOutput(output)
      .setMimeType(ContentService.MimeType.JSON);

  } catch (err) {
    return json({
      durum: "hata",
      mesaj: String(err)
    });
  }
}
