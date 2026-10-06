package com.holymeowlabs.catnipkiosk

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

fun isTv(context: Context) =
    context.getSystemService(UiModeManager::class.java).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
