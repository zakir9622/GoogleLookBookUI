package com.zakir.vestra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Compare
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.zakir.vestra.ui.theme.VestraColors
import java.io.File

/**
 * Interactive A/B split compare slider between original reference and on-device generated image.
 */
@Composable
fun BeforeAfterCompareSlider(
    beforeImage: Any,
    afterImage: Any,
    modifier: Modifier = Modifier,
    initialSplit: Float = 0.5f,
) {
    var splitFraction by remember { mutableFloatStateOf(initialSplit) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(340.dp)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, VestraColors.GlassBorder, RoundedCornerShape(20.dp))
            .background(Color.Black),
    ) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        val splitWidthDp = with(LocalDensity.current) { (totalWidthPx * splitFraction).toDp() }

        // Bottom layer: After (Generated Image)
        AsyncImage(
            model = afterImage,
            contentDescription = "After Generated Image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        // Top layer (clipped to split width): Before (Original Reference)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(splitWidthDp)
                .clipToBounds(),
        ) {
            AsyncImage(
                model = beforeImage,
                contentDescription = "Before Reference Image",
                modifier = Modifier
                    .width(with(LocalDensity.current) { totalWidthPx.toDp() })
                    .fillMaxHeight(),
                contentScale = ContentScale.Crop,
            )
        }

        // Split Divider Line
        Box(
            modifier = Modifier
                .offset(x = splitWidthDp - 1.5.dp)
                .width(3.dp)
                .fillMaxHeight()
                .background(Color.White),
        )

        // Drag Handle
        Box(
            modifier = Modifier
                .offset(x = splitWidthDp - 18.dp)
                .align(Alignment.CenterStart)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, VestraColors.Accent, CircleShape)
                .pointerInput(totalWidthPx) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val newFraction = (splitFraction + (dragAmount.x / totalWidthPx)).coerceIn(0.05f, 0.95f)
                        splitFraction = newFraction
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Compare,
                contentDescription = "Drag to compare before and after",
                tint = Color.Black,
                modifier = Modifier.size(18.dp),
            )
        }

        // "BEFORE" Badge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                "ORIGINAL",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = Color.White,
            )
        }

        // "AFTER" Badge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(VestraColors.Accent.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                "AI LOOK",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = Color.White,
            )
        }
    }
}
