package com.aletchec.traceit.ui.component

import android.content.Context
import android.text.Html
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import com.aletchec.traceit.BuildConfig
import com.aletchec.traceit.R

@Composable
fun AboutAppDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isPreview = androidx.compose.ui.platform.LocalInspectionMode.current

    val versionName = BuildConfig.VERSION_NAME
    val buildVariant = "${BuildConfig.FLAVOR}${BuildConfig.BUILD_TYPE.replaceFirstChar { it.uppercase() }}"

    val appIconBitmap = if (isPreview) null else remember {
        try {
            val drawable = context.packageManager.getApplicationIcon(context.packageName)
            drawable.toBitmap().asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        },
        title = {
            Text(
                text = stringResource(R.string.about_traceit),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                if (appIconBitmap != null) {
                    Image(
                        bitmap = appIconBitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.version_variant_format, versionName, buildVariant),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                HtmlText(
                    html = Html.fromHtml(
                        stringResource(R.string.eula_html),
                        Html.FROM_HTML_MODE_LEGACY
                    )
                )
            }
        }
    )
}

@Composable
fun HtmlText(html: Spanned, modifier: Modifier = Modifier) {
    val context: Context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface
    val linkColor = MaterialTheme.colorScheme.primary

    AndroidView(
        modifier = modifier,
        factory = {
            TextView(context).apply {
                movementMethod = LinkMovementMethod.getInstance()
                linksClickable = true
                gravity = android.view.Gravity.CENTER
                setTextColor(textColor.toArgb())
                setLinkTextColor(linkColor.toArgb())
                setPadding(0, 0, 0, 0)
                includeFontPadding = false
            }
        },
        update = { textView ->
            textView.text = html
            textView.setTextColor(textColor.toArgb())
            textView.setLinkTextColor(linkColor.toArgb())
        }
    )
}
