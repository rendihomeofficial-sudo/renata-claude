package com.renata.claude

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.renata.claude.data.ChatStore
import com.renata.claude.data.LockPrefs
import com.renata.claude.data.SettingsStore
import com.renata.claude.ui.AppRoot
import com.renata.claude.ui.theme.RenataClaudeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsStore.init(this)
        ChatStore.init(this)
        LockPrefs.init(this)
        setContent {
            val settings by SettingsStore.state.collectAsState()
            RenataClaudeTheme(themeMode = settings.themeMode) {
                AppRoot()
            }
        }
    }
}
