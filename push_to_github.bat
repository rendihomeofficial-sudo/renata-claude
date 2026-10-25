@echo off
REM ==============================================================================
REM Script Otomatis (Windows CMD / Batch): Hubungkan ke GitHub & Setup Auto-Update
REM ==============================================================================
REM Cara pakai:
REM   push_to_github.bat <username-atau-org-anda> <nama-repo>
REM Contoh:
REM   push_to_github.bat rensrenata renata-claude
REM ==============================================================================

if "%~1"=="" goto usage
if "%~2"=="" goto usage

set OWNER=%~1
set REPO=%~2

set MANIFEST_RAW_URL=https://raw.githubusercontent.com/%OWNER%/%REPO%/main/version.json
set RELEASE_APK_URL=https://github.com/%OWNER%/%REPO%/releases/download/v1.3.0/Renata-Claude-1.3.0.apk

echo =================================================================
echo Menyiapkan konfigurasi update otomatis...
echo URL Manifest: %MANIFEST_RAW_URL%
echo URL APK: %RELEASE_APK_URL%
echo =================================================================

REM Inisialisasi Git jika belum ada
if not exist ".git" (
    echo Menginisialisasi Git repository...
    git init
    git branch -M main
)

REM Tambahkan remote origin
git remote remove origin 2>nul
git remote add origin https://github.com/%OWNER%/%REPO%.git

echo Menambahkan berkas ke git...
git add .
git commit -m "Rilis v1.3.0: multi-provider, alat lokal, kunci aplikasi, dan auto-update" 2>nul
git tag -f v1.3.0

echo =================================================================
echo Siap untuk diunggah ke GitHub!
echo Jalankan 2 perintah berikut:
echo.
echo    git push -u origin main --force
echo    git push origin v1.3.0 --force
echo.
echo =================================================================
goto end

:usage
echo =================================================================
echo Penggunaan: push_to_github.bat ^<github-username^> ^<nama-repo^>
echo Contoh:     push_to_github.bat rensrenata renata-claude
echo =================================================================

:end
