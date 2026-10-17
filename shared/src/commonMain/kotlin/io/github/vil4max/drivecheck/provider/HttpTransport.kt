package io.github.vil4max.drivecheck.provider

import kotlin.time.Duration

data class HttpRequest(
    val url: String,
    val timeout: Duration,
    val bypassCache: Boolean,
    val headers: Map<String, String>,
)

// Header names are lower case, so lookups do not depend on how the server spells them.
class HttpResponse(val status: Int, val headers: Map<String, String>, val body: ByteArray)

fun interface HttpTransport {
    suspend fun execute(request: HttpRequest): HttpResponse
}

class TransportFailure(val kind: Kind, cause: Throwable? = null) :
    Exception("transport failure: $kind", cause) {
    enum class Kind { Timeout, ConnectionLost, CannotConnect, HostLookup, Other }

    val isTransient: Boolean get() = kind != Kind.Other
}

fun interface AlertsProvider {
    suspend fun fetchAlerts(): io.github.vil4max.drivecheck.domain.AlertsSnapshot
}

fun interface ProviderLog {
    fun info(message: String)
}
