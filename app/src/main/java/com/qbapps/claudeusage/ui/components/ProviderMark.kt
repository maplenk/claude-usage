package com.qbapps.claudeusage.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.qbapps.claudeusage.R

enum class ProviderBrand(
    val displayName: String,
    @DrawableRes val iconRes: Int,
) {
    CLAUDE("Claude", R.drawable.ic_provider_claude),
    CODEX("Codex", R.drawable.ic_provider_codex),
    GROK("Grok", R.drawable.ic_provider_grok),
    OPEN_USAGE("OpenUsage", R.drawable.ic_openusage_mark),
}

@Composable
fun ProviderMark(
    provider: ProviderBrand,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    Icon(
        painter = painterResource(provider.iconRes),
        contentDescription = provider.displayName,
        tint = tint,
        modifier = modifier.then(Modifier.size(size)),
    )
}
