using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
using Android.App;
using Android.Content;
using Android.Content.PM;
using Android.Graphics;
using Android.OS;
using Android.Provider;
using Android.Text;
using Android.Views;
using Android.Widget;
using AssetStudioCLI;
using AssetStudioCLI.Options;

namespace AssetStudioModAndroid
{
    [Activity(Label = "AssetStudioMod", MainLauncher = true, Exported = true,
        ConfigurationChanges = ConfigChanges.Orientation | ConfigChanges.ScreenSize | ConfigChanges.KeyboardHidden)]
    public class MainActivity : Activity
    {
        // ---- menu ids
        const int M_LoadFile = 1, M_LoadFolder = 2, M_Extract = 3, M_Reset = 4,
                  M_Export = 5, M_Options = 6, M_Sort = 7, M_SelAll = 8, M_SelNone = 9, M_About = 10;

        readonly Handler _handler = new Handler(Looper.MainLooper);
        readonly StringBuilder _log = new StringBuilder();
        readonly object _logLock = new object();
        bool _logFlushPosted;
        bool _busy;
        int _previewSeq;
        string _lastDir = "/storage/emulated/0";

        // ---- views
        LinearLayout _assetsPanel, _hierPanel, _logPanel;
        EditText _search;
        Spinner _typeSpinner;
        ListView _assetList, _hierList;
        TextView _count, _hierHint, _logText, _status, _info, _textPreview;
        ScrollView _logScroll;
        ImageView _image;
        ProgressBar _progress;
        Bitmap _bitmap;

        // ---- data
        List<AssetItem> _view = new List<AssetItem>();
        readonly HashSet<AssetItem> _selected = new HashSet<AssetItem>();
        HashSet<BaseNode> _sceneFilter;
        string _sceneFilterName;
        string _typeFilter = "";
        List<string> _typeList = new List<string>();
        readonly Dictionary<Spinner, string[]> _spinnerOpts = new Dictionary<Spinner, string[]>();
        int _sortMode; // 0 asli, 1 nama, 2 tipe, 3 ukuran
        AssetAdapter _assetAdapter;
        readonly List<(BaseNode node, int depth)> _hierItems = new List<(BaseNode, int)>();
        readonly HashSet<BaseNode> _expanded = new HashSet<BaseNode>();
        ArrayAdapter<string> _hierAdapter;
        readonly List<string> _hierLabels = new List<string>();

        // =========================================================== lifecycle
        protected override void OnCreate(Bundle savedInstanceState)
        {
            base.OnCreate(savedInstanceState);

            Engine.Init(FilesDir.AbsolutePath, CacheDir.AbsolutePath, AppendLog, SetProgress);
            BuildUi();
            RequestStorageAccess();
            AppendLog("AssetStudioMod Android siap. Menu (⋮) → Muat file / Muat folder.");
        }

        void RequestStorageAccess()
        {
            try
            {
                if (Build.VERSION.SdkInt >= BuildVersionCodes.R)
                {
                    if (!Android.OS.Environment.IsExternalStorageManager)
                    {
                        new AlertDialog.Builder(this)
                            .SetTitle("Izin penyimpanan")
                            .SetMessage("Aplikasi butuh 'Akses semua file' untuk membaca file Unity dan menyimpan hasil export di penyimpanan internal.")
                            .SetPositiveButton("Buka pengaturan", (s, e) =>
                            {
                                var i = new Intent(Settings.ActionManageAppAllFilesAccessPermission,
                                    Android.Net.Uri.Parse("package:" + PackageName));
                                StartActivity(i);
                            })
                            .SetNegativeButton("Nanti", (s, e) => { })
                            .Show();
                    }
                }
                else
                {
                    RequestPermissions(new[]
                    {
                        Android.Manifest.Permission.ReadExternalStorage,
                        Android.Manifest.Permission.WriteExternalStorage
                    }, 100);
                }
            }
            catch (Exception ex) { AppendLog("[Warning] izin: " + ex.Message); }
        }

        // =========================================================== UI build
        int Dp(int v) => (int)(v * Resources.DisplayMetrics.Density + 0.5f);

        void BuildUi()
        {
            var root = new LinearLayout(this) { Orientation = Orientation.Vertical };

            // tab bar
            var tabs = new LinearLayout(this) { Orientation = Orientation.Horizontal };
            string[] names = { "Daftar Aset", "Hierarki Scene", "Log" };
            for (int i = 0; i < names.Length; i++)
            {
                int idx = i;
                var b = new Button(this) { Text = names[i] };
                b.Click += (s, e) => ShowTab(idx);
                tabs.AddView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f));
            }
            root.AddView(tabs);

            var frame = new FrameLayout(this);
            root.AddView(frame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, 0, 1f));

            // ---------- panel aset
            _assetsPanel = new LinearLayout(this) { Orientation = Orientation.Vertical };
            var searchRow = new LinearLayout(this) { Orientation = Orientation.Horizontal };
            _search = new EditText(this) { Hint = "Cari nama / container" };
            _search.SetSingleLine(true);
            _search.TextChanged += (s, e) => ApplyFilter();
            searchRow.AddView(_search, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f));
            _typeSpinner = new Spinner(this);
            _typeSpinner.ItemSelected += (s, e) =>
            {
                var v = e.Position >= 0 && e.Position < _typeList.Count ? _typeList[e.Position] : "Semua tipe";
                _typeFilter = v == "Semua tipe" ? "" : v;
                ApplyFilter();
            };
            searchRow.AddView(_typeSpinner, new LinearLayout.LayoutParams(Dp(140), ViewGroup.LayoutParams.WrapContent));
            _assetsPanel.AddView(searchRow);

            _count = new TextView(this) { Text = "0 aset" };
            _count.SetPadding(Dp(8), 0, Dp(8), 0);
            _count.Click += (s, e) =>
            {
                if (_sceneFilter != null)
                {
                    _sceneFilter = null; _sceneFilterName = null; ApplyFilter();
                }
            };
            _assetsPanel.AddView(_count);

            _assetAdapter = new AssetAdapter(this);
            _assetList = new ListView(this) { Adapter = _assetAdapter };
            _assetList.ItemClick += (s, e) => ShowPreview(e.Position);
            _assetsPanel.AddView(_assetList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, 0, 1f));

            var previewScroll = new ScrollView(this);
            var pv = new LinearLayout(this) { Orientation = Orientation.Vertical };
            pv.SetPadding(Dp(8), Dp(4), Dp(8), Dp(4));
            _info = new TextView(this) { Typeface = Typeface.Monospace, TextSize = 12f };
            _image = new ImageView(this);
            _image.SetAdjustViewBounds(true);
            _image.SetScaleType(ImageView.ScaleType.FitCenter);
            _textPreview = new TextView(this) { Typeface = Typeface.Monospace, TextSize = 11f };
            _textPreview.SetTextIsSelectable(true);
            pv.AddView(_info);
            pv.AddView(_image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent));
            pv.AddView(_textPreview);
            previewScroll.AddView(pv);
            _assetsPanel.AddView(previewScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, 0, 1f));
            frame.AddView(_assetsPanel);

            // ---------- panel hierarki
            _hierPanel = new LinearLayout(this) { Orientation = Orientation.Vertical, Visibility = ViewStates.Gone };
            _hierHint = new TextView(this) { Text = "" };
            _hierHint.SetPadding(Dp(8), Dp(4), Dp(8), Dp(4));
            _hierPanel.AddView(_hierHint);
            _hierAdapter = new ArrayAdapter<string>(this, Android.Resource.Layout.SimpleListItem1, _hierLabels);
            _hierList = new ListView(this) { Adapter = _hierAdapter };
            _hierList.ItemClick += (s, e) => ToggleHierNode(e.Position);
            _hierList.ItemLongClick += (s, e) => FilterByNode(e.Position);
            _hierPanel.AddView(_hierList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, 0, 1f));
            frame.AddView(_hierPanel);

            // ---------- panel log
            _logPanel = new LinearLayout(this) { Orientation = Orientation.Vertical, Visibility = ViewStates.Gone };
            _logScroll = new ScrollView(this);
            _logText = new TextView(this) { Typeface = Typeface.Monospace, TextSize = 11f };
            _logText.SetPadding(Dp(8), Dp(4), Dp(8), Dp(4));
            _logText.SetTextIsSelectable(true);
            _logScroll.AddView(_logText);
            _logPanel.AddView(_logScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, 0, 1f));
            frame.AddView(_logPanel);

            // ---------- bawah: progress + status
            _progress = new ProgressBar(this, null, Android.Resource.Attribute.ProgressBarStyleHorizontal) { Max = 100 };
            root.AddView(_progress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, ViewGroup.LayoutParams.WrapContent));
            _status = new TextView(this) { Text = "Siap" };
            _status.SetPadding(Dp(8), 0, Dp(8), Dp(4));
            _status.SetSingleLine(true);
            root.AddView(_status);

            SetContentView(root);
            RefreshTypeSpinner();
        }

        void ShowTab(int idx)
        {
            _assetsPanel.Visibility = idx == 0 ? ViewStates.Visible : ViewStates.Gone;
            _hierPanel.Visibility = idx == 1 ? ViewStates.Visible : ViewStates.Gone;
            _logPanel.Visibility = idx == 2 ? ViewStates.Visible : ViewStates.Gone;
            if (idx == 1) RebuildHierarchy();
            if (idx == 2) FlushLog();
        }

        // =========================================================== menu
        public override bool OnCreateOptionsMenu(IMenu menu)
        {
            menu.Add(0, M_LoadFile, 0, "Muat file");
            menu.Add(0, M_LoadFolder, 1, "Muat folder");
            menu.Add(0, M_Extract, 2, "Extract bundle (file/folder)");
            menu.Add(0, M_Export, 3, "Export...");
            menu.Add(0, M_SelAll, 4, "Pilih semua (terfilter)");
            menu.Add(0, M_SelNone, 5, "Kosongkan pilihan");
            menu.Add(0, M_Sort, 6, "Urutkan");
            menu.Add(0, M_Options, 7, "Opsi");
            menu.Add(0, M_Reset, 8, "Reset");
            menu.Add(0, M_About, 9, "Tentang");
            return true;
        }

        public override bool OnOptionsItemSelected(IMenuItem item)
        {
            switch (item.ItemId)
            {
                case M_LoadFile: PickPath(false, p => LoadPaths(new List<string> { p })); break;
                case M_LoadFolder: PickPath(true, p => LoadPaths(new List<string> { p })); break;
                case M_Extract: AskExtract(); break;
                case M_Export: ShowExportDialog(); break;
                case M_Options: ShowOptionsDialog(); break;
                case M_Sort: ShowSortDialog(); break;
                case M_Reset: DoReset(); break;
                case M_SelAll:
                    foreach (var a in _view) _selected.Add(a);
                    _assetAdapter.NotifyDataSetChanged(); UpdateCount(); break;
                case M_SelNone:
                    _selected.Clear(); _assetAdapter.NotifyDataSetChanged(); UpdateCount(); break;
                case M_About:
                    new AlertDialog.Builder(this).SetTitle("AssetStudioMod Android")
                        .SetMessage("Port Android dari AssetStudioMod (aelurum) / AssetStudio (Perfare), lisensi MIT.\n\n" +
                                    "Tidak tersedia di Android: export FBX/Animator (butuh FBX SDK), konversi audio FMOD (butuh libfmod.so), " +
                                    "bundle terkompresi Oodle (butuh libooz.so), pratinjau 3D.")
                        .SetPositiveButton("OK", (s, e) => { }).Show();
                    break;
                default: return base.OnOptionsItemSelected(item);
            }
            return true;
        }

        // =========================================================== jobs
        void RunJob(string title, Action work, Action done = null)
        {
            if (_busy) { Toast.MakeText(this, "Masih ada proses berjalan", ToastLength.Short).Show(); return; }
            _busy = true;
            RunOnUiThread(() => { _status.Text = title + "..."; _progress.Progress = 0; });
            Task.Run(() =>
            {
                try { work(); }
                catch (Exception ex) { AppendLog("[Error] " + ex); }
                finally
                {
                    _busy = false;
                    RunOnUiThread(() =>
                    {
                        try { done?.Invoke(); } catch (Exception ex) { AppendLog("[Error] " + ex.Message); }
                        _status.Text = title + " selesai";
                        _progress.Progress = 100;
                    });
                }
            });
        }

        void LoadPaths(List<string> paths)
        {
            _lastDir = Directory.Exists(paths[0]) ? paths[0] : (System.IO.Path.GetDirectoryName(paths[0]) ?? _lastDir);
            RunJob("Memuat aset", () =>
            {
                AppendLog("Memuat: " + string.Join(", ", paths));
                if (!Engine.Load(paths)) AppendLog("[Warning] Tidak ada file Unity yang bisa dimuat.");
            }, () =>
            {
                _selected.Clear(); _sceneFilter = null; _sceneFilterName = null;
                RefreshTypeSpinner();
                ApplyFilter();
                RebuildHierarchy();
            });
        }

        void DoReset()
        {
            if (_busy) return;
            Engine.Reset();
            _selected.Clear(); _sceneFilter = null; _sceneFilterName = null;
            _info.Text = ""; _textPreview.Text = ""; _image.SetImageBitmap(null);
            RefreshTypeSpinner(); ApplyFilter(); RebuildHierarchy();
            _status.Text = "Direset";
        }

        void AskExtract()
        {
            var items = new[] { "Extract satu file bundle", "Extract satu folder" };
            new AlertDialog.Builder(this).SetTitle("Extract / Decompress bundle")
                .SetItems(items, (s, e) =>
                {
                    bool folder = e.Which == 1;
                    PickPath(folder, p => RunJob("Extract bundle", () => Engine.Extract(p)));
                }).Show();
        }

        // =========================================================== filter / list
        void RefreshTypeSpinner()
        {
            var types = Engine.AllAssets.Select(a => a.TypeString).Distinct().OrderBy(x => x).ToList();
            types.Insert(0, "Semua tipe");
            _typeList = types;
            var ad = new ArrayAdapter<string>(this, Android.Resource.Layout.SimpleSpinnerItem, types);
            ad.SetDropDownViewResource(Android.Resource.Layout.SimpleSpinnerDropDownItem);
            _typeFilter = "";
            _typeSpinner.Adapter = ad;
        }

        void ApplyFilter()
        {
            IEnumerable<AssetItem> q = Engine.AllAssets;
            if (!string.IsNullOrEmpty(_typeFilter)) q = q.Where(a => a.TypeString == _typeFilter);
            var text = _search?.Text?.Trim();
            if (!string.IsNullOrEmpty(text))
                q = q.Where(a => (a.Text ?? "").IndexOf(text, StringComparison.OrdinalIgnoreCase) >= 0 ||
                                 (a.Container ?? "").IndexOf(text, StringComparison.OrdinalIgnoreCase) >= 0);
            if (_sceneFilter != null)
                q = q.Where(a => a.Node != null && _sceneFilter.Contains(a.Node));
            switch (_sortMode)
            {
                case 1: q = q.OrderBy(a => a.Text, StringComparer.OrdinalIgnoreCase); break;
                case 2: q = q.OrderBy(a => a.TypeString).ThenBy(a => a.Text, StringComparer.OrdinalIgnoreCase); break;
                case 3: q = q.OrderByDescending(a => a.FullSize); break;
            }
            _view = q.ToList();
            _assetAdapter.NotifyDataSetChanged();
            UpdateCount();
        }

        void UpdateCount()
        {
            var s = $"{_view.Count} aset ({_selected.Count} terpilih)";
            if (_sceneFilter != null) s += $" | filter hierarki: {_sceneFilterName} (tap untuk hapus)";
            _count.Text = s;
        }

        void ShowSortDialog()
        {
            new AlertDialog.Builder(this).SetTitle("Urutkan")
                .SetSingleChoiceItems(new[] { "Urutan asli", "Nama", "Tipe", "Ukuran (besar dulu)" }, _sortMode, (s, e) =>
                {
                    _sortMode = e.Which; ApplyFilter(); ((Dialog)s).Dismiss();
                }).Show();
        }

        // =========================================================== preview
        void ShowPreview(int position)
        {
            if (position < 0 || position >= _view.Count) return;
            var item = _view[position];
            var seq = ++_previewSeq;
            _info.Text = "Memuat preview...";
            _textPreview.Text = "";
            Task.Run(() =>
            {
                var r = Engine.Preview(item);
                RunOnUiThread(() =>
                {
                    if (seq != _previewSeq) return;
                    _info.Text = r.Info;
                    _textPreview.Text = r.Text ?? "";
                    SetBitmap(r.Image);
                });
            });
        }

        void SetBitmap(byte[] png)
        {
            var old = _bitmap;
            _bitmap = null;
            if (png != null && png.Length > 0)
            {
                try
                {
                    var bounds = new BitmapFactory.Options { InJustDecodeBounds = true };
                    BitmapFactory.DecodeByteArray(png, 0, png.Length, bounds);
                    int sample = 1;
                    while (bounds.OutWidth / sample > 2048 || bounds.OutHeight / sample > 2048) sample *= 2;
                    _bitmap = BitmapFactory.DecodeByteArray(png, 0, png.Length, new BitmapFactory.Options { InSampleSize = sample });
                }
                catch (Exception ex) { AppendLog("[Warning] preview gambar: " + ex.Message); }
            }
            _image.SetImageBitmap(_bitmap);
            old?.Recycle();
        }

        // =========================================================== hierarchy
        void RebuildHierarchy()
        {
            _hierItems.Clear();
            void Walk(BaseNode n, int d)
            {
                _hierItems.Add((n, d));
                if (_expanded.Contains(n)) foreach (var c in n.nodes) Walk(c, d + 1);
            }
            foreach (var r in Engine.Tree) Walk(r, 0);
            _hierLabels.Clear();
            foreach (var (n, d) in _hierItems)
                _hierLabels.Add(new string(' ', d * 3) + (n.nodes.Count > 0 ? (_expanded.Contains(n) ? "▾ " : "▸ ") : "• ") + n.Text);
            _hierAdapter.NotifyDataSetChanged();
            _hierHint.Text = Engine.Tree.Count == 0
                ? "Hierarki kosong. Aktifkan 'Bangun hierarki scene' di Opsi lalu muat ulang file."
                : "Tap: buka/tutup node. Tahan: filter daftar aset ke node ini.";
        }

        void ToggleHierNode(int pos)
        {
            if (pos < 0 || pos >= _hierItems.Count) return;
            var n = _hierItems[pos].node;
            if (n.nodes.Count == 0) return;
            if (!_expanded.Remove(n)) _expanded.Add(n);
            RebuildHierarchy();
        }

        void FilterByNode(int pos)
        {
            if (pos < 0 || pos >= _hierItems.Count) return;
            var node = _hierItems[pos].node;
            var set = new HashSet<BaseNode>();
            void Collect(BaseNode n) { set.Add(n); foreach (var c in n.nodes) Collect(c); }
            Collect(node);
            _sceneFilter = set; _sceneFilterName = node.Text;
            ApplyFilter();
            ShowTab(0);
        }

        // =========================================================== export
        void ShowExportDialog()
        {
            if (Engine.AllAssets.Count == 0)
            {
                Toast.MakeText(this, "Belum ada aset dimuat", ToastLength.Short).Show();
                return;
            }
            var lay = new LinearLayout(this) { Orientation = Orientation.Vertical };
            lay.SetPadding(Dp(16), Dp(8), Dp(16), Dp(8));

            lay.AddView(new TextView(this) { Text = "Cakupan" });
            var scope = new Spinner(this);
            var scopes = new[] { $"Terpilih ({_selected.Count})", $"Semua yang terlihat ({_view.Count})", $"Semua aset ({Engine.AllAssets.Count})" };
            scope.Adapter = Arr(scopes);
            scope.SetSelection(_selected.Count > 0 ? 0 : 1);
            lay.AddView(scope);

            lay.AddView(new TextView(this) { Text = "Mode" });
            var mode = new Spinner(this);
            var modes = new[] { "Convert (png/wav/dll)", "Raw", "Dump (.txt)", "JSON dump", "Live2D Cubism", "Daftar aset (XML)" };
            mode.Adapter = Arr(modes);
            lay.AddView(mode);

            lay.AddView(new TextView(this) { Text = "Output: " + Engine.Settings.Output });

            new AlertDialog.Builder(this).SetTitle("Export").SetView(lay)
                .SetPositiveButton("Mulai", (s, e) =>
                {
                    List<AssetItem> list = scope.SelectedItemPosition == 0 ? _selected.ToList()
                        : scope.SelectedItemPosition == 1 ? new List<AssetItem>(_view)
                        : new List<AssetItem>(Engine.AllAssets);
                    int m = mode.SelectedItemPosition;
                    if (m != 4 && list.Count == 0) { Toast.MakeText(this, "Tidak ada aset untuk diexport", ToastLength.Short).Show(); return; }
                    switch (m)
                    {
                        case 0: RunJob("Export", () => Engine.Export(list, WorkMode.Export)); break;
                        case 1: RunJob("Export raw", () => Engine.Export(list, WorkMode.ExportRaw)); break;
                        case 2: RunJob("Dump", () => Engine.Export(list, WorkMode.Dump)); break;
                        case 3: RunJob("JSON dump", () => Engine.Export(list, WorkMode.JsonDump)); break;
                        case 4: RunJob("Export Live2D", () => Engine.ExportLive2D(), () => { RefreshTypeSpinner(); ApplyFilter(); }); break;
                        case 5: RunJob("Export daftar aset", () => Engine.ExportAssetListXml(list)); break;
                    }
                })
                .SetNegativeButton("Batal", (s, e) => { }).Show();
        }

        ArrayAdapter<string> Arr(string[] items)
        {
            var ad = new ArrayAdapter<string>(this, Android.Resource.Layout.SimpleSpinnerItem, items);
            ad.SetDropDownViewResource(Android.Resource.Layout.SimpleSpinnerDropDownItem);
            return ad;
        }

        // =========================================================== options
        void ShowOptionsDialog()
        {
            var st = Engine.Settings;
            var sv = new ScrollView(this);
            var lay = new LinearLayout(this) { Orientation = Orientation.Vertical };
            lay.SetPadding(Dp(16), Dp(8), Dp(16), Dp(8));
            sv.AddView(lay);

            EditText Edit(string label, string value, bool number = false)
            {
                lay.AddView(new TextView(this) { Text = label });
                var et = new EditText(this) { Text = value ?? "" };
                et.SetSingleLine(true);
                if (number) et.InputType = InputTypes.ClassNumber;
                lay.AddView(et);
                return et;
            }
            Spinner Spin(string label, string[] opts, string cur)
            {
                lay.AddView(new TextView(this) { Text = label });
                var sp = new Spinner(this) { Adapter = Arr(opts) };
                _spinnerOpts[sp] = opts;
                var ix = Array.IndexOf(opts, cur);
                sp.SetSelection(ix < 0 ? 0 : ix);
                lay.AddView(sp);
                return sp;
            }
            CheckBox Check(string label, bool v)
            {
                var cb = new CheckBox(this) { Text = label, Checked = v };
                lay.AddView(cb);
                return cb;
            }
            string Sel(Spinner sp) => _spinnerOpts[sp][Math.Max(0, sp.SelectedItemPosition)];

            var output = Edit("Folder output", st.Output);
            var img = Spin("Format gambar", new[] { "None", "Png", "Jpeg", "Bmp", "Tga", "Webp" }, st.ImageFormat);
            var aud = Spin("Format audio", Enum.GetNames(typeof(AudioFormat)), st.AudioFormat);
            var grp = Spin("Kelompokkan hasil export", Enum.GetNames(typeof(AssetGroupOption)), st.Group);
            var fnm = Spin("Format nama file", Enum.GetNames(typeof(FilenameFormat)), st.FilenameFormat);
            var spr = Spin("Cropping sprite", Enum.GetNames(typeof(SpriteCroppingMode)), st.SpriteCropping);
            var ovw = Check("Timpa file yang sudah ada", st.Overwrite);
            var tasks = Edit("Maks. task export paralel", st.MaxTasks.ToString(), true);
            var uv = Edit("Versi Unity (opsional, mis. 2022.3.2f1)", st.UnityVersion);
            var asm = Edit("Folder assembly (opsional, untuk MonoBehaviour)", st.AssemblyFolder);
            var all = Check("Tampilkan semua tipe aset (lebih lambat)", st.LoadAll);
            var hier = Check("Bangun hierarki scene saat memuat", st.BuildHierarchy);
            var dtd = Check("Dekompres bundle ke disk (hemat RAM)", st.DecompressToDisk);
            var nre = Check("Jangan pulihkan ekstensi TextAsset", st.NotRestoreExt);
            var atv = Check("Hindari parsing via TypeTree", st.AvoidTypetree);
            var rbm = Check("Raw byte array dari MonoBehaviour", st.RawBytesFromMono);

            lay.AddView(new TextView(this) { Text = "— Live2D —", Typeface = Typeface.DefaultBold });
            var l2g = Spin("Grup model", Enum.GetNames(typeof(CubismLive2DExtractor.Live2DModelGroupOption)), st.L2dGroup);
            var l2m = Spin("Mode motion", Enum.GetNames(typeof(CubismLive2DExtractor.Live2DMotionMode)), st.L2dMotion);
            var l2s = Check("Haluskan motion (Bezier)", st.L2dSmooth);
            var l2f = Check("Cari aset Live2D via nama file", st.L2dSearchByFilename);
            var l2t = Check("Filter tekstur ('texture' di nama)", st.L2dFilterTextures);

            var lvl = Spin("Level log", Enum.GetNames(typeof(AssetStudio.LoggerEvent)), st.LogLevel);

            new AlertDialog.Builder(this).SetTitle("Opsi").SetView(sv)
                .SetPositiveButton("Simpan", (s, e) =>
                {
                    st.Output = output.Text.Trim();
                    st.ImageFormat = Sel(img); st.AudioFormat = Sel(aud);
                    st.Group = Sel(grp); st.FilenameFormat = Sel(fnm); st.SpriteCropping = Sel(spr);
                    st.Overwrite = ovw.Checked;
                    st.MaxTasks = int.TryParse(tasks.Text, out var t) && t > 0 ? t : 1;
                    st.UnityVersion = uv.Text.Trim(); st.AssemblyFolder = asm.Text.Trim();
                    st.LoadAll = all.Checked; st.BuildHierarchy = hier.Checked; st.DecompressToDisk = dtd.Checked;
                    st.NotRestoreExt = nre.Checked; st.AvoidTypetree = atv.Checked; st.RawBytesFromMono = rbm.Checked;
                    st.L2dGroup = Sel(l2g); st.L2dMotion = Sel(l2m);
                    st.L2dSmooth = l2s.Checked; st.L2dSearchByFilename = l2f.Checked; st.L2dFilterTextures = l2t.Checked;
                    st.LogLevel = Sel(lvl);
                    st.Save(FilesDir.AbsolutePath);
                    Toast.MakeText(this, "Opsi disimpan (opsi muat berlaku saat muat ulang)", ToastLength.Short).Show();
                })
                .SetNegativeButton("Batal", (s, e) => { }).Show();
        }

        // =========================================================== file browser
        void PickPath(bool folder, Action<string> onPick)
        {
            var cur = Directory.Exists(_lastDir) ? _lastDir : "/storage/emulated/0";
            var entries = new List<(string label, string path, bool dir)>();
            var labels = new List<string>();

            var lay = new LinearLayout(this) { Orientation = Orientation.Vertical };
            var row = new LinearLayout(this) { Orientation = Orientation.Horizontal };
            var edit = new EditText(this) { Text = cur };
            edit.SetSingleLine(true);
            var go = new Button(this) { Text = "Buka" };
            row.AddView(edit, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f));
            row.AddView(go);
            lay.AddView(row);
            var list = new ListView(this);
            var adapter = new ArrayAdapter<string>(this, Android.Resource.Layout.SimpleListItem1, labels);
            list.Adapter = adapter;
            lay.AddView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MatchParent, Dp(360)));

            AlertDialog dlg = null;

            void Nav(string dir)
            {
                try
                {
                    var dirs = Directory.GetDirectories(dir).OrderBy(d => d, StringComparer.OrdinalIgnoreCase);
                    var files = folder ? Enumerable.Empty<string>()
                        : Directory.GetFiles(dir).OrderBy(f => f, StringComparer.OrdinalIgnoreCase);
                    entries.Clear(); labels.Clear();
                    var parent = System.IO.Path.GetDirectoryName(dir.TrimEnd('/'));
                    entries.Add(("⬆ ..", string.IsNullOrEmpty(parent) ? "/" : parent, true));
                    foreach (var d in dirs) entries.Add(("📁 " + System.IO.Path.GetFileName(d), d, true));
                    foreach (var f in files) entries.Add(("📄 " + System.IO.Path.GetFileName(f), f, false));
                    foreach (var en in entries) labels.Add(en.label);
                    cur = dir; edit.Text = dir;
                    adapter.NotifyDataSetChanged();
                }
                catch (Exception ex)
                {
                    Toast.MakeText(this, "Tidak bisa membuka: " + ex.Message, ToastLength.Long).Show();
                }
            }

            go.Click += (s, e) => Nav(edit.Text.Trim());
            list.ItemClick += (s, e) =>
            {
                if (e.Position < 0 || e.Position >= entries.Count) return;
                var en = entries[e.Position];
                if (en.dir) Nav(en.path);
                else { dlg.Dismiss(); onPick(en.path); }
            };

            var b = new AlertDialog.Builder(this).SetTitle(folder ? "Pilih folder" : "Pilih file").SetView(lay)
                .SetNegativeButton("Batal", (s, e) => { });
            if (folder) b.SetPositiveButton("Pilih folder ini", (s, e) => onPick(cur));
            dlg = b.Create();
            Nav(cur);
            dlg.Show();
        }

        // =========================================================== log / progress
        void AppendLog(string line)
        {
            lock (_logLock)
            {
                _log.AppendLine(line);
                if (_log.Length > 90000) _log.Remove(0, _log.Length - 60000);
                if (_logFlushPosted) return;
                _logFlushPosted = true;
            }
            _handler.PostDelayed(new Java.Lang.Runnable(FlushLog), 300);
        }

        void FlushLog()
        {
            string text;
            lock (_logLock) { text = _log.ToString(); _logFlushPosted = false; }
            if (_logText == null) return;
            _logText.Text = text;
            _logScroll.Post(() => _logScroll.FullScroll(FocusSearchDirection.Down));
        }

        void SetProgress(int pct, string msg)
        {
            RunOnUiThread(() =>
            {
                _progress.Progress = Math.Max(0, Math.Min(100, pct));
                if (!string.IsNullOrEmpty(msg)) _status.Text = msg;
            });
        }

        // =========================================================== adapter
        class RowView : LinearLayout
        {
            public int Pos;
            public CheckBox Check;
            public TextView Title, Sub;

            public RowView(Context c, Action<int, bool> onCheck) : base(c)
            {
                Orientation = Orientation.Horizontal;
                SetGravity(GravityFlags.CenterVertical);
                Check = new CheckBox(c) { Focusable = false };
                Check.Click += (s, e) => onCheck(Pos, Check.Checked);
                AddView(Check);
                var col = new LinearLayout(c) { Orientation = Orientation.Vertical };
                Title = new TextView(c) { TextSize = 15f };
                Title.SetSingleLine(true);
                Sub = new TextView(c) { TextSize = 11f };
                Sub.SetSingleLine(true);
                col.AddView(Title); col.AddView(Sub);
                AddView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WrapContent, 1f));
                SetPadding(8, 8, 8, 8);
            }
        }

        class AssetAdapter : BaseAdapter<AssetItem>
        {
            readonly MainActivity _a;
            public AssetAdapter(MainActivity a) { _a = a; }
            public override int Count => _a._view.Count;
            public override AssetItem this[int position] => _a._view[position];
            public override long GetItemId(int position) => position;

            public override View GetView(int position, View convertView, ViewGroup parent)
            {
                var row = convertView as RowView ?? new RowView(_a, (pos, chk) =>
                {
                    if (pos < 0 || pos >= _a._view.Count) return;
                    var it = _a._view[pos];
                    if (chk) _a._selected.Add(it); else _a._selected.Remove(it);
                    _a.UpdateCount();
                });
                var item = _a._view[position];
                row.Pos = position;
                row.Check.Checked = _a._selected.Contains(item);
                row.Title.Text = item.Text;
                row.Sub.Text = $"{item.TypeString} • {FormatSize(item.FullSize)}" +
                               (string.IsNullOrEmpty(item.Container) ? "" : " • " + item.Container);
                return row;
            }

            static string FormatSize(long b) =>
                b >= 1 << 20 ? $"{b / 1048576.0:0.0} MB" : b >= 1 << 10 ? $"{b / 1024.0:0.0} KB" : $"{b} B";
        }
    }
}
