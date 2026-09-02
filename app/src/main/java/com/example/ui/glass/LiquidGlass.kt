package com.example.ui.glass

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight

/**
 * Liquid Glass building blocks for Paper Trail, built on Kyant0/AndroidLiquidGlass
 * (https://github.com/Kyant0/AndroidLiquidGlass) — the Compose-native port of iOS 26's
 * "Liquid Glass" material. Real-time backdrop blur works from API 31 (this app's minSdk);
 * the SDF lens refraction requires API 33+ and silently no-ops below that, so no version
 * gating is needed at call sites.
 */
object LiquidGlassDefaults {
  val CornerRadius: Dp = 24.dp
  val BlurRadius: Dp = 3.dp
  val RefractionHeight: Dp = 14.dp
  val RefractionAmount: Dp = 32.dp

  /**
   * Bottom clearance screens should reserve so scrolling content and FABs don't sit under the
   * floating glass navigation bar. Generous on purpose: the bar's own height (~80dp, sized to
   * fit an icon+label NavigationBarItem) plus its padding (24dp) plus the system navigation bar
   * inset (up to ~48dp on 3-button nav) can add up to ~150dp — this errs toward a little extra
   * breathing room over any clipped content.
   */
  val BottomBarClearance: Dp = 152.dp
}

/** Remembers the capture surface that every glass element on this screen refracts. */
@Composable
fun rememberGlassBackdrop(): LayerBackdrop = rememberLayerBackdrop()

/** Marks content as the backdrop glass elements drawn above it should sample and refract. */
fun Modifier.glassBackdropSource(backdrop: LayerBackdrop): Modifier = layerBackdrop(backdrop)

/**
 * Applies Apple-style "Regular" Liquid Glass to this element: real-time backdrop blur, vibrancy,
 * SDF lens refraction of whatever is drawn behind it, and a thin specular edge highlight.
 *
 * @param tint an optional wash of color over the glass (e.g. a Material color at low alpha),
 * mirroring `LiquidGlassView.glassTint` from the reference implementation.
 */
fun Modifier.liquidGlass(
  backdrop: Backdrop,
  shape: Shape = RoundedCornerShape(LiquidGlassDefaults.CornerRadius),
  tint: Color = Color.Unspecified,
  blurRadius: Dp = LiquidGlassDefaults.BlurRadius,
  refractionHeight: Dp = LiquidGlassDefaults.RefractionHeight,
  refractionAmount: Dp = LiquidGlassDefaults.RefractionAmount
): Modifier = drawBackdrop(
  backdrop = backdrop,
  shape = { shape },
  effects = {
    vibrancy()
    blur(blurRadius.toPx())
    lens(refractionHeight.toPx(), refractionAmount.toPx())
  },
  highlight = { Highlight.Default },
  onDrawSurface = {
    if (tint.isSpecified) {
      drawRect(tint, blendMode = BlendMode.Hue)
      drawRect(tint.copy(alpha = tint.alpha * 0.75f))
    }
  }
)
