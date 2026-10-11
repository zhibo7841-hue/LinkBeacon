package com.networktoolbox

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Resources
import androidx.core.net.toUri

/** One localized resource is the URL authority; Android resolves script/list/system fallback. */
internal object PrivacyPolicyUrls {
    fun from(resources: Resources): String = resources.getString(R.string.info_policy_url)
}

internal enum class PrivacyPolicyOpenResult { OPENED, NO_BROWSER, FAILED }

/** Activity-owned, click-only boundary. Dispatch success is not a successful page download. */
internal class PrivacyPolicyLauncher(private val startActivity: (Intent) -> Unit) {
    fun open(url: String): PrivacyPolicyOpenResult = try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        })
        PrivacyPolicyOpenResult.OPENED
    } catch (_: ActivityNotFoundException) {
        PrivacyPolicyOpenResult.NO_BROWSER
    } catch (_: SecurityException) {
        PrivacyPolicyOpenResult.FAILED
    } catch (_: RuntimeException) {
        PrivacyPolicyOpenResult.FAILED
    }
}

internal enum class PrivacyPolicyFeedback { NONE, NO_BROWSER, OPEN_FAILED, COPIED, COPY_FAILED }
