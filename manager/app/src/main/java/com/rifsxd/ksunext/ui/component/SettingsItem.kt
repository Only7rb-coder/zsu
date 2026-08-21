package com.rifsxd.ksunext.ui.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemColors
import com.dergoogler.mmrl.ui.component.LabelItem
import com.dergoogler.mmrl.ui.component.text.TextRow

@Composable
fun SwitchItem(
    icon: ImageVector? = null,
    title: String,
    summary: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    beta: Boolean = false,
    modifier: Modifier = Modifier,
    colors: ListItemColors = ListItemDefaults.colors(containerColor = Color.Transparent),
    onCheckedChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val stateAlpha = remember(checked, enabled) { Modifier.alpha(if (enabled) 1f else 0.5f) }
    val colorScheme = MaterialTheme.colorScheme
    val rowShape = RoundedCornerShape(12.dp)
    val containerColor = if (checked) {
        colorScheme.primaryContainer.copy(alpha = 0.68f)
    } else {
        colorScheme.surfaceContainerHigh.copy(alpha = 0.54f)
    }
    val borderColor = if (checked) {
        colorScheme.primary.copy(alpha = 0.52f)
    } else {
        colorScheme.outline.copy(alpha = 0.28f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                role = Role.Switch,
                enabled = enabled,
                indication = LocalIndication.current,
                onValueChange = onCheckedChange
            )
            .then(stateAlpha),
        shape = rowShape,
        color = containerColor,
        contentColor = colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        ListItem(
            modifier = Modifier.fillMaxWidth(),
            colors = colors,
            headlineContent = {
                TextRow(
                    leadingContent = if (beta) {
                        {
                            LabelItem(text = "Beta")
                        }
                    } else null
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            leadingContent = icon?.let {
                {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (checked) colorScheme.primary else colorScheme.onSurfaceVariant
                    )
                }
            },
            trailingContent = {
                PremiumToggle(
                    checked = checked,
                    enabled = enabled
                )
            },
            supportingContent = {
                if (summary != null) {
                    Text(text = summary)
                }
            }
        )
    }
}

@Composable
private fun PremiumToggle(
    checked: Boolean,
    enabled: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.primary else colors.surfaceContainerHighest,
        animationSpec = tween(durationMillis = 180),
        label = "toggleTrack"
    )
    val trackBorder by animateColorAsState(
        targetValue = if (checked) colors.primary.copy(alpha = 0.9f) else colors.outline.copy(alpha = 0.42f),
        animationSpec = tween(durationMillis = 180),
        label = "toggleBorder"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 27.dp else 3.dp,
        animationSpec = tween(durationMillis = 180),
        label = "toggleThumbOffset"
    )

    Box(
        modifier = Modifier
            .width(58.dp)
            .height(34.dp)
            .clip(CircleShape)
            .background(trackColor)
            .border(
                width = 1.dp,
                color = trackBorder,
                shape = CircleShape
            )
            .graphicsLayer { alpha = if (enabled) 1f else 0.48f },
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(28.dp)
                .shadow(3.dp, CircleShape),
            shape = CircleShape,
            color = if (checked) colors.onPrimary else colors.surfaceContainerHigh,
            contentColor = if (checked) colors.primary else colors.onSurfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Remove,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun RadioItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(title)
        },
        leadingContent = {
            RadioButton(selected = selected, onClick = onClick)
        }
    )
}
