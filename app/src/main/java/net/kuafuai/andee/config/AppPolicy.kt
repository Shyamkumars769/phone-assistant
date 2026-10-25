package net.kuafuai.andee.config

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The user's allowlist. Edited only through [net.kuafuai.andee.ui.AllowlistActivity]
 * (device PIN/biometric required). No tool exposes it to the model.
 * Deny by default.
 */
object AppPolicy {
    /** Hardened build: inbound server, hub, peers, market and update check are off. */
    val hardened: Boolean get() = true

    /** True while a screen the model must never drive is visible. */
    @Volatile var sensitiveUiVisible: Boolean = false

    private const val PREFS = "andee_policy"
    private const val K_APPS = "allowed_apps"
    private const val K_CAPS = "allowed_caps"

    /** tool name -> capability key. Off unless the user switches it on. */
    val GATED: Map<String, String> = mapOf(
        "device.sms.read" to "sms_read",
        "device.sms.send" to "sms_send",
        "device.dial" to "dial",
        "device.call_log" to "call_log",
    )
    val CAP_LABELS: Map<String, String> = mapOf(
        "sms_read" to "Read SMS (OTPs arrive here)",
        "sms_send" to "Send SMS",
        "dial" to "Place calls / open dialer",
        "call_log" to "Read call log",
    )

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun allowedApps(c: Context): Set<String> =
        prefs(c).getStringSet(K_APPS, emptySet()).orEmpty().toSet()

    fun setAllowedApps(c: Context, s: Set<String>) {
        prefs(c).edit().putStringSet(K_APPS, s).apply()
    }

    fun capEnabled(c: Context, cap: String): Boolean =
        prefs(c).getStringSet(K_CAPS, emptySet()).orEmpty().contains(cap)

    fun setCaps(c: Context, s: Set<String>) {
        prefs(c).edit().putStringSet(K_CAPS, s).apply()
    }

    /** Needed for the phone to be drivable at all: home screen, keyboard, permission dialogs. */
    fun essential(c: Context): Set<String> {
        val out = mutableSetOf(
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller",
        )
        runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            c.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName?.let { out += it }
        }
        runCatching {
            Settings.Secure.getString(c.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')?.takeIf { it.isNotEmpty() }?.let { out += it }
        }
        return out
    }

    fun isPackageAllowed(c: Context, pkg: String): Boolean =
        pkg.isNotEmpty() && pkg != c.packageName &&
            (pkg in allowedApps(c) || pkg in essential(c))

    /** Implicit intents: allowed only if the app that would handle it is allowed. */
    fun intentAllowed(c: Context, intent: Intent): Boolean {
        val pkg = runCatching {
            c.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
        }.getOrNull() ?: return false
        return isPackageAllowed(c, pkg)
    }
}

/** Device PIN/pattern/biometric gate for the vault. 60 s grace after a success. */
object AuthGate {
    @Volatile private var lastOkMs = 0L
    @Volatile var result: Boolean = false
    @Volatile var latch: CountDownLatch? = null

    fun markOk() { lastOkMs = System.currentTimeMillis() }

    fun ensure(c: Context): Boolean {
        val km = c.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (!km.isDeviceSecure) return false
        if (System.currentTimeMillis() - lastOkMs < 60_000) return true
        val l = CountDownLatch(1)
        latch = l
        result = false
        c.startActivity(
            Intent(c, net.kuafuai.andee.ui.AuthGateActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        l.await(60, TimeUnit.SECONDS)
        return result
    }
}
