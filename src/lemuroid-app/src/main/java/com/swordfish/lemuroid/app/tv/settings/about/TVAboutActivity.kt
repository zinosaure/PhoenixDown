package com.swordfish.lemuroid.app.tv.settings.about

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import com.swordfish.lemuroid.app.mobile.feature.settings.about.AboutScreen
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme

class TVAboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            SystemBarStyle.dark(Color.TRANSPARENT),
            SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme(darkTheme = true) {
                AboutScreen(
                    onNavigateBack = {
                        finish()
                    }
                )
            }
        }
    }
}
