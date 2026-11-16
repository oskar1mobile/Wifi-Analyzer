package app.wifianalyzer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.wifianalyzer.ui.AppTheme
import app.wifianalyzer.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme { App() }
        }
    }
}

private val requiredPermissions: Array<String> = buildList {
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.NEARBY_WIFI_DEVICES)
}.toTypedArray()

@Composable
private fun App(vm: ScanViewModel = viewModel()) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasPermission = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (hasPermission) vm.scan()
    }

    LaunchedEffect(Unit) {
        if (hasPermission) {
            if (state.result == null) vm.scan()
        } else {
            launcher.launch(requiredPermissions)
        }
    }

    MainScreen(
        state = state,
        hasPermission = hasPermission,
        onScan = { if (hasPermission) vm.scan() else launcher.launch(requiredPermissions) },
        onRequestPermission = { launcher.launch(requiredPermissions) },
        onDemo = vm::loadDemo,
        onAutoRefresh = vm::setAutoRefresh,
    )
}
