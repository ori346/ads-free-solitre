package com.adfree.solitaire

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import com.adfree.solitaire.data.ThemeMode
import com.adfree.solitaire.ui.GameScreen
import com.adfree.solitaire.ui.theme.SolitaireTheme
import java.util.Locale
import android.graphics.Color as AndroidColor

/** Edge-to-edge host activity for Ad-Free Solitaire. */
class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.createConfigurationContext(enforcedConfiguration(newBase.resources.configuration)))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(enforcedConfiguration(newConfig))
        window?.decorView?.layoutDirection = View.LAYOUT_DIRECTION_LTR
    }

    private fun enforcedConfiguration(source: Configuration): Configuration =
        Configuration(source).apply {
            setLocale(Locale.US)
            setLayoutDirection(Locale.US)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR
        setContent {
            val dark = when (vm.settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SolitaireTheme(dark) {
                GameScreen(vm, dark)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.setActive(true)
    }

    override fun onPause() {
        vm.setActive(false)
        super.onPause()
    }
}
