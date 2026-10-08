package com.raviga.downwork.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.raviga.downwork.di.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
