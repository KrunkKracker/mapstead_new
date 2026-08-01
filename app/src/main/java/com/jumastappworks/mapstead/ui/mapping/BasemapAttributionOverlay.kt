package com.jumastappworks.mapstead.ui.mapping

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.jumastappworks.mapstead.R
import com.jumastappworks.mapstead.data.mapping.*

/**
 * Reusable attribution and logo overlay for all MapTiler/OFM map surfaces.
 * Complies with provider requirements and localized accessibility.
 */
@Composable
fun BasemapAttributionOverlay(
    modifier: Modifier = Modifier,
    sourceId: BasemapSourceId?,
    basemapProvider: BasemapProvider
) {
    val context = LocalContext.current
    val sid = sourceId ?: return
    val def = basemapProvider.getDefinition(sid)
    val attribution = basemapProvider.getAttribution(sid)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        if (def?.provider == BasemapProviderType.MAPTILER) {
            Box(
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .size(width = 100.dp, height = 30.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.visit_maptiler)
                    ) { 
                        try {
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, "https://www.maptiler.com".toUri()))
                        } catch (e: Exception) { }
                    }
                    .padding(2.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(R.raw.maptiler_logo)
                        .decoderFactory(SvgDecoder.Factory())
                        .build(),
                    contentDescription = stringResource(R.string.maptiler_logo_description),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            shape = RoundedCornerShape(4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                attribution.forEach { entry ->
                    Text(
                        text = stringResource(entry.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .semantics { role = Role.Button }
                            .clickable(
                                role = Role.Button,
                                onClickLabel = stringResource(R.string.view_copyright)
                            ) { 
                                try {
                                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, entry.destination.toUri()))
                                } catch (e: Exception) { }
                            }
                    )
                }
            }
        }
    }
}
