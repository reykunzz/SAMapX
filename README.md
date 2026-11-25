# San Editor – Liquid Glass All Menus

Perubahan dari source `SanEditor-TEST.zip`:

- Semua item pada sidebar menggunakan gaya Liquid Glass biru: gradien transparan, border bercahaya, dan state aktif yang lebih terang.
- Kartu header, langkah, folder GTA SA, dan pemilihan map menggunakan panel glass yang senada.
- Dialog Panduan, Changelog, kritik/saran, bahasa, update, dan dialog konfirmasi memakai permukaan glass biru.
- Label menu `Dukung` pada sidebar diubah menjadi `Donate`.
- Tombol Panduan/Changelog dan baris `SupportSection` di bawah header Beranda dihapus agar tidak duplikat. Akses menu tetap melalui sidebar.
- Fungsi pemilihan folder, pemilihan map, update, dan aksi menu yang sudah ada dipertahankan.

Catatan:
- Efek glass menggunakan gradien/transparansi dan border Compose agar tetap ringan dan kompatibel dengan Android 10 (API 29). Ini bukan blur latar sistem penuh.
- Belum dapat diuji build Gradle di lingkungan ini karena Gradle/Gradle Wrapper tidak tersedia. Silakan build di Android Studio atau Termux.
