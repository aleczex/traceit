package com.aletchec.traceit.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aletchec.traceit.R

@Composable
fun OptionsDialog(
    onDismiss: () -> Unit,
    onPrivacyClick: () -> Unit,
    onLicensesClick: () -> Unit,
    onAboutClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.options)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OptionsActionItem(
                    title = stringResource(R.string.privacy_options),
                    icon = Icons.Rounded.PrivacyTip,
                    onClick = {
                        onDismiss()
                        onPrivacyClick()
                    }
                )
                OptionsActionItem(
                    title = stringResource(R.string.open_source_licenses),
                    icon = Icons.Rounded.HistoryEdu,
                    onClick = {
                        onDismiss()
                        onLicensesClick()
                    }
                )
                OptionsActionItem(
                    title = stringResource(R.string.about),
                    icon = Icons.Rounded.Info,
                    onClick = {
                        onDismiss()
                        onAboutClick()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun OptionsActionItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}
