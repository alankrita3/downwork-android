package com.raviga.app.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.raviga.app.di.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
