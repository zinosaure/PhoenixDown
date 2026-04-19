package com.swordfish.lemuroid.app.tv.catalog

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import com.swordfish.lemuroid.app.mobile.feature.catalog.CatalogScreen
import com.swordfish.lemuroid.app.mobile.feature.catalog.RomDownloader
import com.swordfish.lemuroid.app.tv.shared.BaseTVActivity
import com.swordfish.lemuroid.lib.library.metadata.GameMetadataProvider
import dagger.android.AndroidInjection
import javax.inject.Inject

/**
 * TVCatalogActivity - Reutiliza CatalogScreen de móvil con Compose
 * La misma pantalla de catálogo funciona tanto en móvil como en TV
 */
class TVCatalogActivity : BaseTVActivity() {

    @Inject
    lateinit var gameMetadataProvider: GameMetadataProvider

    @Inject
    lateinit var romDownloader: RomDownloader
    
    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme()
            ) {
                Surface {
                    CatalogScreen(
                        gameMetadataProvider = gameMetadataProvider,
                        romDownloader = romDownloader
                    )
                }
            }
        }
    }

    @dagger.Module
    abstract class Module
}
