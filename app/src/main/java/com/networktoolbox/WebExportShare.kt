package com.networktoolbox

import android.content.ActivityNotFoundException
import android.content.Intent

internal enum class WebShareOutcome { STARTED, NO_APP, FAILED }
internal fun launchWebExportChooser(intent:Intent,launch:(Intent)->Unit):WebShareOutcome = try {
    launch(intent);WebShareOutcome.STARTED
} catch(_:ActivityNotFoundException) {WebShareOutcome.NO_APP}
catch(_:Exception) {WebShareOutcome.FAILED}
