package com.lnx.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.lnx.app.core.designsystem.DarkMode
import com.lnx.app.core.designsystem.LnxTheme
import com.lnx.app.core.designsystem.ThemeSlot
import com.lnx.app.feature.calendar.CalendarScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LnxTheme(slot = ThemeSlot.MATERIAL_YOU, darkMode = DarkMode.FOLLOW_SYSTEM) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CalendarScreen()
                }
            }
        }
    }
}
