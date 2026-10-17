package io.github.vil4max.drivecheck.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
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
            val body = stream?.use { readCapped(it) } ?: ByteArray(0)
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
        // The feed is a few kilobytes. A captive portal can answer 200 with a large page, and
        // reading it whole could end in OutOfMemoryError, a crash instead of "Unavailable".
        const val MAX_BODY_BYTES = 256 * 1024

        // A manual loop: InputStream.readNBytes needs API 33, and minSdk is 26.
        fun readCapped(stream: InputStream): ByteArray {
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8 * 1024)
            while (buffer.size() <= MAX_BODY_BYTES) {
                val read = stream.read(chunk)
                if (read < 0) break
                buffer.write(chunk, 0, read)
            }
            val bytes = buffer.toByteArray()
            if (bytes.size > MAX_BODY_BYTES) {
                throw ProviderFailure.Undecodable(
                    bytes.copyOf(240).decodeToString(),
                    IOException("response body exceeds $MAX_BODY_BYTES bytes"),
                )
            }
            return bytes
        }

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
