package io.github.vil4max.drivecheck.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException

class UrlConnectionTransport : HttpTransport {
    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(Dispatchers.IO) {
        val connection = URI(request.url).toURL().openConnection() as HttpURLConnection
        try {
            configure(connection, request)
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val body = stream?.use { it.readBytes() } ?: ByteArray(0)
            val headers = connection.headerFields
                .filterKeys { it != null }
                .mapKeys { it.key.lowercase() }
                .mapValues { it.value.firstOrNull().orEmpty() }
            HttpResponse(status, headers, body)
        } catch (error: IOException) {
            throw TransportFailure(classify(error), error)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        // REQ-REFRESH-003's four transient kinds, in Java terms. ConnectException and
        // NoRouteToHostException are SocketExceptions, so they are matched before the general case.
        // Android throws UnknownHostException for a device with no network too.
        fun classify(error: IOException): TransportFailure.Kind = when (error) {
            is SocketTimeoutException -> TransportFailure.Kind.Timeout
            is ConnectException, is NoRouteToHostException -> TransportFailure.Kind.CannotConnect
            is UnknownHostException -> TransportFailure.Kind.HostLookup
            is SocketException, is EOFException -> TransportFailure.Kind.ConnectionLost
            else -> TransportFailure.Kind.Other
        }

        fun configure(connection: HttpURLConnection, request: HttpRequest) {
            val timeoutMillis = request.timeout.inWholeMilliseconds.toInt()
            connection.requestMethod = "GET"
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.instanceFollowRedirects = true
            if (request.bypassCache) {
                connection.useCaches = false
                connection.setRequestProperty("Cache-Control", "no-cache")
            }
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
        }
    }
}
