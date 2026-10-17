@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.vil4max.drivecheck.store

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import io.github.vil4max.drivecheck.provider.AlertsProvider
import io.github.vil4max.drivecheck.provider.TestClock
import io.github.vil4max.drivecheck.status.StatusController
import io.github.vil4max.drivecheck.status.StatusState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class StatusStoreTest {
    private val snapshot = AlertsSnapshot(
        source = "test",
        serverCachedAt = Instant.parse("2026-10-17T09:59:30Z"),
        fetchedAt = Instant.parse("2026-10-17T10:00:00Z"),
        statuses = mapOf(AlertRegion.KyivCity to AlertStatus.Alarm, AlertRegion.Lviv to AlertStatus.Quiet),
    )

    @Test
    fun REQ_ANDROID_006_the_snapshot_round_trips_through_the_codec() {
        assertEquals(snapshot, SnapshotCodec.decode(SnapshotCodec.encode(snapshot)))
        assertEquals(
            snapshot.copy(serverCachedAt = null),
            SnapshotCodec.decode(SnapshotCodec.encode(snapshot.copy(serverCachedAt = null))),
        )
    }

    @Test
    fun REQ_ANDROID_006_a_corrupt_store_reads_as_empty() {
        assertNull(SnapshotCodec.decode("{not json"))
        assertNull(SnapshotCodec.decode("""{"source":"x"}"""))
        assertNull(SnapshotCodec.decode(null))
    }

    @Test
    fun REQ_REGION_008_the_last_region_stays_and_kyiv_city_is_the_default() = runTest {
        val empty = StatusController(AlertsProvider { snapshot }, TestClock(), backgroundScope, store = InMemoryStatusStore())
        val stored = InMemoryStatusStore().apply { saveRegion(AlertRegion.Lviv) }

        assertEquals(AlertRegion.KyivCity, empty.region.value)
        assertEquals(AlertRegion.Lviv, StatusController(AlertsProvider { snapshot }, TestClock(), backgroundScope, store = stored).region.value)
    }

    @Test
    fun REQ_REGION_006_a_committed_region_shows_from_the_snapshot_at_once_and_is_saved() = runTest {
        val store = InMemoryStatusStore().apply { saveSnapshot(snapshot) }
        val controller = StatusController(AlertsProvider { snapshot }, TestClock(), backgroundScope, store = store)

        controller.selectRegion(AlertRegion.Lviv)

        assertEquals(StatusState.Quiet(snapshot.checkedAt), controller.state.value)
        assertEquals(AlertRegion.Lviv, store.loadRegion())
    }

    @Test
    fun REQ_ANDROID_006_a_stored_snapshot_is_shown_at_start_and_a_fetch_is_saved() = runTest {
        val store = InMemoryStatusStore().apply { saveSnapshot(snapshot) }
        val fresh = snapshot.copy(statuses = mapOf(AlertRegion.KyivCity to AlertStatus.Quiet))
        val controller = StatusController(AlertsProvider { fresh }, TestClock(), backgroundScope, store = store)

        assertEquals(StatusState.Alarm(snapshot.checkedAt), controller.state.value)

        controller.onSurfaceStarted()
        runCurrent()

        assertEquals(fresh, store.loadSnapshot())
    }
}
