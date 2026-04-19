package com.swordfish.lemuroid.app.tv.disclaimer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import com.swordfish.lemuroid.app.mobile.feature.disclaimer.DisclaimerScreen
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper

class TVDisclaimerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            SystemBarStyle.dark(Color.TRANSPARENT),
            SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme(darkTheme = true) {
                DisclaimerScreen(
                    onAccept = {
                        SharedPreferencesHelper.getSharedPreferences(applicationContext)
                            .edit()
                            .putBoolean("disclaimer_accepted", true)
                            .apply()
                        finish()
                    },
                )
            }
        }
    }
}
