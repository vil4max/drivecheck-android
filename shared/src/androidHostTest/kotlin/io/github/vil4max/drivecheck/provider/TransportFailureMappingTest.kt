package io.github.vil4max.drivecheck.provider

import io.github.vil4max.drivecheck.provider.TransportFailure.Kind
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class TransportFailureMappingTest {
    @Test
    fun REQ_REFRESH_003_java_io_failures_map_to_transient_kinds() {
        val expected = mapOf(
            SocketTimeoutException("timeout") to Kind.Timeout,
            ConnectException("refused") to Kind.CannotConnect,
            NoRouteToHostException("no route") to Kind.CannotConnect,
            UnknownHostException("ubilling.net.ua") to Kind.HostLookup,
            SocketException("Connection reset") to Kind.ConnectionLost,
            EOFException("unexpected end of stream") to Kind.ConnectionLost,
        )
        expected.forEach { (error, kind) ->
            assertEquals(kind, UrlConnectionTransport.classify(error), error.javaClass.simpleName)
        }
    }

    @Test
    fun REQ_REFRESH_003_tls_and_unknown_failures_are_not_transient() {
        listOf(SSLHandshakeException("bad certificate"), IOException("other")).forEach { error ->
            val kind = UrlConnectionTransport.classify(error)
            assertEquals(Kind.Other, kind, error.javaClass.simpleName)
            assertFalse(TransportFailure(kind).isTransient)
        }
    }
}
