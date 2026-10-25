package net.kuafuai.andee.ui

import android.app.Activity
import android.app.KeyguardManager
import android.os.Bundle
import android.view.WindowManager
import net.kuafuai.andee.config.AppPolicy
import net.kuafuai.andee.config.AuthGate

/** Shows the system PIN/biometric prompt for the vault, then reports back to [AuthGate]. */
class AuthGateActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        val i = km.createConfirmDeviceCredentialIntent("Andee vault", "Confirm it's you to let Andee use a saved password")
        if (i == null) { done(false); return }
        AppPolicy.sensitiveUiVisible = true
        @Suppress("DEPRECATION")
        startActivityForResult(i, 7)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        done(requestCode == 7 && resultCode == RESULT_OK)
    }

    private fun done(ok: Boolean) {
        AppPolicy.sensitiveUiVisible = false
        if (ok) AuthGate.markOk()
        AuthGate.result = ok
        AuthGate.latch?.countDown()
        finish()
    }
}
