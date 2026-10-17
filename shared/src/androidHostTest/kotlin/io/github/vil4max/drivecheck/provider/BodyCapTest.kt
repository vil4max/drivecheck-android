package io.github.vil4max.drivecheck.provider

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class BodyCapTest {
    @Test
    fun REQ_PROVIDER_001_a_body_over_the_cap_is_undecodable_not_a_crash() {
        val cap = UrlConnectionTransport.MAX_BODY_BYTES
        val atCap = ByteArray(cap) { 'a'.code.toByte() }

        assertContentEquals(atCap, UrlConnectionTransport.readCapped(ByteArrayInputStream(atCap)))
        assertFailsWith<ProviderFailure.Undecodable> {
            UrlConnectionTransport.readCapped(ByteArrayInputStream(ByteArray(cap + 1)))
        }
    }
}
