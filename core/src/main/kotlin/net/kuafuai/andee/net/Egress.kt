package net.kuafuai.andee.net

import okhttp3.Interceptor
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

/**
 * Outbound allowlist for OkHttp clients. Install with
 * `addNetworkInterceptor(Egress.guard(host))`: a *network* interceptor runs on
 * every hop, so a redirect to another host is refused too. https/wss only.
 */
object Egress {
    fun guard(vararg hosts: String): Interceptor {
        val allowed = hosts.map { it.lowercase() }.filter { it.isNotEmpty() }.toSet()
        return Interceptor { chain ->
            val url = chain.request().url
            if (!url.isHttps || url.host.lowercase() !in allowed) {
                throw IOException("blocked by egress policy: ${url.scheme}://${url.host}")
            }
            chain.proceed(chain.request())
        }
    }

    fun hostOf(url: String): String = url.toHttpUrlOrNull()?.host.orEmpty()
}
