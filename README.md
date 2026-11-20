# San Editor — San Andreas Map Editor
Editor IPL teks untuk GTA San Andreas di Android (Kotlin + Jetpack Compose).
Catatan: IPL biner (bnry) belum didukung. Ini bukan port Ariane; terinspirasi dari Ariane (Dryxio, GPL).

## Developer

**ReyyXSA**

## Panduan penggunaan

1. **Siapkan folder map.** Buat folder (misalnya `map`) yang berisi `gta3.img`, folder `Maps`, dan folder `texdb`. Di Android 11 ke atas, `Android/data` tidak bisa dipilih, jadi salin dulu folder game ke `Download`.
2. **Pilih folder GTA SA.** Di halaman awal, tekan **Pilih** pada kartu "Folder GTA San Andreas", lalu pilih folder `map`. Fungsinya agar model dan tekstur asli tampil di 3D.
3. **Buka atau buat IPL.** Tekan **Buka File IPL** untuk membuka file `.ipl` teks, atau **Buat IPL baru** untuk file kosong. IPL biner (`bnry`) belum didukung.
4. **Tab Objek.** Cari berdasarkan nama model atau ID, dan pakai filter Semua, Luar, Interior, atau LOD. Ketuk kartu objek untuk mengubah Model, ID, X, Y, Z, lalu tekan **Terapkan**. Tombol **Objek baru** di pojok kanan bawah menambah objek.
5. **Tab 3D.** Geser satu jari untuk memutar kamera, cubit untuk zoom. Ketuk objek untuk memilihnya, lalu seret panah merah (X), hijau (Y), atau biru (Z) untuk menggeser. Toolbar berisi Putar, Geser, Fokus, Tampilkan semua, Urungkan, Ulangi, Snap, dan Inspector.
6. **Inspector.** Tab **Objek** berisi tombol geser dan rotasi. Tab **Stream** menampilkan objek detail dari file stream biner, hanya untuk dilihat. Gunakan **Salin ke IPL** agar bisa diedit. Tab **Adegan** berisi Tambah objek, jarak pandang, balik tekstur, dan sembunyikan LOD.
7. **Simpan.** Tombol **Simpan** menimpa file asli. Titik kuning berarti ada perubahan yang belum disimpan. **Simpan sebagai**, **Urungkan**, **Ulangi**, dan **Diagnosa file game** ada di menu titik tiga.

## Changelog

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
