package com.swordfish.lemuroid.app.tv.shared

import android.content.Context
import android.os.Build
import android.os.Environment
import timber.log.Timber

object TVHelper {
    fun isSAFSupported(context: Context): Boolean {
        val packageManager = context.packageManager

        // TV, Watch, Automotive NO soportan SAF - siempre usar fallback
        val isTV = packageManager.hasSystemFeature("android.hardware.type.television")
        val isWatch = packageManager.hasSystemFeature("android.hardware.type.watch")
        val isAutomotive = packageManager.hasSystemFeature("android.hardware.type.automotive")
        val isNonStandardHardware = isTV || isWatch || isAutomotive

        Timber.d("isSAFSupported: isTV=$isTV, isWatch=$isWatch, isAutomotive=$isAutomotive")

        if (isNonStandardHardware) {
            Timber.d("isSAFSupported: Returning FALSE (non-standard hardware)")
            return false
        }

        // Solo dispositivos estándar (móvil/tablet) con Android 10+ sin legacy storage
        val isNotLegacyStorage =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !Environment.isExternalStorageLegacy()

        Timber.d("isSAFSupported: Returning $isNotLegacyStorage (standard hardware)")
        return isNotLegacyStorage
    }

    fun isTV(context: Context): Boolean {
        val packageManager = context.packageManager
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as android.app.UiModeManager

        // Check 1: Standard TV Hardware Feature
        if (packageManager.hasSystemFeature("android.hardware.type.television")) return true

        // Check 2: Leanback Software Feature (Most common for Android TV)
        if (packageManager.hasSystemFeature("android.software.leanback")) return true

        // Check 3: UI Mode Manager (Some boxes set this but miss features)
        if (uiModeManager.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION) return true

        return false
    }
}
