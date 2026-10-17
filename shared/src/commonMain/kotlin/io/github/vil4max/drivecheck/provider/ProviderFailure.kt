package io.github.vil4max.drivecheck.provider

import kotlin.time.Instant

sealed class ProviderFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class HttpStatus(val code: Int, bodyPrefix: String) : ProviderFailure("HTTP $code: $bodyPrefix")

    class NotJson(contentType: String, bodyPrefix: String) :
        ProviderFailure("content type $contentType: $bodyPrefix")

    class Undecodable(bodyPrefix: String, cause: Throwable) : ProviderFailure("undecodable body: $bodyPrefix", cause)

    class RateLimited(val until: Instant) : ProviderFailure("rate limited until $until")
}
