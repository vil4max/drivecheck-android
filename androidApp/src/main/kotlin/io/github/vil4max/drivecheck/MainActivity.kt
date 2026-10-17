package io.github.vil4max.drivecheck

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vil4max.drivecheck.theme.DriveCheckColors
import io.github.vil4max.drivecheck.theme.DriveCheckTheme

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
                    AppRoot(viewModel, onOpenSettings = ::openAppSettings)
                }
            }
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(viewModel: StatusViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onPermissionResult()
    }

    LifecycleStartEffect(viewModel) {
        viewModel.onScreenStarted()
        onStopOrDispose { viewModel.onScreenStopped() }
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

    if (!state.isOnboardingDone) {
        OnboardingScreen(onGetStarted = viewModel::completeOnboarding)
        return
    }

    Box(Modifier.fillMaxSize()) {
        StatusScreen(viewModel, onOpenSettings)
        state.notice?.let { region ->
            RegionChangedNotice(
                regionName = stringResource(region.nameRes()),
                onDismiss = viewModel::dismissNotice,
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(bottom = 16.dp),
            )
        }
    }

    if (state.showOutsideSheet) {
        ModalBottomSheet(onDismissRequest = viewModel::dismissOutsideSheet, containerColor = DriveCheckColors.buttonFill) {
            OutsideUkraineContent(onGotIt = viewModel::dismissOutsideSheet)
        }
    }
}
