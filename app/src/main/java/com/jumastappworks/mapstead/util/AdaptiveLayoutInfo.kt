package com.jumastappworks.mapstead.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.window.core.layout.WindowHeightSizeClass

data class AdaptiveLayoutInfo(
    val isWidthCompact: Boolean,
    val isWidthMedium: Boolean,
    val isWidthExpanded: Boolean,
    val isHeightCompact: Boolean,
    val isHeightMedium: Boolean,
    val isHeightExpanded: Boolean,
    val isLandscape: Boolean,
    val showPersistentSupportingPane: Boolean,
    val useBottomNavigation: Boolean,
    val useNavigationRail: Boolean
)

@Composable
fun rememberAdaptiveLayoutInfo(): AdaptiveLayoutInfo {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val configuration = LocalConfiguration.current

    val widthSizeClass = adaptiveInfo.windowSizeClass.windowWidthSizeClass
    val heightSizeClass = adaptiveInfo.windowSizeClass.windowHeightSizeClass

    val isWidthCompact = widthSizeClass == WindowWidthSizeClass.COMPACT
    val isWidthMedium = widthSizeClass == WindowWidthSizeClass.MEDIUM
    val isWidthExpanded = widthSizeClass == WindowWidthSizeClass.EXPANDED

    val isHeightCompact = heightSizeClass == WindowHeightSizeClass.COMPACT
    val isHeightMedium = heightSizeClass == WindowHeightSizeClass.MEDIUM
    val isHeightExpanded = heightSizeClass == WindowHeightSizeClass.EXPANDED

    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Adaptive decisions
    val showPersistentSupportingPane = isWidthExpanded || (isWidthMedium && isLandscape)
    val useNavigationRail = isWidthMedium || isWidthExpanded
    val useBottomNavigation = isWidthCompact

    return AdaptiveLayoutInfo(
        isWidthCompact = isWidthCompact,
        isWidthMedium = isWidthMedium,
        isWidthExpanded = isWidthExpanded,
        isHeightCompact = isHeightCompact,
        isHeightMedium = isHeightMedium,
        isHeightExpanded = isHeightExpanded,
        isLandscape = isLandscape,
        showPersistentSupportingPane = showPersistentSupportingPane,
        useBottomNavigation = useBottomNavigation,
        useNavigationRail = useNavigationRail
    )
}
