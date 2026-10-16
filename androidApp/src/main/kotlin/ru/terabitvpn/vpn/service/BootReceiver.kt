package ru.terabitvpn.vpn.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import ru.terabitvpn.vpn.TerabitApplication

/** "Start on boot": brings the tunnel up after a reboot or an app update, if the user enabled it. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return
        val controller = TerabitApplication.instance.controller
        if (!controller.settings.value.startOnBoot) return
        // Consent must already exist: a receiver can't show the system VPN dialog.
        if (VpnService.prepare(context) != null || controller.selectedServer() == null) return
        ContextCompat.startForegroundService(
            context,
            Intent(context, TerabitVpnService::class.java).setAction(TerabitVpnService.ACTION_START),
        )
    }
}
