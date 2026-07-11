# Claude Code Yönergesi — KD İmsakiye Tema Uygulaması

Aşağıdaki metni Claude Code'a aynen verebilirsin:

---

Bu repo native bir Android imsakiye uygulaması (3 ekran: İmsakiye, Namaz Vakitleri, Ayarlar). Ekteki `C:\Users\PC\AndroidStudioProjects\uxtest\claude_references\ui/` klasöründe yüksek sadakatli bir HTML tasarım referansı ve README var.

GÖREV: Uygulamanın 3 ekranını bu tasarıma göre, hem light hem dark tema destekli olarak yeniden yaz.

KURALLAR:
1. Önce kod tabanını keşfet: View/XML mi Compose mu, mevcut tema altyapısı ne — TASARIMI MEVCUT MİMARİYE UYARLA, mimariyi değiştirme.
2. `C:\Users\PC\AndroidStudioProjects\uxtest\claude_references\ui/README.md` tek gerçek kaynak: tüm renk tokenları (light/dark tablosu), tipografi ölçeği, spacing/radius değerleri ve ekran ekran layout spesifikasyonu orada. Değerleri oradan al, uydurma.
3. Hazır `android-res/values/colors.xml` ve `android-res/values-night/colors.xml` dosyalarını projenin res klasörüne entegre et (isim çakışması varsa mevcut adlandırma konvansiyonuna uyarla). Tema geçişi values/values-night + AppCompatDelegate ile çalışsın; Ayarlar'daki Gündüz/Gece/Otomatik segmenti MODE_NIGHT_NO / MODE_NIGHT_YES / MODE_NIGHT_FOLLOW_SYSTEM'e bağlansın ve tercih kalıcı olsun (DataStore/SharedPreferences).
4. Fontlar: UI için Quicksand, saatler/rakamlar için Space Mono (res/font altına ekle, downloadable fonts da olur). Geri sayımda tabular figürler kullan (Space Mono zaten monospace).
5. Kritik detaylar (README'de tamamı var):
   - Geri sayım saniyelik canlı; iftar geçince hedef ertesi imsak, etiket "İMSAKA KALAN".
   - Progress barın dolu kısmında statik accent glow (hafif blur'lu drawable).
   - İmsakiye tablosunda başlık satırı sabit, günler scroll; scrollbar HİÇBİR listede görünmesin.
   - Bugün satırı/kartı accent vurgulu; "Dün" öğeleri soluk (opacity ~.42-.5).
   - Namaz Vakitleri'nde Bugün kartı 3×2 büyük grid; geçmiş vakitler grayed-out (opacity .45); sıradaki vakit renkle vurgulanır, ekstra "Sıradaki" metni YAZILMAZ. Diğer günler kompakt 6'lı tek satır.
   - Bottom navbar: pill (dış radius 26dp, iç aktif tab 20dp, padding 6dp — konsantrik köşeler). Light: BEYAZ bar (#FFFFFF) + krem aktif tab (#F0EBE1), ikonlar koyu (#22303F). Dark: açık-koyu bar (#28374E) + daha koyu aktif tab (#0F1826), aktif içerik #F2F5F9, pasif ikonlar rgba(233,237,243,.5). Aktif tabda ikon+etiket, pasiflerde yalnız ikon. Renkler kullanıcı tarafından güncellendi — HTML'deki turn 3 (light) ve turn 4 (dark) navbar değerleri esas.
   - Ayarlar'da hatırlatma ve geri sayım süreleri SAAT ve DAKİKA olarak iki ayrı stepper ile ayarlanır.
6. Header illüstrasyonları (`assets/header-illo*.png`) düşük çözünürlüklü placeholder — ImageView/Image olarak tam genişlik yerleştir, ben sonra orijinalleriyle değiştireceğim.
7. Dokunma hedeflerini min 44dp yap (stepper butonları dahil), metin ölçülerini sp kullan.
8. Bitirince her ekranın light+dark ekran görüntüsünü alıp HTML referansla karşılaştır, sapmaları düzelt.

---
