package com.networktoolbox

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * The complete LinkBeacon brand mark used by the launcher and information
 * surfaces. Keeping the background and foreground together prevents callers
 * from accidentally rendering only the adaptive-icon foreground.
 */
@Composable
internal fun AppBrandLogo(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    foregroundResource: Int = AboutIconPresentation.foregroundResource,
    backgroundResource: Int = AboutIconPresentation.backgroundResource,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(colorResource(backgroundResource)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(foregroundResource),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
