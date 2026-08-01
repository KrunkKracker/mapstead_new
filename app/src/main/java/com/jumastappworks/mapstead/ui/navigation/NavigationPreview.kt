package com.jumastappworks.mapstead.ui.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun NavigationPreviewItem(
    screenWidthDp: Int,
    fontScale: Float,
    useNavigationRail: Boolean
) {
    val presentation = navigationPresentation(
        widthDp = screenWidthDp,
        fontScale = fontScale,
        useNavigationRail = useNavigationRail
    )

    MaterialTheme {
        Surface {
            if (presentation == NavigationPresentation.NavigationRail) {
                MapsteadNavigationRail(
                    currentRoute = Route.Properties,
                    selectedPropId = null,
                    onNavItemClick = {}
                )
            } else {
                MapsteadBottomBar(
                    currentRoute = Route.Properties,
                    selectedPropId = null,
                    isIconOnly = presentation == NavigationPresentation.IconOnly,
                    onNavItemClick = {}
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 800, name = "320dp Narrow Window (Icon-only)")
@Composable
fun Preview320() = NavigationPreviewItem(320, 1.0f, false)

@Preview(showBackground = true, widthDp = 360, heightDp = 800, name = "360dp Compact Phone (Compact Labels)")
@Composable
fun Preview360() = NavigationPreviewItem(360, 1.0f, false)

@Preview(showBackground = true, widthDp = 411, heightDp = 891, name = "411dp Large Phone")
@Composable
fun Preview411() = NavigationPreviewItem(411, 1.0f, false)

@Preview(showBackground = true, widthDp = 800, heightDp = 360, name = "Landscape Compact Phone")
@Composable
fun PreviewLandscape() = NavigationPreviewItem(800, 1.0f, false)

@Preview(showBackground = true, widthDp = 600, heightDp = 900, name = "600dp Medium Window (Rail)")
@Composable
fun Preview600() = NavigationPreviewItem(600, 1.0f, true)

@Preview(showBackground = true, widthDp = 840, heightDp = 1000, name = "840dp Expanded Window (Rail)")
@Composable
fun Preview840() = NavigationPreviewItem(840, 1.0f, true)

@Preview(showBackground = true, widthDp = 360, heightDp = 800, fontScale = 1.3f, name = "360dp Font 1.3 (Icon-only)")
@Composable
fun Preview360Font13() = NavigationPreviewItem(360, 1.3f, false)

@Preview(showBackground = true, widthDp = 360, heightDp = 800, fontScale = 2.0f, name = "360dp Font 2.0 (Icon-only)")
@Composable
fun Preview360Font20() = NavigationPreviewItem(360, 2.0f, false)
