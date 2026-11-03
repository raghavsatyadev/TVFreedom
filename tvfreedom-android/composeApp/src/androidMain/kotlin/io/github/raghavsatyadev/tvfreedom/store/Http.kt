package io.github.raghavsatyadev.tvfreedom.store

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import java.io.IOException

/** One HTTP client for downloads (F-Droid, GitHub, images). Callers check the status themselves. */
internal val http: HttpClient by lazy {
    HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 60_000
        }
    }
}

/** The body of [url]; an [IOException] when the server does not answer 2xx. */
internal suspend fun httpBytes(url: String): ByteArray {
    val r = http.get(url)
    if (!r.status.isSuccess()) throw IOException("$url answered ${r.status.value}")
    return r.bodyAsBytes()
}
