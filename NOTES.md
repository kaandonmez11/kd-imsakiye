# KD İmsakiye — Proje Notları (Claude)

> Bu dosya Claude'un proje incelemesi sonucu tuttuğu nottur. Son güncelleme: 2026-07-11 (v2.0 UI redesign tamamlandı)

## v2.0 Yeniden Tasarım (2026-07-11, referans: claude_references/ui/)

Üç ekran (İmsakiye, Vakitler, Ayarlar) HTML referansına göre sıfırdan yazıldı; gündüz/gece çift layout mimarisi kaldırılıp **values/values-night token** sistemine geçildi.

- **Tokenlar**: `values(-night)/colors.xml` (bg/surface/ink/accent/nav_*...), `dimens.xml`. Fontlar: Schibsted Grotesk (UI, variable) + Azeret Mono (rakamlar, tabular). `header_band` light `#D17351` (illüstrasyon zeminiyle eşleşir).
- **Tema sistemi**: `ThemePrefs` (SharedPreferences) — LIGHT / DARK / **AUTO (vakit bazlı: iftara sayarken light, imsaka sayarken dark)**; sistem teması KULLANILMIYOR. Manifest'te `configChanges="uiMode"` → tema değişimi **recreate'siz**: `onConfigurationChanged` eski ekranın bitmap'ini overlay yapıp fade'ler, `setupUi()` yeniden kurar. Tema `attachBaseContext`'te uygulanır (onCreate'te geç kalır — sistem-dark cihazda yanlış tema bug'ı buradaydı).
- **Ekran yapısı**: `main_root` FrameLayout → loading + 3 sayfa include + yüzen navbar (bottom overlay). Sayfa geçişi `selectTab` + `showPanel` fade. Sayfalara navbar yüksekliği kadar bottom padding layout listener ile verilir.
- **Navbar**: pill 26/20/6dp; kayan highlight (manuel ValueAnimator: tab genişlikleri + highlight left/width + etiket genişlik/alpha birlikte lerp edilir — ChangeBounds kullanma, yarıda kesilince bozuluyordu). Gölge elevation DEĞİL: `NavBarContainer` software layer'da `setShadowLayer` ile çizer (elevation ambient gölgesi köşelerde sert kenar bırakıyordu).
- **İmsakiye**: canlı sayaç (İFTARA/İMSAKA KALAN otomatik), `GlowProgressBar` (blur'lu glow), sabit başlıklı GÜN/İMSAK/İFTAR tablosu (satırlar `row_imsakiye_day` inflate, baseline hizalı), fading edge.
- **Vakitler**: Dün (soluk) + Bugün (3×2 grid, geçmiş vakitler .45, sıradaki accent) + kompakt gün kartları; scroll navbar arkasından geçer, üst/alt fading edge.
- **Ayarlar**: şehir seçimi (özel panel: `dialog_city_picker`, seçiliye scroll), "Konumu kullan" (aktifken grileşir "Güncel konum kullanılıyor"; header pinleri custom seçimde soluk), Hatırlatma/Geri sayım **saat+dakika stepper** (dk ±5, toplam dakika DataStore'da; eski saat değeri migrate edilir), Gündüz/Gece/Otomatik segmenti (kayan highlight), bildirim test butonu (gerçek alarm→receiver→FGS zinciri, 2 dk sayaç).
- **Header illüstrasyonları**: `drawable(-night)-nodpi/header_illo.png` — kaynaklardan (iftar/sahur_background) 1024×325 pencereyle kırpıldı; iki temada aynı yükseklik. Kaynak değişirse aynı pencereyle yeniden kırp.
- **Dikkat**: `include android:id` kök id'yi ezer; `TextView.apply{}` içinde `findViewById` TextView'a çözülür (`this@MainActivity.` şart); wrap FrameLayout içine match_parent düz View koyma (ekran boyu büyütür).

## Denetim düzeltmeleri (2026-07-11, tam tarama sonrası)

- `changeCity()`: fetch boş dönerse eski veri + alarmlar KORUNUR (eskiden hepsi siliniyordu); başarıda tema da yeniden değerlendirilir. "Konumu kullan" yolu da ili listeye karşı doğrular (yurt dışı → İstanbul).
- `NotificationScheduler` (object): alarm kurulum/iptal mantığı Activity'den taşındı; `BootReceiver` BOOT_COMPLETED + MY_PACKAGE_REPLACED'te `scheduleFromCache` ile alarmları yeniden kurar (RECEIVE_BOOT_COMPLETED izni eklendi).
- `getInterestDays`: liste sonunda pencere kaydırılmaz, KISALIR — bugün hep index 1 (yıl sonu "Bugün" kayması giderildi). `days[2]` kullanan sayaç dalına guard eklendi (31 Aralık gecesi sayaç "—").
- `NotificationReceiver`: boş ses adı → varsayılan sesli `ramadan_channel_default` (geçersiz URI'li sessiz kanal bug'ı).
- Manifest: `screenOrientation="portrait"` (rotasyonda tam yeniden yükleme oluyordu).
- Vakitler ekranı: bir sonraki vakit anına zamanlanmış otomatik rebind (`uiHandler` + `vakitlerTick`) — "sıradaki" vurgusu ekran açıkken tazelenir.
- Stepper ayarları: değerler anında DataStore'a yazılır, yalnızca alarm kurulumu 800ms debounce.
- Splash arka planı `@color/header_band` (eski pembe kaldırıldı).
- Bilinen küçük kalıntılar: verinin İLK gününde (1 Ocak) "Dün" satırı bugünü gösterir (kozmetik, yılda 1 gün); kullanıcı "Durmaya zorla" yaparsa alarmlar düşer (platform davranışı).

## İkinci denetim düzeltmeleri (2026-07-11)

- `getAnnualDataIgnoringVersion()`: BootReceiver'ın güncelleme (MY_PACKAGE_REPLACED) yolu, versionCode artışının cache'i geçersiz saydırması yüzünden boşa düşüyordu — scheduler artık sürüm kontrolsüz okur (parse hatasına karşı try/catch'li). `getSavedDataCity` kaldırıldı.
- `onPause`: sayaç + vakitler tick durdurulur (arka planda boşa iş yok) ve bekleyen alarm debounce'u ANINDA flush edilir; `onResume`: tema yeniden değerlendirilir + aktif tab yeniden bind edilir (arka planda vakit/tema geçişleri resume'da yakalanır).
- Test bildirimi butonu kaldırıldı; splash/launcher ikon zinciri yeni palete taşındı (`ic_launcher_background`=#D17351, splash=@color/header_band).
- `NotificationScheduler.planLiveTask` private; ölü importlar temizlendi.

## Proje Özeti

- **Ne**: Türkiye için Ramazan imsakiye / namaz vakitleri Android uygulaması (`com.kdgames.imsakiye`).
- **UI**: Tamamen klasik View sistemi (XML layout + `findViewById`). Compose bağımlılıkları var ama **hiç Compose kullanılmıyor**.
- **Mimari**: Tek Activity (`MainActivity`, ~1400 satır), her şey içinde. ViewModel / Fragment / Navigation yok.
- **Veri**: `https://ezanvakti.imsakiyem.com/api/prayer-times/{cityId}/yearly` → yıllık veri Gson ile DataStore'a JSON string olarak cache'leniyor.
- **Konum**: FusedLocation + Geocoder ile il tespiti (`adminArea`), olmazsa fallback İstanbul.
- **Bildirimler**: AlarmManager exact alarm → `NotificationReceiver` (normal bildirim) ve `LiveCountdownReceiver` → `LiveNotificationService` (foreground canlı geri sayım). Sadece Hicri 9. ay (Ramazan) için kuruluyor.
- **Ekranlar**: Gece/Gündüz modu × (ana imsakiye, namaz vakitleri, ayarlar) = 6 panel, hepsi tek layout'ta (`main_root`) gizle/göster ile.

## Dosya Haritası

| Dosya | Rol |
|---|---|
| `MainActivity.kt` | Her şey: UI doldurma, alarm kurma, API çağrısı, konum, ayarlar |
| `DataManager.kt` | DataStore wrapper (tamamı suspend) |
| `PrayTimeUtils.kt` | Saf yardımcılar: tarih/saat parse, gün penceresi, Türkçe ad çevirileri (unit testli) |
| `CityManager.kt` | `res/raw/city_request_ids.json` → şehir adı → API id |
| `services/TurkishPrayerApiService.kt` | Retrofit arayüzü |
| `services/NotificationReceiver.kt` | Normal bildirim gösterir |
| `services/LiveCountdownReceiver.kt` | FGS başlatır |
| `services/LiveNotificationService.kt` | Canlı geri sayım bildirimi (API 35 promoted ongoing destekli) |
| `data/TurkishResponseDataStruct.kt` | DTO'lar |

## Yapılan İyileştirmeler (2026-07-11'de uygulandı, hepsi derlendi + testler geçti)

1. ✅ **Yıl kontrolü bugu**: `Calendar.YEAR` sabiti yerine veri yılı `LocalDate.now().year` ile karşılaştırılıyor (`initializeApp`); refetch başarısızsa eski veri korunuyor.
2. ✅ **`getInterestDays`**: `PrayTimeUtils.getInterestDays`'e taşındı; tarih eşleştirmeli, sınırlarda clamp'li (1 Ocak / 31 Aralık crash'i giderildi). Unit testli.
3. ✅ **Offline crash**: boş veri → toast + loading ekranına dokununca retry; `checkDataAndOpenPanel` `days.size < 3` guard'ı.
4. ✅ **Exact alarm izni**: `scheduleRamadanNotifications` başında bir kez isteniyor (`exactAlarmSettingsShown` flag); her iki plan fonksiyonu da `setAndAllowWhileIdle` inexact fallback kullanıyor.
5. ✅ **View id copy-paste hataları**: tüm konum ikonu tint'leri `setLocationIconTint(viewId, active, inactive)` helper'ından geçiyor; gündüz ayarlarında `useLocation = true` set edilmeme bugu da ortak `loadSettings(config)` ile çözüldü.
6. ✅ **İzin yarışı**: `registerForActivityResult(RequestPermission)` — init, konum izni sonucundan SONRA çalışıyor. Bildirim izni de ActivityResult API'sinde.
7. ✅ **DataManager**: tüm `runBlocking` kaldırıldı, hepsi `suspend`. Alarm kodları alarm başına değil tek seferde yazılıyor (`saveAlarmCodes(Collection<Int>)`). Ölü `KEY_DATA_YEAR` silindi.
8. ✅ **Retrofit**: `apiService` lazy singleton.
9. ✅ **Veri odaklı binding**: `ImsakiyeRowIds`/`PrayerRowIds` id tabloları + `bindImsakiyeRows`/`bindPrayerRows`; gece/gündüz ayarları tek `loadSettings(SettingsConfig)`; sayı inputları `setupNumberInput`. MainActivity ~1410 → ~750 satır.
10. ✅ **Şehir değişimi**: ortak `loadCityData(city, forceRefresh)` + `applyCityChange()` — şehir değişince **alarmlar da yeniden kuruluyor** (önceden kurulmuyordu!).
11. ✅ **`cities` listesi**: hard-coded liste silindi; `CityManager.getCityNames()` JSON'dan okuyup Türkçe `Collator` ile sıralıyor.
12. ✅ Küçükler: `cachedDataProp`/`city` artık lateinit değil; `SimpleDateFormat`/`Calendar` tamamen `java.time`'a çevrildi; iç içe launch'lar kaldırıldı; `Locale(...)` deprecated ctor'ları `forLanguageTag` oldu; Compose/databinding-compiler/work-runtime bağımlılıkları ve `ui/theme/*.kt` silindi; tüm bağımlılıklar version catalog'da (retrofit 2.11, coroutines 1.9, gson 2.11); release'te `isMinifyEnabled = true` + Gson/Retrofit proguard kuralları; `applicationId` → `com.kdgames.imsakiye`; `PrayTimeUtilsTest` eklendi (10 test).

## Dikkat / Kalanlar

- ⚠️ **`applicationId` değişti** (`com.kdgames.uxtest` → `com.kdgames.imsakiye`): uygulama Play'de yayındaysa GERİ ALINMALI — Play aynı uygulamanın güncellemesi olarak kabul etmez. Cihazda da eski paket ayrı uygulama olarak kalır.
- ⚠️ Release hâlâ **debug keystore** ile imzalı (build.gradle.kts'de TODO). Yayın öncesi gerçek keystore şart.
- ⚠️ R8 açıldı; release APK'sı derleniyor ama cihazda smoke test yapılmadı — özellikle Gson deserializasyonu ve bildirimler release build'de bir kez elle doğrulanmalı.
- `Geocoder.getFromLocation` deprecated uyarısı bilinçli bırakıldı (senkron API hâlâ çalışıyor; async listener API'ye geçiş ayrı iş).
- İftar 1 saat kala bildirimi `sound = ""` ile kuruluyor → `ramadan_channel_` kanalında geçersiz ses URI'si (davranış eskiyle aynı, dokunulmadı). İstenirse boş ses → varsayılan bildirim sesi yapılabilir.

## Eski Bulgular (referans)

Yukarıdaki 1–7 maddeleri düzeltilmeden önceki bug'lardı; ayrıntılı analiz git geçmişindeki NOTES.md ilk sürümünde.

## API / Domain Notları

- API cevabı: `{ success, code, message, data: [ { date: ISO_DATE_TIME, hijri_date: {day,month,year}, times: {imsak,gunes,ogle,ikindi,aksam,yatsi} } ] }`
- Alarm request code şeması: sahur canlı = `gün*100+ay`, iftar canlı = `gün*10000+ay`, normal bildirimler = `"SAHUR_1H_g_a".hashCode()` vb. Kurulan kodlar DataStore'da CSV olarak tutulup iptalde kullanılıyor.
- Bildirim kanalları ses başına ayrı: `ramadan_channel_<ses>`; sesler `res/raw` (faded_davul, ezan, ezan_with_top).
