# Renata Claude

Klona fungsional dari aplikasi **Claude** (Android) yang dibangun dari nol dengan satu perbedaan
pokok: **tidak ada login, tidak ada akun, tidak ada kuota bawaan**. Semua provider, key, model, dan
server ditentukan sendiri oleh pengguna lewat halaman **Settings**.

> APK asli (`Claude.apk`) memakai API internal Anthropic (`StreamTimelineRequest` protobuf/Connect ke
> `a-api.anthropic.com`) yang tidak bisa diarahkan ke server pihak ketiga. Karena itu aplikasi ini
> diimplementasikan ulang sebagai aplikasi native Jetpack Compose yang mendukung dua format wire
> standar: **Anthropic Messages API** (`POST {base_url}/v1/messages`) dan **OpenAI Chat Completions
> API** (`POST {base_url}/chat/completions`), sehingga base URL, format API, dan API key bisa diganti bebas.

## Fitur

- **Tanpa login** — aplikasi langsung terbuka ke layar chat.
- **Dukungan multi-provider lengkap** — preset siap pakai satu ketukan untuk:
  - **Anthropic (Claude)**: sonnet-4-5, opus-4-1, haiku-4-5, 3-5-sonnet-latest
  - **MiniMax**: format Anthropic (`api.minimax.io/anthropic`) dan format OpenAI (`api.minimax.io/v1`)
  - **OpenAI**: gpt-4.1, gpt-4.1-mini, gpt-4o, gpt-4o-mini, o4-mini
  - **OpenRouter**: claude-sonnet-4.5, gemini-2.5-pro, llama-3.3-70b-instruct
  - **Groq**: llama-3.3-70b-versatile, llama-3.1-8b-instant
  - **Together AI**: Llama-3.3-70B-Instruct-Turbo
  - **Ollama (lokal)**: llama3.2, qwen2.5-coder (tanpa kunci)
  - **LM Studio (lokal)**: model lokal di PC/jaringan lokal (tanpa kunci)
  - **Kustom**: URL, format wire (Anthropic/OpenAI), dan gaya auth bebas
- **Dua format wire API**:
  - `anthropic` (Messages API dengan SSE content blocks, thinking block, dan tools)
  - `openai` (Chat Completions API dengan SSE deltas, reasoning content, dan function calling)
- **Gaya autentikasi fleksibel**: `x-api-key`, `Authorization: Bearer`, atau keduanya sekaligus.
- **Base URL kustom** — pakai proxy, gateway, atau self-hosted server Anda sendiri.
- **API key kustom** — disimpan lokal di perangkat (SharedPreferences), tidak pernah dikirim ke mana pun
  selain base URL yang Anda tentukan.
- **Tanpa batas** — tidak ada penghitung kuota, tidak ada rate limit dari sisi aplikasi. Batas apa pun
  sepenuhnya berasal dari provider yang Anda pakai.
- **Header tambahan** — JSON bebas untuk gateway kustom.
- **Alat lokal (Device Tools)** — model dapat memanggil alat yang dieksekusi langsung di perangkat:
  - `get_datetime`: tanggal, waktu, dan zona waktu lokal/IANA.
  - `calculate`: parser ekspresi aritmetika murni tanpa eksekusi kode berbahaya.
  - `fetch_url`: mengambil teks halaman web langsung untuk dijadikan bacaan model.
  - Multi-turn auto-loop hingga 5 putaran alat per giliran.
- **Pencarian web sisi server (Server Research / Web Search)** — mengaktifkan tool pencarian web
  dari penyedia (Anthropic `web_search_20250305`) dan menampilkan daftar sumber/kutipan (*citations*)
  yang dapat diklik langsung di bawah bubble jawaban.
- **Kunci aplikasi (App Lock dengan PIN)**:
  - Lindungi riwayat percakapan dengan PIN 4-8 digit.
  - PIN di-hash menggunakan PBKDF2 (120.000 iterasi) dengan salt acak 16-byte — PIN asli tidak pernah tersimpan.
  - Waktu tenggang (*grace period*) 30 detik saat aplikasi ditinggalkan sebelum mengunci otomatis.
  - Layar kunci mengisolasi konten percakapan dari render tree Compose saat terkunci.
- **Renderer Markdown & LaTeX luring**:
  - Paragraf, daftar bertingkat, kutipan (*blockquote*), blok kode bersintaks.
  - Format inline: bold, italic, inline code, tautan, strikethrough.
  - Rumus matematika LaTeX inline (`$...$`) dan block (`$$...$$`) diubah menjadi teks Unicode yang
    terbaca tanpa ketergantungan runtime web/KaTeX.
- **Streaming SSE** dengan indikator "Thinking" (extended thinking) bila diaktifkan.
- **Riwayat chat lokal** — disimpan sebagai satu file JSON per percakapan di penyimpanan privat app.
  Bisa cari teks/judul, rename, sematkan (*pin*), hapus, dan hapus semua.
- **Lampiran** — gambar (PNG/JPG/GIF/WebP), PDF, dan file teks/kode dikirim sebagai base64.
- **Pengenalan suara bawaan Android** — memakai `SpeechRecognizer` bawaan perangkat, tanpa
  ketergantungan library tambahan. Mikrofon butuh izin `RECORD_AUDIO`.
- **Live voice (ngobrol seperti teman curhat)** — sesi percakapan dua arah langsung: dengar →
  jawab → baca per kalimat → dengar kembali dengan persona teman curhat yang hangat.
- **Parameter sampling** — max tokens, temperature, top P, system prompt.
- **Pembaruan dalam aplikasi** — memeriksa `version.json`, mengunduh APK baru, lalu membuka installer sistem.
- **Tema** system / light / dark.

## Struktur

```
app/src/main/java/com/renata/claude/
├── MainActivity.kt              # entry point Compose & inisialisasi store
├── data/
│   ├── Models.kt                # Message / Attachment / Chat / AppSettings / ToolCall / Citation
│   ├── Providers.kt             # Preset penyedia (Anthropic, MiniMax, OpenAI, OpenRouter, dll)
│   ├── Tools.kt                 # Model ToolCall, ToolResult, ToolSpec, Citation
│   ├── ToolRegistry.kt          # Eksekusi alat lokal (jam, kalkulator, fetch URL) & parser math
│   ├── SettingsStore.kt         # Persistensi preferensi (SharedPreferences + Flow)
│   ├── ChatStore.kt             # Persistensi percakapan (JSON per chat)
│   ├── LockPrefs.kt             # Penyimpanan salt/hash PIN & verifikasi PBKDF2
│   ├── AiClient.kt              # Facade router provider (Anthropic vs OpenAI)
│   ├── AnthropicClient.kt       # Klien HTTP Messages API + parser SSE Anthropic
│   └── OpenAiClient.kt          # Klien HTTP Chat Completions + parser SSE OpenAI
├── text/
│   ├── InlineParser.kt          # Parser inline style: bold, italic, code, link, strike, math
│   ├── Latex.kt                 # Konverter LaTeX ke Unicode murni (akar, pecahan, simbol, skrip)
│   └── MarkdownParser.kt        # Parser blok: heading, list, blockquote, code block, formula
├── ui/
│   ├── AppRoot.kt               # Drawer riwayat, navigasi, dialog, dan gerbang kunci aplikasi
│   ├── AppViewModel.kt          # State ViewModel, orchestrator multi-turn tools & streaming
│   ├── ChatScreen.kt            # Layar chat, composer, daftar lampiran
│   ├── MessageBubble.kt         # Bubble chat, markdown viewer, citations, tool calls badge
│   ├── MarkdownText.kt          # Komponen Compose perender markdown blok + inline
│   ├── CitationList.kt          # Komponen chip sumber web yang bisa diklik
│   ├── LockScreen.kt            # Layar masukan PIN dan pengaturan kunci
│   ├── AttachmentFactory.kt     # Resolusi URI berkas -> base64/teks
│   ├── FileExtractor.kt         # Ekstraktor teks dari PDF & berkas teks
│   ├── LiveVoiceScreen.kt       # Layar percakapan suara live
│   ├── SettingsScreen.kt        # Halaman konfigurasi penyedia, model, PIN, dan opsi
│   ├── SplashScreen.kt          # Splash screen animasi awal
│   ├── UpdateDialog.kt          # Dialog pembaruan & unduhan APK
│   └── theme/Theme.kt           # Palet Material 3 & tema warna
├── update/
│   ├── UpdateManifest.kt        # Parser & validator version.json
│   ├── UpdateChecker.kt         # Pengambil manifest & pengunduh APK
│   ├── UpdatePrefs.kt           # Jadwal pengecekan otomatis (1x sehari)
│   └── ApkInstaller.kt          # FileProvider + intent installer Android
└── voice/
    ├── VoiceInput.kt            # Normalisasi & penggabungan teks transkripsi
    ├── VoiceRecognizer.kt       # Wrapper SpeechRecognizer bawaan perangkat
    ├── VoiceScript.kt           # Pembersih markdown & pemecah kalimat TTS
    ├── Speaker.kt               # Wrapper TextToSpeech Android
    ├── LiveVoiceController.kt   # Controller siklus dengar -> streaming -> bicara
    └── LiveVoiceSink.kt         # Kontrak stream jawaban suara
```

## Cara Pakai

1. Buka **Settings** (ikon roda gigi di kanan atas, atau dari drawer navigasi).
2. Pilih salah satu **Preset Penyedia** (misalnya Anthropic, OpenAI, Groq, OpenRouter, MiniMax, atau Kustom).
3. Base URL, format API, dan model default akan terisi otomatis.
4. Masukkan **Kunci API** Anda.
5. Tekan tombol **Tes koneksi** untuk memverifikasi jaringan dan kunci.
6. (Opsional) Aktifkan **Alat lokal (Tools)**, **Pencarian web (Research)**, atau **Kunci aplikasi (PIN)**.
7. Kembali ke layar chat dan mulai percakapan.

## Build

Toolchain yang dipakai proyek ini: AGP 8.4.0, Kotlin 1.9.24, Gradle 8.9, JDK 17, compileSdk 34,
minSdk 24.

```bash
# Unit test JVM
gradle :app:testDebugUnitTest

# Debug build
gradle :app:assembleDebug

# Rilis signed
gradle :app:assembleRelease
```

- APK debug: `app/build/outputs/apk/debug/app-debug.apk`
- APK rilis: `app/build/outputs/apk/release/app-release.apk`
- APK rilis siap pasang: `dist/Renata-Claude-1.3.0.apk`

### Keystore & Signature

Signing config rilis memakai keystore `keystore/renata-claude.jks` (alias `renata`).

```
package        : com.renata.claude   versionCode 4   versionName 1.3.0
minSdk / target: 24 / 34
label          : Renata Claude
signature      : v2 (RSA 2048, CN=Renata Claude), 1 signer
sha256         : c21208f46feb4e835c599805373a5473cbd3de9d804e85aac6ad70116abdb770
```

APK ini ditandatangani dengan keystore yang sama persis dengan versi sebelumnya, sehingga
bisa langsung diperbarui di atas versi 1.0.0, 1.1.0, maupun 1.2.0 tanpa kehilangan data.

## Verifikasi Pengujian (Unit Tests)

Seluruh suite unit test dieksekusi di atas JVM murni tanpa emulater:

```
Total unit tests: 79 passed, 0 skipped, 0 failed, 0 errors
```

Daftar kelas pengujian:
1. `AnthropicClientTest`: Wire format Messages API, resolusi endpoint, headers, SSE stream, 401 error mapping.
2. `ProviderBodyTest`: Validasi JSON payload untuk Anthropic dan OpenAI wire format, tools JSON schema, citations.
3. `ToolRegistryTest`: Eksekusi get_datetime, calculate (operator aritmetika, kurung, fungsi matematika), fetch_url parser HTML ke teks.
4. `PinHasherTest`: PBKDF2 hashing, salt generation, constant-time comparison, penolakan input invalid.
5. `InlineParserTest`: Parsing markdown inline (bold, italic, inline code, link, strikethrough, math span).
6. `LatexTest`: Konversi rumus LaTeX ke Unicode (pecahan, akar kuadrat, sub/superscript, simbol Yunani, blackboard bold).
7. `MarkdownParserTest`: Parsing blok markdown (heading, list, blockquote, fenced code block, block formula).
8. `UpdateManifestTest`: Validasi format manifest `version.json`, penolakan URL non-HTTP/HTTPS, perbandingan versionCode.
9. `VoiceInputTest`: Normalisasi kandidat speech recognition, penggabungan partial vs final text.
10. `VoiceScriptTest`: Pembersihan format markdown/kode untuk TTS dan pemotongan per kalimat alami.
