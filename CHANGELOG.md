# Changelog

Catatan rilis Renata Claude. Versi terbaru selalu ada di bagian atas.

---

## 1.3.5 — 27 Oktober 2026

Fokus rilis ini: membuat pembaruan otomatis benar-benar bisa diandalkan di perangkat.

### Diperbaiki

- **Pemeriksaan pembaruan diulang setiap kali aplikasi kembali aktif.** Sebelumnya pemeriksaan hanya berjalan sekali saat aplikasi dibuka, jadi versi baru yang terbit saat aplikasi masih terbuka tidak pernah terdeteksi. Sekarang pemeriksaan otomatis berjalan lagi setiap kali Anda kembali ke aplikasi, dengan jeda minimum 5 menit agar server manifest tidak dibanjiri.
- **Cermin cadangan untuk manifest pembaruan.** Kalau `raw.githubusercontent.com` tidak bisa dijangkau dari jaringan Anda, aplikasi sekarang mencoba cermin jsDelivr dan berkas `version.json` di GitHub Releases sebelum menyerah.
- **Kegagalan pemeriksaan tidak lagi diam.** Kalau pemeriksaan otomatis gagal, alasannya ditampilkan lewat pesan singkat, bukan hilang tanpa jejak.
- **Daftar URL manifest sudah dirapikan.** URL kosong dibuang dan URL duplikat dihapus, sehingga cermin cadangan tidak pernah dicoba dua kali.

### Ditambahkan

- **Versi terpasang tampil di Pengaturan.** Bagian "Pembaruan" kini menampilkan versi dan kode versi aplikasi yang sedang berjalan, jadi mudah memastikan apakah pembaruan sudah terpasang.
- **Pemilih model di Pengaturan.** Model yang tersedia untuk penyedia terpilih bisa dipilih dari daftar dropdown, lengkap dengan keterangan singkat tiap model.
- **Daftar model MiniMax diselaraskan dengan server.** Hanya `MiniMax-M3.1-Flash` dan `MiniMax-M3` yang ditawarkan, karena lini M2.x sudah tidak dilayani.
- **Proses berpikir MiniMax dipisahkan dari jawaban.** Pada format OpenAI, thinking dikirim lewat `reasoning_split` agar muncul di `reasoning_content`, bukan sebagai tag `<think>` di dalam jawaban.

### Teknis

- `versionCode` 9, `versionName` 1.3.5.
- Test baru `UpdateManifestUrlsTest` (4 test) untuk penyusunan daftar URL manifest, plus test MiniMax untuk daftar model dan pemisahan thinking. `UpdateSimulationTest` tetap memverifikasi alur deteksi versi baru.

### Unduh

- APK: <https://github.com/rendihomeofficial-sudo/renata-claude/releases/download/v1.3.5/Renata-Claude-1.3.5.apk>

---

## 1.3.4 — 27 Oktober 2026

Fokus rilis ini: dukungan MiniMax yang jauh lebih lengkap dan akurat.

### Ditambahkan

- **Kontrol kedalaman berpikir (effort) untuk MiniMax.** Enam pilihan di Pengaturan: `Otomatis (max)`, `Rendah`, `Sedang`, `Tinggi`, `Sangat tinggi`, `Maksimal`. Nilainya dikirim sebagai `output_config.effort` (format Anthropic) atau `reasoning_effort` (format OpenAI), dan hanya berlaku untuk keluarga MiniMax-M3.1 — model lama mengabaikannya.
- **Prompt sistem bawaan yang lebih profesional.** Kalau pengguna belum menulis prompt sendiri, MiniMax memakai prompt default: jawab langsung di kalimat pertama, bedakan fakta dari dugaan, sertakan satuan dan asumsi, serta sebutkan sumber untuk data yang cepat berubah.
- **Panduan pemakaian skill (tool) di dalam prompt.** Aturan kapan memakai `get_datetime`, `calculate`, dan `fetch_url` ikut ditambahkan — tetapi hanya saat alatnya benar-benar dinyalakan, sehingga model tidak pernah meminta alat yang tidak tersedia.
- **Proses berpikir dikembalikan ke provider.** Isi `reasoning_content` (format OpenAI) dan blok `thinking` beserta `signature`-nya (format Anthropic) dikirim balik pada giliran berikutnya, jadi rantai berpikir tidak terpotong di tengah percakapan.
- **Pengaturan khusus MiniMax di layar Pengaturan.** Ada catatan bahwa MiniMax mengabaikan `Temperature` dan `Top P`, dan bahwa kedalaman berpikir diatur terpisah.

### Diperbaiki

- **Thinking block dikirim di posisi yang benar.** Blok `thinking` harus menjadi blok pertama dalam giliran assistant, kalau tidak permintaan ditolak provider.
- **Signature thinking disimpan dan dirangkai dengan benar.** Signature yang datang terpecah beberapa kali, atau diulang utuh oleh provider, digabung tanpa duplikasi.
- **Giliran dengan tool call sekarang membawa isi yang benar.** Setelah alat dijalankan, giliran berikutnya mengirim ulang jawaban, proses berpikir, dan hasil panggilannya — sebelumnya chat yang dirujuk bisa kehilangan isi.
- **Deskripsi alat dibuat lebih jelas.** Tiap alat menjelaskan kapan harus dipakai, ditambah `tool_choice: auto` agar provider bebas memilih.

### Teknis

- `versionCode` 8, `versionName` 1.3.4.
- Batas token untuk MiniMax dinaikkan otomatis menjadi 8192, atau 16384 pada kedalaman `high`/`xhigh`/`max`, karena token berpikir dihitung terhadap batas yang sama. Format OpenAI memakai `max_completion_tokens`.
- 16 test baru di `MiniMaxSupportTest` dan 4 test baru di `ProviderBodyTest`, mencakup parameter MiniMax, prompt, dan batas token.

### Unduh

- APK: <https://github.com/rendihomeofficial-sudo/renata-claude/releases/download/v1.3.4/Renata-Claude-1.3.4.apk>

---

## 1.3.3 — 26 Oktober 2026

- Alur rilis otomatis: setiap tag baru otomatis membuat GitHub Release dan memperbarui manifest pembaruan di branch `main`, sehingga aplikasi langsung mendeteksi versi terbaru saat dibuka.

## 1.3.2 — 26 Oktober 2026

- Tampilan baru khas Claude: kartu jawaban dengan sudut asimetris, indikator bintang berkedip saat model berpikir, kursor streaming, serta tampilan awal dan kotak pesan yang lebih rapi.

## 1.3.1 — 26 Oktober 2026

- Rilis pengujian alur pembaruan otomatis: pop-up pembaruan dan unduhan rilis terbaru.

## 1.3.0 — 25 Oktober 2026

- Penyedia siap pakai (Anthropic, MiniMax, OpenAI, OpenRouter, Groq, Together, Ollama, LM Studio), format API OpenAI, alat lokal (jam, kalkulator, baca halaman web), pencarian web sisi server dengan daftar sumber, kunci aplikasi dengan PIN, dan tampilan markdown/LaTeX yang lebih rapi.
