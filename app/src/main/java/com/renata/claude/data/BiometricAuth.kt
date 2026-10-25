package com.renata.claude.data

import android.content.Context
import android.os.Build
import androidx.core.hardware.fingerprint.FingerprintManagerCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

/** Status ketersediaan perangkat keras dan data biometrik/sidik jari pada perangkat. */
object BiometricAuth {

    /**
     * Memeriksa apakah perangkat memiliki sensor sidik jari dan pengguna telah mendaftarkan sidik jari.
     */
    fun canAuthenticate(context: Context): Boolean = try {
        val compat = FingerprintManagerCompat.from(context)
        compat.isHardwareDetected && compat.hasEnrolledFingerprints()
    } catch (_: Exception) {
        false
    }

    /**
     * Menampilkan dialog sidik jari bawaan sistem.
     * Menggunakan Android BiometricPrompt standar (API 28+) dengan fallback FingerprintManagerCompat.
     */
    fun promptBiometric(
        activity: Context,
        title: String = "Buka Renata Claude",
        subtitle: String = "Sentuh sensor sidik jari Anda",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val prompt = android.hardware.biometrics.BiometricPrompt.Builder(activity)
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButton("Gunakan PIN", activity.mainExecutor) { _, _ ->
                        onError("Dibatalkan pengguna")
                    }
                    .build()

                val cancellationSignal = android.os.CancellationSignal()
                prompt.authenticate(
                    cancellationSignal,
                    activity.mainExecutor,
                    object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) {
                            super.onAuthenticationSucceeded(result)
                            onSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                            super.onAuthenticationError(errorCode, errString)
                            onError(errString?.toString() ?: "Autentikasi sidik jari gagal.")
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            onError("Sidik jari tidak dikenali.")
                        }
                    }
                )
            } catch (e: Exception) {
                onError(e.message ?: "Tidak dapat memulai pemindai biometrik.")
            }
        } else {
            onError("Biometrik membutuhkan Android 9 ke atas.")
        }
    }
}
