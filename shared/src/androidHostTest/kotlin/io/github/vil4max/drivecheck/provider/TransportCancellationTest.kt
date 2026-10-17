package io.github.vil4max.drivecheck.provider

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

// The background bound (8 s) is real only if cancelling a request frees the blocking read; a
// receiver otherwise waits out the 15 s socket timeout and its retry (REQ-REFRESH-008).
class TransportCancellationTest {
    @Test
    fun REQ_REFRESH_008_a_cancelled_request_releases_a_silent_server_at_once() {
        ServerSocket(0).use { server ->
            val accepted = mutableListOf<java.net.Socket>()
            thread(isDaemon = true) { runCatching { while (true) accepted += server.accept() } }
            val request = HttpRequest("http://127.0.0.1:${server.localPort}/", timeout = 15.seconds, bypassCache = true, headers = emptyMap())
            val started = TimeSource.Monotonic.markNow()

            runBlocking {
                assertFailsWith<TimeoutCancellationException> {
                    withTimeout(300.milliseconds) { UrlConnectionTransport().execute(request) }
                }
            }

            assertTrue(started.elapsedNow() < 3.seconds, "took ${started.elapsedNow()}")
            accepted.forEach { it.close() }
        }
    }
}
