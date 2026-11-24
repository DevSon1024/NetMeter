package com.devson.netmeter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devson.netmeter.core.permission.UsagePermissionViewModel
import com.devson.netmeter.core.theme.NetMeterTheme
import com.devson.netmeter.features.settings.SettingsViewModel

class MainActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val permissionViewModel: UsagePermissionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsViewModel.themeMode.collectAsStateWithLifecycle()
            val palette by settingsViewModel.selectedPalette.collectAsStateWithLifecycle()
            val isAmoled by settingsViewModel.isAmoledTheme.collectAsStateWithLifecycle()
            val dynamicColor by settingsViewModel.dynamicColor.collectAsStateWithLifecycle()

            NetMeterTheme(
                themeMode = themeMode,
                palette = palette,
                isAmoledTheme = isAmoled,
                dynamicColor = dynamicColor
            ) {
                NetMeterApp(
                    settingsViewModel = settingsViewModel,
                    permissionViewModel = permissionViewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionViewModel.checkPermission(this)
    }
}
