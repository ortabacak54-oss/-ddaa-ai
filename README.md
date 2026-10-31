# İddaa AI — Android MVP (siyah & gold)

## Bu sürümde neler var?
- Modern siyah-altın arayüz ve dört sekme: Maçlar, Kupon, Takip, İstatistik
- Lig filtresi ve maç kartları
- 1X2, 2.5 üst ve KG Var için örnek olasılık gösterimi
- Seçimleri kupona ekleme/silme, toplam oran ve girilen tutara göre olası brüt ödeme hesabı
- Kupon seçimlerini takip listesine aktarma
- Takip kayıtlarını Kazandı/Kaybetti/Bekliyor/İptal olarak elle sonuçlandırma
- Kupon ve takip verilerini telefonda SharedPreferences ile saklama
- GitHub Actions ile bulutta debug APK derleme workflow'u

## Önemli sınırlar
Bu proje şu anda **demo fikstürler ve eğitilmemiş, açıklanabilir bir örnek sezgisel** kullanır. Canlı maç verisi, gerçek bahis oranı veya eğitilmiş bir yapay zekâ modeli içermez. Ekrandaki oranlar/olasılıklar örnektir; gerçek tahmin veya kazanç garantisi değildir. Uygulama üretime alınmadan önce lisanslı/güvenilir veri sağlayıcı, sunucu tarafı API anahtarı yönetimi, model doğrulaması, veri gizliliği ve yasal uygunluk çalışmaları gerekir. API anahtarını Android uygulamasına gömmeyin.

## Telefonda APK oluşturma (GitHub Actions)
1. GitHub'da yeni bir **public veya private repository** oluştur.
2. Bu ZIP'i açıp içindeki tüm dosya ve klasörleri repo köküne yükle. `.github/workflows/android.yml` gizli klasörünü de eklediğinden emin ol.
3. GitHub repo sayfasında **Actions** sekmesine gir. İlk kullanımda workflow çalıştırmayı onayla.
4. **Android APK** workflow'unu seçip **Run workflow** düğmesine bas.
5. İşlem tamamlanınca çalışmanın sayfasındaki **Artifacts** bölümünden `iddaa-ai-debug-apk` dosyasını indir.
6. ZIP'i telefonda aç, `app-debug.apk` dosyasına dokun ve Android'in istediği bilinmeyen uygulama yükleme iznini yalnızca GitHub'dan indirdiğin kendi build'in için ver.

## Yerel derleme
JDK 17 ve Gradle 8.9 ile proje kökünde:
`gradle assembleDebug`
APK: `app/build/outputs/apk/debug/app-debug.apk`
