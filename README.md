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

## Changelog

### 1.7.0
- Pilih map: tombol Pilih map di halaman awal menampilkan folder dan file di files/data (buka folder, pilih .ipl untuk diedit, .ide untuk tambah model).
- Tambah model ke IDE: pilih model dari gta3.img yang belum punya ID, isi ID dan nama TXD, lalu aplikasi menulis satu baris ke blok objs. File IDE lama dicadangkan di penyimpanan aplikasi.
- Model yang baru ditambahkan langsung muncul di Tambah objek tanpa perlu membuka ulang folder game.

### 1.6.2
- Dihapus: impor DFF/TXD dan export paket custom. Fitur ini menyebabkan crash karena model baru perlu IMG dan collision.
- Tambah objek hanya menampilkan model yang punya ID di IDE game, supaya ID-nya valid.

### 1.6.1
- Nama model impor diperiksa terhadap IDE game dan gta3.img. Nama yang sudah dipakai ditolak.
- ID model impor dipilih otomatis dari ID kosong di atas ID tertinggi game.
- Ekspor dibatalkan jika ada bentrok nama atau ID, supaya game tidak crash.
- Model impor yang bentrok saat dipulihkan dilewati.

### 1.6.0
- Export paket mod: membuat IDE, IPL, dan DFF/TXD model impor dalam struktur folder game (data/maps/custom, models/gta3, texdb/txd).
- Peringatan sebelum export jika ada objek dengan ID yang tidak dikenal.
- Panduan pemasangan ada di BACA_DULU.txt di dalam folder export.

### 1.5.3
- Model impor disimpan di penyimpanan aplikasi dan dipulihkan saat game dibuka lagi. Model tidak lagi berubah jadi kubus setelah aplikasi dibuka ulang.
- Simpan IDE menyertakan semua model impor, termasuk yang belum dipakai di IPL.
- Peringatan: model impor belum masuk gta3.img, jadi jangan dipakai di game dulu.

### 1.5.0
- Perbaikan: grid tidak lagi crash saat dinyalakan.
- Grid biru di tanah sekarang diatur tombol Grid (menyala secara default). Grid ganda sudah dihapus.
- Grid tanah dan penggaris: tombol Grid di toolbar. Pilih 2 objek di mode Pilih banyak untuk melihat jaraknya.
- Kunci objek: objek terkunci tidak bisa digeser, diputar, atau diubah posisinya. Kunci hanya berlaku di sesi aplikasi dan tidak ditulis ke IPL.
- Pencarian model lengkap di picker "Tambah objek": daftar dari IDE, gta3.img, dan model impor.
- Ganti model: di tab Adegan, isi "Dari" dan "Menjadi", lalu tekan Ganti semua. Objek terkunci dilewati.
- Draf otomatis: perubahan disimpan ke draf 1,5 detik setelah edit terakhir. Saat aplikasi dibuka lagi, dialog akan menawarkan pemulihan.
- Undo lebih jelas: tombol Batalkan menampilkan aksi terakhir, misalnya "Batalkan: Geser X".

### 1.4.0
- Tampilan atas (tombol di toolbar): kamera tegak lurus ke bawah, seperti minimap.
- Pilih banyak objek (tombol Pilih banyak): ketuk beberapa objek. Seret salah satunya untuk menggeser semuanya. Panel grup punya tombol geser, putar, hapus, dan batal pilih.

### 1.3.0
- Impor model baru: pilih file DFF (dan TXD opsional) di tab Adegan, lalu tempatkan di 3D.
- Model hasil impor otomatis mendapat ID mulai 20000 dan ikut tersimpan di Simpan IDE.

### 1.2.2
- Zoom tanpa batas di tampilan 3D.
- Geser objek bebas dengan sentuhan: seret objek yang sudah dipilih.
- Simpan IDE: file .ide pendamping dari IPL.

### 1.2.1
- Logo baru dan ikon launcher.
- Menu awal potret, editor landscape saat file IPL terbuka.
- Tombol Buat IPL baru.
- Panduan penggunaan di halaman awal.

### 1.2.0 (UI V4)
- Navigasi pindah ke atas (tab Objek dan 3D); tab Peta dihapus.
- Toolbar 3D ringkas berbasis ikon: Putar, Geser, Fokus, Semua, Undo, Redo, Inspector.
- Zoom viewport dengan scroll roda mouse.
- Tombol Simpan di samping Buka.
- Menu titik tiga dibersihkan (tanpa versi, changelog, developer); changelog ada di halaman awal.
- Tema Deep Emerald (hijau zamrud gelap) dan font DM Sans; angka/koordinat memakai monospace.
- Inspector bertab (Objek, Stream, Adegan) dan kartu objek terpilih di viewport (Salin, Hapus, Tutup).
- Chip Snap di toolbar 3D.
- Daftar objek 2 kolom dengan filter Semua, Luar, Interior, LOD.
- Halaman awal: daftar file terakhir, status folder GTA satu baris, changelog dapat dilipat.
