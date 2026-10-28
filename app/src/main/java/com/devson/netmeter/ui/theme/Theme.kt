package com.devson.netmeter.ui.theme

import androidx.compose.runtime.Composable
import com.devson.netmeter.core.theme.NetMeterTheme
import com.devson.netmeter.core.theme.ThemeMode

@Composable
fun NetMeterTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) = NetMeterTheme(themeMode = themeMode, dynamicColor = dynamicColor, content = content)