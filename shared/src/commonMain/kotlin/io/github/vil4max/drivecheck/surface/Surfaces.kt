package io.github.vil4max.drivecheck.surface

import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.domain.AlertsSnapshot
import io.github.vil4max.drivecheck.domain.NearbyRegionPolicy
import io.github.vil4max.drivecheck.provider.AlertsProvider
import io.github.vil4max.drivecheck.provider.ProviderFailure
import io.github.vil4max.drivecheck.status.DisplayPhase
import io.github.vil4max.drivecheck.store.StatusStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

enum class FreshnessTier { Fresh, Aging, Expired }

// What the home-screen widget shows (REQ-REFRESH-009, REQ-SURF-003, REQ-SURF-010).
sealed interface WidgetPresentation {
    data object Missing : WidgetPresentation

    data class Shown(
        val region: AlertRegion,
        val phase: DisplayPhase,
        val tier: FreshnessTier,
        val checkedAt: Instant,
        val lastKnown: AlertStatus?,
    ) : WidgetPresentation
}

val WidgetPresentation.tier: FreshnessTier? get() = (this as? WidgetPresentation.Shown)?.tier
val WidgetPresentation.phase: DisplayPhase get() = (this as? WidgetPresentation.Shown)?.phase ?: DisplayPhase.Stale
val WidgetPresentation.showsWarning: Boolean get() = tier != null && tier != FreshnessTier.Fresh
val WidgetPresentation.asksToOpenApp: Boolean get() = tier == FreshnessTier.Expired

object WidgetPresenter {
    private val AGING = 3.minutes
    private val EXPIRED = 10.minutes

    fun present(snapshot: AlertsSnapshot?, region: AlertRegion, now: Instant): WidgetPresentation {
        if (snapshot == null) return WidgetPresentation.Missing
        val age = now - snapshot.checkedAt
        val tier = when {
            age < AGING -> FreshnessTier.Fresh
            age < EXPIRED -> FreshnessTier.Aging
            else -> FreshnessTier.Expired
        }
        val status = snapshot.status(region)
        val alarms = snapshot.statuses.filterValues { it == AlertStatus.Alarm }.keys
        // A known alarm stays red at any age; old quiet data is grey; yellow only on fresh data.
        val phase = when {
            status == AlertStatus.Alarm -> DisplayPhase.Alarm
            status == null -> DisplayPhase.RegionUnavailable
            tier != FreshnessTier.Fresh -> DisplayPhase.Stale
            NearbyRegionPolicy.isSurrounded(region, alarms) -> DisplayPhase.StayAlert
            else -> DisplayPhase.NoAlert
        }
        return WidgetPresentation.Shown(region, phase, tier, snapshot.checkedAt, status)
    }
}

enum class TileState { Active, Inactive, Unavailable }

data class TileLabel(val phase: DisplayPhase, val state: TileState)

// The quick-settings tile (REQ-ANDROID-007): Android draws its colours, so the traffic light rides
// on the active state and the label.
object TilePresenter {
    fun present(widget: WidgetPresentation): TileLabel = when (widget) {
        WidgetPresentation.Missing -> TileLabel(DisplayPhase.Stale, TileState.Unavailable)
        is WidgetPresentation.Shown -> TileLabel(
            widget.phase,
            if (widget.phase == DisplayPhase.Alarm || widget.phase == DisplayPhase.StayAlert) TileState.Active else TileState.Inactive,
        )
    }
}

enum class NotificationAction { Post, Update, End, None }

// The ongoing notification follows confirmed answers only (REQ-SURF-009): a failed, stale or
// unknown refresh never ends it, and nothing is posted without an alarm.
object AlertNotificationPolicy {
    private val STALE_AFTER = 15.minutes

    fun decide(isPosted: Boolean, status: AlertStatus?, confirmed: Boolean, allowed: Boolean): NotificationAction = when {
        !allowed -> if (isPosted) NotificationAction.End else NotificationAction.None
        !confirmed || status == null -> NotificationAction.None
        status == AlertStatus.Alarm -> if (isPosted) NotificationAction.Update else NotificationAction.Post
        isPosted -> NotificationAction.End
        else -> NotificationAction.None
    }

    fun isStale(checkedAt: Instant, now: Instant): Boolean = now - checkedAt > STALE_AFTER
}

// The moments a stored snapshot changes how the surfaces look, so a closed app can redraw them
// without a fetch (REQ-REFRESH-009, REQ-SURF-003).
object SurfaceSchedule {
    private val BOUNDARIES = listOf(3.minutes, 10.minutes, 15.minutes + 1.seconds)

    fun nextRedraw(checkedAt: Instant, now: Instant): Instant? =
        BOUNDARIES.map { checkedAt + it }.firstOrNull { it > now }
}

enum class BackgroundOutcome { Fetched, Held, Failed }

// One fetch outside the app: a widget update or a notification action (REQ-REFRESH-008). A
// scheduled update honours the stored rate-limit window; a success is saved for the app; a failure
// keeps the last known good snapshot. Bounded, because a broadcast receiver has little time.
class BackgroundRefresh(
    private val provider: AlertsProvider,
    private val store: StatusStore,
    private val clock: Clock,
) {
    suspend fun run(isScheduled: Boolean): BackgroundOutcome {
        val now = clock.now()
        val window = store.loadRateLimitedUntil()
        if (isScheduled && window != null && now < window) return BackgroundOutcome.Held
        return try {
            val snapshot = withTimeout(BOUND) { provider.fetchAlerts() }
            store.saveSnapshot(snapshot)
            BackgroundOutcome.Fetched
        } catch (timeout: TimeoutCancellationException) {
            BackgroundOutcome.Failed
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (limited: ProviderFailure.RateLimited) {
            store.saveRateLimitedUntil(minOf(limited.until, now + 5.minutes))
            BackgroundOutcome.Failed
        } catch (failure: Exception) {
            BackgroundOutcome.Failed
        }
    }

    companion object {
        val BOUND = 8.seconds
    }
}
