package com.rifsxd.ksunext.ui.component

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    val switchInteractionSource = remember { MutableInteractionSource() }
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
                Switch(
                    checked = checked,
                    enabled = enabled,
                    onCheckedChange = onCheckedChange,
                    interactionSource = switchInteractionSource,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colorScheme.onPrimary,
                        checkedTrackColor = colorScheme.primary,
                        checkedBorderColor = colorScheme.primary,
                        uncheckedThumbColor = colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = colorScheme.surfaceContainerHighest,
                        uncheckedBorderColor = colorScheme.outline.copy(alpha = 0.72f),
                        disabledCheckedThumbColor = colorScheme.onSurface.copy(alpha = 0.38f),
                        disabledCheckedTrackColor = colorScheme.primary.copy(alpha = 0.38f),
                        disabledUncheckedThumbColor = colorScheme.onSurface.copy(alpha = 0.24f),
                        disabledUncheckedTrackColor = colorScheme.onSurface.copy(alpha = 0.12f)
                    )
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
