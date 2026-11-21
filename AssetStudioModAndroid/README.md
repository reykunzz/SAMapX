# AssetStudioMod Android

Port Android dari [AssetStudioMod](https://github.com/aelurum/AssetStudio) (fork AssetStudio oleh Perfare).
Engine load/parse/filter/export memakai kode asli AssetStudioMod (modul CLI + Utility); UI Android
mengikuti struktur versi desktop: Daftar Aset (cari, filter tipe, centang, preview), Hierarki Scene, Log,
menu Muat file/folder, Extract bundle, Export, Opsi.

Fitur: Texture2D/Sprite -> png/jpg/bmp/tga/webp, TextAsset, MonoBehaviour (json/dump), Font, Shader,
Mesh -> obj, Raw/Dump/JSON dump, Live2D Cubism, extract bundle, daftar aset XML, hierarki scene.

Tidak tersedia di Android (butuh library proprietary/native yang tidak ada):
- Export FBX / Animator / SplitObjects (butuh Autodesk FBX SDK: libAssetStudioFBXNative.so)
- Konversi audio FSB -> WAV (butuh libfmod.so dari FMOD)
- Bundle terkompresi Oodle (butuh libooz.so)
- Preview 3D / pemutar audio
Library opsional di atas bisa ditaruh di `app/Libraries/arm64-v8a/` (akan ikut dikemas otomatis).

Lisensi: MIT (Perfare, aelurum), lihat LICENSE.
