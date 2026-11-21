package com.lemarc.fake_gps.mock

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process

/** Vérifie que l'appli est choisie comme « application de position fictive » (options pour les développeurs). */
object MockLocationAccess {

    fun isSelectedMockApp(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                ops.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName)
            }
        } catch (_: SecurityException) {
            AppOpsManager.MODE_ERRORED
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
