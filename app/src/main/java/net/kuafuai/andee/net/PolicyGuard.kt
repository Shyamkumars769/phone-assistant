package net.kuafuai.andee.net

import android.content.Context
import net.kuafuai.andee.config.AppPolicy
import net.kuafuai.andee.config.AuthGate
import net.kuafuai.andee.screen.ScreenController

/** Mechanical enforcement of the user's allowlist. Called first in [CommandDispatcher.dispatch]. */
object PolicyGuard {
    private val SCREEN_FREE = setOf("screen.global", "screen.wait")

    fun check(ctx: Context, method: String, screen: ScreenController?) {
        AppPolicy.GATED[method]?.let { cap ->
            if (!AppPolicy.capEnabled(ctx, cap)) {
                throw IllegalStateException(
                    "$method is switched off in the user's allowlist settings. Tell the user; do not look for another way.",
                )
            }
        }
        if (method.startsWith("device.vault.") && !AuthGate.ensure(ctx)) {
            throw IllegalStateException(
                "The vault needs the user's device PIN or biometric and it was not confirmed (or no screen lock is set): $method.",
            )
        }
        if (method.startsWith("screen.") && method !in SCREEN_FREE) {
            if (AppPolicy.sensitiveUiVisible) {
                throw IllegalStateException("A protected settings screen is open; the screen cannot be driven now: $method.")
            }
            val pkgs = screen?.visibleAppPackages()
                ?: throw IllegalStateException("Cannot confirm which app is in front, so $method is refused. Try again, or go Home.")
            val bad = pkgs.firstOrNull { it != ctx.packageName && !AppPolicy.isPackageAllowed(ctx, it) }
            if (bad != null) {
                throw IllegalStateException(
                    "$method refused: '$bad' is in front and is not on the user's allowlist. Use screen.global home and tell the user.",
                )
            }
        }
    }
}
