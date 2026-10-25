#!/bin/bash
# ==============================================================================
# Script Otomatis: Hubungkan ke GitHub & Setup Auto-Update Renata Claude
# ==============================================================================
# Cara pakai:
#   bash push_to_github.sh <username-atau-org-anda> <nama-repo>
# Contoh:
#   bash push_to_github.sh rensrenata renata-claude
# ==============================================================================

set -e

OWNER="$1"
REPO="$2"

if [ -z "$OWNER" ] || [ -z "$REPO" ]; then
    echo "================================================================="
    echo "Penggunaan: bash push_to_github.sh <github-username> <nama-repo>"
    echo "Contoh:     bash push_to_github.sh rensrenata renata-claude"
    echo "================================================================="
    exit 1
fi

MANIFEST_RAW_URL="https://raw.githubusercontent.com/${OWNER}/${REPO}/main/version.json"
RELEASE_APK_URL="https://github.com/${OWNER}/${REPO}/releases/download/v1.3.0/Renata-Claude-1.3.0.apk"

echo ">> Menyiapkan konfigurasi update otomatis..."
echo "   URL Manifest: ${MANIFEST_RAW_URL}"
echo "   URL Download APK: ${RELEASE_APK_URL}"

# 1. Update gradle.properties dengan URL Manifest yang tepat
sed -i "s|renata.updateManifestUrl=.*|renata.updateManifestUrl=${MANIFEST_RAW_URL}|g" gradle.properties

# 2. Update version.json dengan URL GitHub Anda
cat <<EOF > version.json
{
  "versionName": "1.3.0",
  "versionCode": 4,
  "downloadUrl": "${RELEASE_APK_URL}",
  "releaseNotes": "Penyedia siap pakai (Anthropic, MiniMax, OpenAI, OpenRouter, Groq, Together, Ollama, LM Studio), format API OpenAI, alat lokal (jam, kalkulator, baca halaman web), pencarian web sisi server dengan daftar sumber, kunci aplikasi dengan PIN, dan tampilan markdown/LaTeX yang lebih rapi.",
  "publishedAt": "2026-10-25"
}
EOF

# 3. Inisialisasi Git jika belum ada
if [ ! -d ".git" ]; then
    echo ">> Menginisialisasi Git repository..."
    git init
    git branch -M main
fi

# 4. Tambahkan Remote GitHub
REMOTE_URL="https://github.com/${OWNER}/${REPO}.git"
if git remote | grep -q "^origin$"; then
    echo ">> Memperbarui git remote origin ke ${REMOTE_URL}..."
    git remote set-url origin "${REMOTE_URL}"
else
    echo ">> Menambahkan git remote origin ${REMOTE_URL}..."
    git remote add origin "${REMOTE_URL}"
fi

# 5. Commit semua perubahan
echo ">> Menambahkan berkas ke git..."
git add .
git commit -m "Rilis v1.3.0: multi-provider, alat lokal, kunci aplikasi, dan auto-update" || true

# 6. Buat Git Tag untuk Rilis
git tag -f v1.3.0

echo "================================================================="
echo "Konfigurasi lokal selesai!"
echo "Untuk mengunggah (push) ke GitHub, jalankan perintah berikut:"
echo ""
echo "   git push -u origin main --force"
echo "   git push origin v1.3.0 --force"
echo ""
echo "Setelah push:"
echo "1. GitHub Actions (.github/workflows/release.yml) akan otomatis jalan"
echo "2. APK v1.3.0 akan otomatis diunggah ke GitHub Releases"
echo "3. version.json di-host di: ${MANIFEST_RAW_URL}"
echo "4. Aplikasi akan otomatis mendeteksi update & menampilkan popup dialog!"
echo "================================================================="
