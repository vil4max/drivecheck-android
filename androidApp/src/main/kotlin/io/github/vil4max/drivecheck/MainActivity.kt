package io.github.vil4max.drivecheck

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import io.github.vil4max.drivecheck.status.RefreshOutcome
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vil4max.drivecheck.domain.AlertRegion
import io.github.vil4max.drivecheck.domain.AlertStatus
import io.github.vil4max.drivecheck.map.MapCaption
import io.github.vil4max.drivecheck.map.MapState
import io.github.vil4max.drivecheck.status.DetailsSummary
import io.github.vil4max.drivecheck.status.RegionList
import io.github.vil4max.drivecheck.theme.DriveCheckColors
import io.github.vil4max.drivecheck.theme.DriveCheckTheme
import kotlin.time.Clock

class MainActivity : ComponentActivity() {
    private val viewModel: StatusViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            DriveCheckTheme {
                Surface {
                    AppRoot(
                        viewModel,
                        onOpenSettings = ::openAppSettings,
                        onOpenNotificationSettings = ::openNotificationSettings,
                        onOpenSource = ::openSource,
                    )
                }
            }
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    private fun openNotificationSettings() {
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    private fun openSource() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wiki.ubilling.net.ua/doku.php?id=aerialalertsapi")))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(
    viewModel: StatusViewModel,
    onOpenSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenSource: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mapState by viewModel.mapState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Status) }
    var showRegions by rememberSaveable { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onPermissionResult()
    }

    // REQ-SURF-008: re-read on every start, since Android settings may have changed meanwhile.
    val context = LocalContext.current
    val notificationSettings = remember { NotificationSettings(context) }
    var notification by remember { mutableStateOf(notificationSettings.state()) }
    fun applyNotificationChange() {
        notification = notificationSettings.state()
        Surfaces.updateAll(context)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) notificationSettings.onPermissionRefused()
        applyNotificationChange()
    }
    val onNotificationChange: (Boolean) -> Unit = { on ->
        notificationSettings.isChosen = on
        if (on && notificationSettings.needsPermission() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            applyNotificationChange()
        }
    }

    val activity = LocalActivity.current
    LifecycleStartEffect(viewModel) {
        notification = notificationSettings.state()
        viewModel.onScreenStarted()
        onStopOrDispose { viewModel.onScreenStopped(activity?.isChangingConfigurations == true) }
    }

    // REQ-REGION-010: the system prompt comes only after Get Started.
    LaunchedEffect(state.shouldRequestPermission) {
        if (!state.shouldRequestPermission) return@LaunchedEffect
        if (viewModel.hasLocationPermission()) {
            viewModel.onPermissionResult()
        } else {
            permissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    // REQ-REFRESH-011: the driver's pull ends with a haptic only, never a text message.
    val view = LocalView.current
    LaunchedEffect(viewModel) {
        viewModel.refreshOutcomes.collect { outcome ->
            val feedback = when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> HapticFeedbackConstants.VIRTUAL_KEY
                outcome == RefreshOutcome.Confirm -> HapticFeedbackConstants.CONFIRM
                else -> HapticFeedbackConstants.REJECT
            }
            view.performHapticFeedback(feedback)
        }
    }

    if (!state.isOnboardingDone) {
        OnboardingScreen(onGetStarted = viewModel::completeOnboarding)
        return
    }

    val snapshot = viewModel.snapshot()
    val alarms = AlertRegion.entries.filter { snapshot?.status(it) == AlertStatus.Alarm }
    val resources = LocalResources.current

    BackHandler(enabled = showRegions) { showRegions = false }

    Box(Modifier.fillMaxSize()) {
        when {
            showRegions -> localizedRegionList(snapshot, state.region, resources)
                ?.let { RegionListScreen(it, onBack = { showRegions = false }) }
            tab == Tab.Status -> StatusContent(
                presentation = state.presentation,
                regionName = stringResource(state.region.nameRes()),
                isChecking = state.isDriverRefreshing,
                onRefresh = viewModel::refresh,
                locationDenied = state.isLocationDenied,
                onOpenSettings = onOpenSettings,
                map = { modifier ->
                    LaunchedEffect(Unit) { viewModel.onMapShown() }
                    MapCard(
                        state = mapState.toCard(alarms),
                        onOpen = { if (snapshot != null) showRegions = true },
                        onRetry = viewModel::retryMap,
                        modifier = modifier,
                    )
                },
            )
            else -> DetailsScreen(
                lines = DetailsSummary.lines(viewModel.statusState(), snapshot, state.region, state.presentation.isStale),
                countryStatuses = if (snapshot == null) emptyList() else AlertRegion.entries.map { snapshot.status(it) },
                alertRegions = alarms,
                locationDenied = state.isLocationDenied,
                onOpenSettings = onOpenSettings,
                onOpenSource = onOpenSource,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                notification = notification,
                onNotificationChange = onNotificationChange,
                onOpenNotificationSettings = onOpenNotificationSettings,
            )
        }
        Box(Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 12.dp)) {
            TabBar(selected = tab, onSelect = { tab = it; showRegions = false })
        }
        state.notice?.let { region ->
            RegionChangedNotice(
                regionName = stringResource(region.nameRes()),
                onDismiss = viewModel::dismissNotice,
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 104.dp),
            )
        }
    }

    if (state.showOutsideSheet) {
        ModalBottomSheet(onDismissRequest = viewModel::dismissOutsideSheet, containerColor = DriveCheckColors.buttonFill) {
            OutsideUkraineContent(onGotIt = viewModel::dismissOutsideSheet)
        }
    }
}

@Composable
private fun MapState.toCard(alarms: List<AlertRegion>): MapCardState = when (this) {
    is MapState.Loaded -> {
        val image = remember(this) { BitmapFactory.decodeByteArray(image, 0, image.size)?.asImageBitmap() }
        if (image == null) {
            MapCardState.Failed
        } else {
            MapCardState.Loaded(image, alarms, MapCaption.of(alarms.size, fetchedAt, Clock.System.now()).minutes)
        }
    }
    MapState.Failed -> MapCardState.Failed
    else -> MapCardState.Loading
}
