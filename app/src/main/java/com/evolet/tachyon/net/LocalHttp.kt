package com.evolet.tachyon.net

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.IOException
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * The only HTTP client in the app. It refuses every host except the on-device
 * Tier 2 servers (CLAUDE.md rule 1). No proxy, so traffic can't be routed off the phone.
 */
object LocalHttp {
    private val ALLOWED_HOSTS = setOf("127.0.0.1", "localhost")

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .addInterceptor { chain ->
                val host = chain.request().url.host
                if (host !in ALLOWED_HOSTS) throw IOException("Blocked: $host is not on this device")
                chain.proceed(chain.request())
            }
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true) // a restarted Termux server leaves stale pooled connections
            .build()
    }
}
