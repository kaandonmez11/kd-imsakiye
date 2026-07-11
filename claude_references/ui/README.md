# Handoff: KD İmsakiye — Light + Dark Tema Yeniden Tasarımı

## Overview
3 ekranlı native Android imsakiye uygulamasının rafine yeniden tasarımı: **İmsakiye** (geri sayım + gün listesi), **Namaz Vakitleri** (gün gün vakit kartları), **Ayarlar**. Light ve dark tema birebir aynı düzeni paylaşır; yalnızca renk paleti değişir.

## About the Design Files
Bu paketteki `Imsakiye Redesign.dc.html` **HTML ile hazırlanmış tasarım referansıdır** — kopyalanacak üretim kodu değildir. Görev: bu tasarımı mevcut Android kod tabanının kendi yapısında (View/XML veya Jetpack Compose, hangisi kullanılıyorsa) yeniden üretmek. HTML dosyasında **turn 3 = final light tema (id 3a/3b/3c)**, **turn 4 = final dark tema (id 4a/4b/4c)**. Turn 1 ve 2 eski iterasyonlardır, YOK SAYIN.

## Fidelity
**High-fidelity.** Renkler, tipografi, boşluklar ve köşe yarıçapları finaldir; piksel hassasiyetinde uygulanmalı. Referans genişlik 412dp (px değerlerini 1:1 dp alın).

## Design Tokens

### Renkler — Light (`values/colors.xml`) / Dark (`values-night/colors.xml`)
| Token | Light | Dark |
|---|---|---|
| bg (ekran) | #F7F4EF | #16202E |
| surface (kart) | #FFFFFF | #1D2938 |
| surfaceCell (vakit hücresi) | #FBF9F4 | #223041 |
| border | #EBE5D9 | #283548 |
| borderStrong | #E3DDD1 | #2A3648 |
| divider | #F0EBE1 | #263243 |
| progressTrack | #E7E1D5 | #2A3648 |
| ink (ana metin) | #22303F | #E9EDF3 |
| inkSecondary | #3B4756 | #C3CBD6 |
| muted | #68727F | #93A0AF |
| mutedLabel | #98A1AE | #7C8797 |
| mutedFaint | #A9B1BC | #6B7686 |
| accent | #C25A36 | #E8536B |
| accentContainer (vurgu zemini) | #FAEDE4 | #3A2230 |
| accentBorder (Bugün kart çerçevesi) | #E8B08A | #8A4257 |
| onAccent (badge metni) | #FFF7F2 | #FFF2F4 |
| headerBand (illüstrasyon zemini) | #F0AE75 | #233B5D |
| navBar (dış pill) | #FFFFFF | #28374E |
| navBarActiveTab | #F0EBE1 | #0F1826 |
| navBarActiveContent | #22303F | #F2F5F9 |
| navBarInactive (ikon) | #22303F | rgba(233,237,243,.5) |
| navBar gölge | rgba(34,48,63,.28) | rgba(0,0,0,.45) |

NOT: HTML dosyası nihai kaynaktır; kullanıcı editörde ufak renk düzeltmeleri yapmış olabilir — uygulamadan önce HTML'deki güncel değerleri doğrulayın.

### Tipografi
- **Genel UI:** Quicksand (Google Fonts). Alternatif/fallback: Schibsted Grotesk. Ağırlıklar 400–800.
- **Rakamlar/saatler:** Space Mono (tüm saat değerleri, geri sayım, tarih rakam kolonu). Alternatif: Azeret Mono. `tabular-nums` şart (geri sayım zıplamasın).
- Ölçek: geri sayım 52sp/300; ekran başlığı 20–22sp/800; kart başlığı 15.5–16sp/700; liste satırı 15sp/500-700; saat (bugün kartı) 17sp/600; saat (liste) 13-15sp; etiketler 10–11sp/600-700 (letter-spacing .06–.2em büyük harfli etiketlerde); alt bilgi 11–12.5sp.

### Boşluk & Radius
- Ekran yatay padding: 20dp (kartlar), 26dp (metin blokları)
- Kart radius: 20–22dp; vakit hücresi 12dp; navbar dış 26dp, iç aktif tab 20dp (navbar padding 6dp — köşeler konsantrik: 26 = 20 + 6)
- Stepper/chip/badge: tam pill (100dp)
- Kart iç padding: başlık 14-16dp/20dp, grid 0 14dp 16dp, hücre 10dp dikey

## Screens

### 1. İmsakiye (3a light / 4a dark)
Yukarıdan aşağı:
1. **Header bandı** (headerBand zemin): status bar; ortada pin ikonu + "İzmir" (16sp/700); altında tam genişlik illüstrasyon PNG (`header-illo.png` light / `header-illo-dark.png` dark — placeholder, kullanıcı orijinalleriyle değiştirecek).
2. **Tarih satırı**: sol "7 Temmuz, Salı" (17sp/700) + altında hicri "22 Muharrem 1448" (12.5sp, mutedLabel); sağda "Diyanet takvimi" (12.5sp/600 mutedLabel).
3. **Geri sayım bloğu**: "İFTARA KALAN" etiketi (11sp/700, .2em, mutedLabel) → canlı sayaç `HH:MM:SS` (52sp/300 mono, tabular) → 4dp progress bar (dolu kısım accent + **statik glow**: `box-shadow 0 0 10px 1.5px accent@%55-65` — Android'de hafif blur'lu accent drawable veya elevation+tint) → altında iki uç: "İmsak 03:59" / "İftar 20:45" (iftar saati accent/600).
4. **Gün tablosu kartı** (surface, 22dp radius): sabit başlık satırı `[boş 40dp] TEMMUZ | İMSAK | İFTAR` (11sp/600 mutedFaint, alt çizgi divider) + **scroll eden liste** (scrollbar GİZLİ). Satır grid: 40dp | 1fr | 76dp | 76dp, padding 12dp/22dp. "Dün" satırı opacity .42; "Bugün" satırı accentContainer zemin, gün adı ink/700, saatler accent/600; diğer satırlar inkSecondary.
5. **Bottom navbar**: dış pill navBar zemin, radius 26dp, padding 6dp, drop shadow. 3 sekme (İmsakiye/Vakitler/Ayarlar — outline ikonlar: hilal, saat, slider). Aktif sekme: navBarActiveTab zemin, 20dp radius, ikon+etiket (12.5sp/700) yan yana; pasifler yalnız ikon, navBarInactive renk.

### 2. Namaz Vakitleri (3b/4b)
Header bandı + başlık satırı ("Namaz Vakitleri" 20sp/800, sağda "Temmuz 2026") aynı desen. Altı **scroll eden kart listesi** (scrollbar gizli, kartlar `flex-shrink:0` eşdeğeri — sabit yükseklik, ezilmesin):
- **Dün kartı**: normal kart düzeni, tüm 6 vakit görünür, komple opacity .5, sağ üstte "DÜN" (11sp/700 mutedLabel).
- **Bugün kartı**: accentBorder 1.5dp çerçeve + hafif renkli gölge; başlıkta sağda accent zeminli "BUGÜN" pill badge (onAccent metin). Vakitler **3×2 grid** (8dp gap): her hücre surfaceCell zemin 12dp radius, etiket 11sp + saat 17sp/600 mono. **Geçmiş vakitler** (İmsak/Güneş/Öğle) hücre opacity .45 ve saat weight 400. **Sıradaki vakit** (İkindi): accentContainer zemin + accentBorder outline, etiket ve saat accent renkli — ekstra "Sıradaki" yazısı YOK.
- **Yarın + sonraki 4 gün kartları**: kompakt tek satır 6'lı grid (etiket 10sp üstte, saat 13sp altta), hücre padding 6dp, başlıkta tarih + hicri.
Navbar: aktif sekme "Vakitler".

### 3. Ayarlar (3c/4c)
Header bandı + "Ayarlar" başlığı. 3 gruplu kart (grup etiketi 11sp/700 .16em mutedFaint):
- **KONUM**: kart içinde "Şehir → İzmir >" satırı; divider; altında accent renkli "Konumu kullan" aksiyonu (hedef ikonu + 14.5sp/700).
- **BİLDİRİMLER**: iki satır — "Hatırlatma / İftar ve imsaktan önce" ve "Geri sayım / Bildirimde canlı sayaç". Her satırda **iki pill stepper yan yana**: `− 1 sa +` ve `− 30 dk +` (saat VE dakika ayrı ayarlanır), yanında "önce" etiketi. Stepper: 1dp borderStrong çerçeve, − muted / + accent, değer mono 12.5-13sp/600, buton dokunma alanı ≥32dp (üretimde 44dp'ye çıkarın).
- **GÖRÜNÜM**: segmented control (track divider zemin, pill): Gündüz | Gece | Otomatik. Seçili segment: light temada beyaz zemin + gölge; dark temada #2E3D52 zemin + açık metin. Dark ekranda "Gece" seçili gösterilir.
Altta ortada "v1.1.1 · Kaan Dönmez" (11sp, en soluk). Navbar: aktif "Ayarlar".

## Interactions & Behavior
- Geri sayım saniyede bir günceller; iftar geçtiyse hedef ertesi imsak, etiket "İMSAKA KALAN" olur.
- Listeler native scroll, **scrollbar görünmez** (swipe ile).
- Tema: Gündüz/Gece/Otomatik segmenti → AppCompatDelegate night mode (MODE_NIGHT_NO/YES/FOLLOW_SYSTEM). Tüm renkler values/values-night ile otomatik çözülür.
- Navbar sekme geçişi: aktif tab arka planı yumuşak geçebilir (150–200ms), zorunlu değil.
- Progress bar: gün içindeki konum = (now − imsak) / (iftar − imsak).

## Assets
- `assets/header-illo.png` (light) ve `assets/header-illo-dark.png` (dark): ekran görüntüsünden kırpılmış DÜŞÜK çözünürlüklü placeholder'lar. Kullanıcı orijinal illüstrasyonları sağlayacak — layout'ta tam genişlik, aspect korunur.
- İkonlar: 24dp outline, stroke ~1.9dp, yuvarlak uçlu (Material Symbols Rounded outline benzeri): hilal, saat, ayar-slider, konum pini, hedef/konum, chevron.

## Files
- `Imsakiye Redesign.dc.html` — tasarım referansı (turn 3 = light final, turn 4 = dark final)
- `android-res/values/colors.xml`, `android-res/values-night/colors.xml`, `android-res/values/dimens.xml` — hazır token dosyaları
- `CLAUDE_CODE_PROMPT.md` — Claude Code'a verilecek yönerge
