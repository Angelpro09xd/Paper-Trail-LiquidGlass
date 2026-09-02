package com.example.ui.glass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

/**
 * A floating, iOS 26-style Liquid Glass tab bar: a translucent pill that refracts whatever
 * screen content scrolls beneath it, in place of a flat Material [androidx.compose.material3.NavigationBar].
 */
@Composable
fun GlassNavigationBar(
  backdrop: Backdrop,
  items: List<Triple<String, String, ImageVector>>,
  currentRoute: String?,
  onItemSelected: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
      .navigationBarsPadding()
      .padding(horizontal = 20.dp, vertical = 12.dp)
      .fillMaxWidth()
      .liquidGlass(
        backdrop = backdrop,
        // Rounded rect rather than a true pill: NavigationBarItem sizes itself to fit an
        // icon + label (~80dp), so the corner radius is fixed rather than tied to height/2.
        shape = RoundedCornerShape(28.dp),
        tint = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
      ),
    horizontalArrangement = Arrangement.SpaceEvenly,
    verticalAlignment = Alignment.CenterVertically
  ) {
    items.forEach { (route, label, icon) ->
      val isSelected = currentRoute == route
      NavigationBarItem(
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) },
        selected = isSelected,
        onClick = { onItemSelected(route) },
        colors = NavigationBarItemDefaults.colors(
          indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
          selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
          selectedTextColor = MaterialTheme.colorScheme.primary,
          unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
          unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.testTag("nav_item_$route")
      )
    }
  }
}
