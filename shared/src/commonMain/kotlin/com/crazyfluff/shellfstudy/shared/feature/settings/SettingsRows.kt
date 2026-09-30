package com.crazyfluff.shellfstudy.shared.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.designsystem.theme.einkBorder
import com.crazyfluff.shellfstudy.shared.designsystem.theme.emphasisContainerColor

/** A switch row. The whole row is the touch target, and [testTag] goes on the row, which carries
 *  the switch's toggle semantics. */
@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .testTag(testTag)
            .rowPadding(indent = false)
    ) {
        RowText(title, subtitle, enabled, Modifier.weight(1f).padding(end = 16.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * The notifications "main switch": a prominent pill that gates every row after it. It's styled
 * differently from [SwitchRow] because its job is different. Turning it off silences everything
 * beneath it.
 */
@Composable
internal fun MainSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (checked) emphasisContainerColor() else MaterialTheme.colorScheme.surfaceVariant,
        border = einkBorder(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .testTag(testTag)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/** A row that opens something: a picker (with the current [value] shown trailing) or another screen. */
@Composable
internal fun NavRow(
    title: String,
    onClick: () -> Unit,
    testTag: String? = null,
    subtitle: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    indent: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .rowPadding(indent)
    ) {
        RowText(title, subtitle, enabled, Modifier.weight(1f).padding(end = 16.dp))
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else DISABLED_ALPHA)
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else DISABLED_ALPHA)
            )
        }
    }
}

/** A read-only row: a fact about the app or account, with no control. */
@Composable
internal fun InfoRow(title: String, subtitle: String?, testTag: String) {
    RowText(
        title = title,
        subtitle = subtitle,
        enabled = true,
        modifier = Modifier.fillMaxWidth().testTag(testTag).rowPadding(indent = false)
    )
}

/** An action that can't be undone from this screen, like log out. It's drawn in the error colour
 *  with a leading icon, so it doesn't read as another setting. */
@Composable
internal fun DestructiveRow(title: String, icon: ImageVector, onClick: () -> Unit, testTag: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(testTag)
            .rowPadding(indent = false)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
    }
}
