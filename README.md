# San Editor — San Andreas Map Editor
Editor IPL teks untuk GTA San Andreas di Android (Kotlin + Jetpack Compose).
Catatan: IPL biner (bnry) belum didukung. Ini bukan port Ariane; terinspirasi dari Ariane (Dryxio, GPL).

## Developer

**ReyyXSA**

## Panduan penggunaan

1. **Pilih folder game.** Di halaman awal, tekan Ganti pada kartu Folder GTA San Andreas, lalu pilih folder files yang berisi data, models, dan texdb.
2. **Pilih map.** Tekan Pilih map. Buka data, lalu maps, lalu pilih folder wilayah (misalnya LA). Tekan file .ipl untuk mengedit objek, atau file .ide untuk menambah model.
3. **Tambah model ke IDE.** Di file .ide, cari model di kolom Cari model dari gta3.img. Hanya model yang ada di gta3.img dan belum punya ID yang ditampilkan. Isi ID (atau tekan ID otomatis, hasilnya di bawah 20000), isi nama TXD, lalu tekan Tambah ke IDE. File IDE lama dicadangkan otomatis.
4. **Tambah objek ke IPL.** Buka file .ipl, lalu tekan Tambah objek dan pilih model yang sudah punya ID. Atur posisi, lalu Simpan.
5. **Tampilan 3D.** Buka tab 3D. Geser satu jari untuk memutar kamera, cubit untuk zoom. Ketuk objek untuk memilihnya, lalu seret panah X, Y, atau Z untuk menggeser. Toolbar berisi Putar, Geser, Fokus, Grid, Tampilan atas, Pilih banyak, dan Inspector.
6. **Inspector.** Tab Objek berisi tombol geser, rotasi, Kunci, dan Hapus. Tab Adegan berisi Tambah objek, Ganti model, dan Tanah.
7. **Simpan.** Tombol Simpan menimpa file asli. Titik kuning berarti ada perubahan yang belum disimpan. Menu titik tiga berisi Simpan sebagai, Urungkan, Ulangi, dan Diagnosa file game.
8. **Aturan penting.** Pakai ID di bawah 20000. ID 20000 ke atas membuat game crash. Pastikan nama model sama persis di IDE, IPL, dan gta3.img. Backup gta3.img sebelum mengubahnya.

## Tampilan atas dan pilih banyak (1.4.0)

- **Tampilan atas**: tekan ikon panah atas di toolbar 3D. Kamera menghadap lurus ke bawah dan sudut vertikalnya dikunci. Putar tetap bisa dipakai untuk mengatur arah peta. Tekan lagi untuk kembali ke tampilan miring.
- **Pilih banyak**: tekan ikon centang di toolbar. Ketuk objek untuk menambah atau mengeluarkannya dari pilihan. Seret salah satu objek yang dipilih untuk menggeser semuanya. Panel grup memuat tombol X/Y/Z, putar ±90, Hapus semua, dan Batal pilih.

## Impor model baru (1.3.0)

Di tab 3D, buka panel **Adegan**, lalu di bagian **Impor model baru**:
1. Pilih file `.dff`. Nama model otomatis terisi dari nama file.
2. Pilih file `.txd` jika model punya texture sendiri (opsional).
3. Tekan **Impor & tempatkan**. Model langsung muncul di posisi kamera.

Catatan: model impor hanya bisa dilihat dan ditempatkan di editor. Agar muncul di game, file DFF dan TXD harus dimasukkan ke dalam gta3.img atau mod loader. Gunakan **Simpan IDE** untuk membuat file IDE pendamping.

## Cara rilis update

1. Naikkan `versionCode` dan `versionName` di `app/build.gradle.kts`.
2. Commit dan push ke `main`.
3. Buat tag: `git tag v1.8.4 && git push origin v1.8.4`.
4. Workflow membuat GitHub Release dan melampirkan APK otomatis. Aplikasi akan mendeteksinya saat dibuka.

## Changelog

- **1.9.21**: Tampilan landscape permanen dengan sidebar, tema kaca biru, penskalaan UI, dan Inspector bertema kaca.
- **1.9.18**: Gizmo di atas tombol kamera di kanan bawah. Kartu objek di kiri bawah.
- **1.9.17**: Toolbar atas dirapikan. Lima ikon utama, sisanya di menu ≡.
- **1.9.16**: Tampilan 3D dirapikan. Panel kanan lebih kecil, gizmo di kiri bawah, kartu objek di tengah bawah.
- **1.9.15**: Tampilan 3D diperbarui: pad keyboard untuk arah, gizmo sumbu untuk orbit, joystick dihapus. Tab Objek dipindah ke menu ⋮.
- **1.9.14**: Tombol arah memakai pad bergaya D-pad. Bahasa mengikuti sistem: Indonesia jika perangkat berbahasa Indonesia, selain itu English.
- **1.9.13**: Gizmo dan kotak merah kembali seperti awal. Tombol Edit dihapus. Rotasi objek pindah ke bawah panel kamera.
- **1.9.12**: Objek yang hanya dipilih diberi kotak kuning. Kotak merah dan gizmo muncul setelah tombol Edit ditekan.
- **1.9.11**: Tombol panah kamera dan rotasi objek bisa ditahan untuk berulang, tanpa ketuk berkali-kali.
- **1.9.10**: Mode Objek: tombol ↺ ↻ untuk memutar objek 15° ke kiri atau kanan.
- **1.9.9**: Tap objek hanya memilih. Geser hanya bisa setelah menekan tombol Edit (pensil).
- **1.9.8**: Tombol Edit dihapus. Objek yang sudah dipilih langsung bisa digeser dengan jari.
- **1.9.7**: Tombol Edit di kartu objek. Objek hanya bisa digeser setelah Edit diaktifkan.
- **1.9.6**: Mode Jelajah: objek yang sudah dipilih langsung bisa digeser tanpa menahan.
- **1.9.5**: Mode Jelajah: pilih objek cukup dengan sentuh. Mode Kamera dan Objek tetap perlu ditahan.
- **1.9.4**: Tombol Kembali diganti panah kiri. Setelah mengedit objek, panah kembali ke mode Jelajah dan posisi kamera sebelumnya.
- **1.9.3**: Tambah objek memakai kisi gambar yang bisa digulir. Ketuk gambar untuk menaruh objek, tanpa mengetik ID.
- **1.9.2**: Mode Jelajah, Kamera, dan Objek dipisah. Pilih objek dengan tahan. Joystick untuk putar kamera. Buka dan Simpan pindah ke menu ⋮ di tab 3D.
- **1.9.1**: Perbaikan toolbar atas di tab 3D yang tidak bisa ditekan.
- **1.9.0**: Tampilan 3D layar penuh. Header transparan tanpa logo, toolbar dan Inspector di kiri atas.
- **1.8.10**: Pratinjau texture model di daftar Tambah objek.
- **1.8.9**: Mode Objek: kamera berpusat di objek, tombol arah menggeser dan memutar objek. Geser objek perlu ditahan lebih lama.
- **1.8.8**: Nama file APK rilis sekarang SanEditor-v<versi>.apk.
- **1.8.7**: Rotasi kamera lebih halus. Geser objek perlu ditahan sebentar. Tombol kamera punya kecepatan dan tombol putar.
- **1.8.6**: Pop-up update menampilkan catatan rilis.
- **1.8.5**: Alamat update GitHub diperbarui ke akun reykunzz.
- **1.8.4**: Cek update otomatis dari GitHub, dan tombol Cek update manual.
- **1.8.3**: Kartu objek dibuat lebih kecil. Tombol kamera berbentuk panah.
- **1.8.2**: Tombol arah kamera (kiri, kanan, maju, mundur, naik, turun). Objek hanya bergeser kalau digeser cukup jauh. Kartu nama objek lebih kecil.
- **1.8.1**: Editor 3D, panduan, dan changelog sekarang dua bahasa (Indonesia dan English). Changelog dipersingkat.
- **1.8.0**: Dukungan Saweria, pilihan bahasa, dan kritik dan saran lewat email atau Facebook.
- **1.7.14**: Panduan dan changelog tampil sebagai pop-up.
- **1.7.0**: Pilih map, tambah model ke IDE dari gta3.img.
- **1.6.0**: Export paket mod.
- **1.5.0**: Grid, kunci objek, pilih banyak, dan ganti model.
- **1.4.0**: Tampilan atas dan pilih banyak objek.
