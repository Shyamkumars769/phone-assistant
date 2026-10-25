package net.kuafuai.andee.ui

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import net.kuafuai.andee.config.AppPolicy

/**
 * The allowlist editor. Opens only after the device PIN/pattern/biometric.
 * Hidden from accessibility services and FLAG_SECURE, so the model can neither
 * read nor screenshot it; PolicyGuard also refuses screen.* while it is visible.
 */
class AllowlistActivity : Activity() {
    private var built = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.decorView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        AppPolicy.sensitiveUiVisible = true
        val km = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isDeviceSecure) {
            Toast.makeText(this, "Set a screen lock (PIN, pattern or password) first.", Toast.LENGTH_LONG).show()
            finish(); return
        }
        val i = km.createConfirmDeviceCredentialIntent("Andee allowlist", "Confirm it's you to change what Andee may use")
        if (i == null) { finish(); return }
        @Suppress("DEPRECATION")
        startActivityForResult(i, 1)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) build() else finish()
    }

    override fun onResume() { super.onResume(); AppPolicy.sensitiveUiVisible = true }
    override fun onPause() { super.onPause(); if (built) AppPolicy.sensitiveUiVisible = false }
    override fun onStop() { super.onStop(); AppPolicy.sensitiveUiVisible = false; if (built) finish() }

    private fun build() {
        built = true
        val pm = packageManager
        val essential = AppPolicy.essential(this)
        val allowed = AppPolicy.allowedApps(this)

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 48) }
        col.addView(TextView(this).apply { text = "Phone capabilities (off by default)"; textSize = 18f })
        val capSwitches = LinkedHashMap<String, Switch>()
        for ((key, label) in AppPolicy.CAP_LABELS) {
            val sw = Switch(this).apply { text = label; isChecked = AppPolicy.capEnabled(this@AllowlistActivity, key) }
            capSwitches[key] = sw; col.addView(sw)
        }
        col.addView(TextView(this).apply { text = "\nApps Andee may use"; textSize = 18f })

        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName to runCatching { it.loadLabel(pm).toString() }.getOrDefault(it.activityInfo.packageName) }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        val boxes = LinkedHashMap<String, CheckBox>()
        for ((pkg, label) in apps) {
            val always = pkg in essential
            val cb = CheckBox(this).apply {
                text = if (always) "$label  (always allowed)\n$pkg" else "$label\n$pkg"
                isChecked = always || pkg in allowed
                isEnabled = !always
            }
            boxes[pkg] = cb; col.addView(cb)
        }
        col.addView(Button(this).apply {
            text = "Save"
            setOnClickListener {
                AppPolicy.setAllowedApps(this@AllowlistActivity, boxes.filter { it.value.isChecked && it.key !in essential }.keys)
                AppPolicy.setCaps(this@AllowlistActivity, capSwitches.filter { it.value.isChecked }.keys)
                Toast.makeText(this@AllowlistActivity, "Saved", Toast.LENGTH_SHORT).show()
                finish()
            }
        })
        setContentView(ScrollView(this).apply { addView(col) })
    }
}
