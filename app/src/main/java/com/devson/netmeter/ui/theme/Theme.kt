package com.devson.netmeter.ui.theme

import androidx.compose.runtime.Composable
import com.devson.netmeter.core.theme.AppThemePalette
import com.devson.netmeter.core.theme.NetMeterTheme
import com.devson.netmeter.core.theme.ThemeMode

@Composable
fun NetMeterTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    palette: AppThemePalette = AppThemePalette.BLUE,
    isAmoledTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = NetMeterTheme(
    themeMode = themeMode,
    palette = palette,
    isAmoledTheme = isAmoledTheme,
    dynamicColor = dynamicColor,
    content = content
)
