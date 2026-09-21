package com.networktoolbox

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.networktoolbox.core.designsystem.NetworkToolboxTheme
import com.networktoolbox.core.designsystem.NetworkToolboxThemeMode
import com.networktoolbox.core.network.website.WebsiteProgressStatus
import com.networktoolbox.core.network.website.WebsiteStage
import com.networktoolbox.feature.webdiagnostics.R
import com.networktoolbox.feature.webdiagnostics.domain.DiagnosticStageState
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckProgress
import com.networktoolbox.feature.webdiagnostics.domain.TlsCheckStage
import com.networktoolbox.feature.webdiagnostics.presentation.TlsCheckRunState
import com.networktoolbox.feature.webdiagnostics.presentation.TlsCheckUiState
import com.networktoolbox.feature.webdiagnostics.presentation.WebsiteDiagnosticsRunState
import com.networktoolbox.feature.webdiagnostics.presentation.WebsiteDiagnosticsUiState
import com.networktoolbox.feature.webdiagnostics.presentation.WebsiteProgressUi
import com.networktoolbox.feature.webdiagnostics.ui.TlsCheckScreen
import com.networktoolbox.feature.webdiagnostics.ui.WebsiteDiagnosticsScreen
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class WebDiagnosticsScreenTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun tlsIdleShowsHostPortAndStartInLightTheme() {
        renderTls(TlsCheckUiState(), darkTheme = false)

        compose.onNodeWithTag("tls_target").assertIsDisplayed()
        compose.onNodeWithTag("tls_port").assertIsDisplayed()
        compose.onNodeWithTag("tls_start").assertIsDisplayed()
    }

    @Test fun tlsRunningUsesRealStagesLocksInputAndShowsBackConfirmation() {
        var stopAndLeaveCalls = 0
        val progress = TlsCheckProgress(
            target = "example.com",
            stages = mapOf(
                TlsCheckStage.DNS to DiagnosticStageState.SUCCESS,
                TlsCheckStage.TCP to DiagnosticStageState.SUCCESS,
                TlsCheckStage.TLS to DiagnosticStageState.RUNNING,
                TlsCheckStage.CERTIFICATE to DiagnosticStageState.PENDING,
            ),
        )
        renderTls(
            TlsCheckUiState(
                targetInput = "example.com",
                runState = TlsCheckRunState.Running(progress),
            ),
            onStopAndLeave = { action -> stopAndLeaveCalls += 1; action() },
        )

        compose.onNodeWithTag("tls_running").assertIsDisplayed()
        compose.onNodeWithTag("tls_target").assertIsNotEnabled()
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.onNodeWithText(compose.activity.getString(R.string.web_back_dialog_title)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.web_stop_leave)).performClick()
        assertEquals(1, stopAndLeaveCalls)
    }

    @Test fun websiteIdleIsAccessibleInDarkTheme() {
        renderWebsite(WebsiteDiagnosticsUiState(), darkTheme = true)

        compose.onNodeWithTag("website_target").assertIsDisplayed()
        compose.onNodeWithTag("website_start").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.web_privacy_note)).assertIsDisplayed()
    }

    @Test fun websiteRunningShowsTypedPipelineAndCancellation() {
        var stops = 0
        val progress = WebsiteProgressUi(
            targetUrlRedacted = "https://example.com/",
            stages = mapOf(
                WebsiteStage.DNS to WebsiteProgressStatus.PASS,
                WebsiteStage.TCP to WebsiteProgressStatus.PASS,
                WebsiteStage.TLS to WebsiteProgressStatus.PASS,
                WebsiteStage.CERTIFICATE to WebsiteProgressStatus.ATTENTION,
                WebsiteStage.HTTP to WebsiteProgressStatus.RUNNING,
            ),
        )
        renderWebsite(
            WebsiteDiagnosticsUiState(
                targetInput = "example.com",
                runState = WebsiteDiagnosticsRunState.Running(progress),
            ),
            onStop = { stops += 1 },
        )

        compose.onNodeWithTag("website_running").assertIsDisplayed()
        compose.onNodeWithTag("website_target").assertIsNotEnabled()
        compose.onNodeWithText(compose.activity.getString(R.string.web_site_stop)).performClick()
        assertEquals(1, stops)
    }

    private fun renderTls(
        state: TlsCheckUiState,
        darkTheme: Boolean = false,
        onStopAndLeave: ((() -> Unit) -> Unit) = {},
    ) {
        compose.activity.setContent {
            NetworkToolboxTheme(themeMode = if (darkTheme) NetworkToolboxThemeMode.DARK else NetworkToolboxThemeMode.LIGHT) {
                TlsCheckScreen(
                    uiState = state,
                    onTargetChanged = {},
                    onPortChanged = {},
                    onStart = {},
                    onStop = {},
                    onStopAndLeave = onStopAndLeave,
                    onToggleDetails = {},
                    onToggleSans = {},
                    onToggleChain = {},
                    onBack = {},
                )
            }
        }
    }

    private fun renderWebsite(
        state: WebsiteDiagnosticsUiState,
        darkTheme: Boolean = false,
        onStop: () -> Unit = {},
    ) {
        compose.activity.setContent {
            NetworkToolboxTheme(themeMode = if (darkTheme) NetworkToolboxThemeMode.DARK else NetworkToolboxThemeMode.LIGHT) {
                WebsiteDiagnosticsScreen(
                    uiState = state,
                    onTargetChanged = {},
                    onStart = {},
                    onStop = onStop,
                    onStopAndLeave = {},
                    onToggleDetails = {},
                    onToggleRedirects = {},
                    onToggleRecommendations = {},
                    onToggleCertificate = {},
                    onBack = {},
                )
            }
        }
    }
}
