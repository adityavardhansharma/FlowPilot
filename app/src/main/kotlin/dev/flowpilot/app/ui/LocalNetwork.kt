package dev.flowpilot.app.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Android 17 (API 37) blocks TCP connections to LAN addresses for apps that target it, unless the app holds
 * the runtime ACCESS_LOCAL_NETWORK permission (shown to people as "Nearby devices"). Without it every request
 * to the computer silently times out.
 */
object LocalNetwork {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    val required: Boolean get() = Build.VERSION.SDK_INT >= 37

    fun granted(context: Context): Boolean =
        !required || ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
}

/**
 * Returns a function that runs its block once local network access has been asked for. The block always runs,
 * granted or not: a Tailscale or public address still works without the permission, and a LAN failure is
 * explained where it happens.
 */
@Composable
fun rememberLocalNetworkAccess(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<() -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val block = pending[0]
        pending[0] = null
        block?.invoke()
    }
    return remember(launcher) {
        val run: (() -> Unit) -> Unit = { block ->
            if (LocalNetwork.granted(context)) {
                block()
            } else {
                pending[0] = block
                try {
                    launcher.launch(LocalNetwork.PERMISSION)
                } catch (_: Exception) {
                    pending[0] = null
                    block()
                }
            }
        }
        run
    }
}
