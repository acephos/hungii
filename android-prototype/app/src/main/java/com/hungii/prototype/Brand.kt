package com.hungii.prototype

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Shared path artwork, rather than platform-dependent text or a separate icon. */
@Composable
internal fun HungiiWordmark(modifier: Modifier = Modifier, height: Dp = 56.dp) {
    Image(
        painter = painterResource(R.drawable.ic_hungii_wordmark),
        contentDescription = "Hungii",
        colorFilter = ColorFilter.tint(Accent),
        modifier = modifier.height(height).aspectRatio(370f / 164f),
    )
}
