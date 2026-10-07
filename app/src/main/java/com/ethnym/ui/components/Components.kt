package com.ethnym.ui.components

import android.content.ClipData
import android.os.PersistableBundle
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ethnym.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
        HorizontalDivider()
    }
}

/**
 * A titled group of related content on a solid sheet a shade off the background, like the iOS
 * app's list sections: a [SectionIntro], then [content].
 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    info: String? = null,
    accessory: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Card(modifier.fillMaxWidth(), colors = sheetColors()) {
        // On the sheet's shade, tonal buttons, selected segments and menus would blend in, so they
        // move one step further from the background. The components keep their default styling.
        MaterialTheme(
            colorScheme = colors.copy(
                secondaryContainer = colors.surfaceContainerHighest,
                surfaceContainer = colors.surfaceContainerHigh,
            ),
        ) {
            Column(
                Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionIntro(title, info = info, accessory = accessory)
                content()
            }
        }
    }
}

/**
 * A sheet a shade off the background. The text color is set because the light palette's
 * surfaceVariant is the same grey, which would otherwise give muted text.
 */
@Composable
fun sheetColors(): CardColors = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    contentColor = MaterialTheme.colorScheme.onSurface,
)

/**
 * A section's first row: its title, an (i) that explains what the section is for when there is
 * [info], and any [accessory] on the right.
 */
@Composable
fun SectionIntro(
    title: String,
    modifier: Modifier = Modifier,
    info: String? = null,
    accessory: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        if (info != null) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                InfoIconButton(title = title, text = info)
            }
        }
        Spacer(Modifier.weight(1f))
        accessory()
    }
}

/** A small outlined label, such as "Offline". */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** Short explanatory text above a form, like the web wallet's muted headings. */
@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun ErrorText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = modifier,
    )
}

@Composable
fun ComingSoon(description: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.coming_soon), style = MaterialTheme.typography.titleSmall)
        if (description != null) HintText(description)
    }
}

/** A row of tabs inside a screen (the web wallet's sub-tabs). */
@Composable
fun SubTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SecondaryTabRow(selectedTabIndex = selectedIndex, modifier = modifier) {
        tabs.forEachIndexed { index, title ->
            Tab(selected = index == selectedIndex, onClick = { onSelect(index) }, text = { Text(title, maxLines = 1) })
        }
    }
}

@Composable
fun BusyIndicator(modifier: Modifier = Modifier) {
    CircularProgressIndicator(modifier = modifier.size(20.dp), strokeWidth = 2.dp)
}

/** Full address, selectable. Addresses are never truncated where they fit (as in the web wallet). */
@Composable
fun AddressText(address: String, modifier: Modifier = Modifier) {
    SelectionContainer(modifier = modifier) {
        Text(text = address, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(R.string.password),
    error: String? = null,
    imeAction: ImeAction = ImeAction.Done,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = imeAction),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    painter = painterResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    contentDescription = stringResource(if (visible) R.string.hide_password else R.string.show_password),
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Copies [text] to the clipboard. [sensitive] marks it so Android hides it from the clipboard
 * preview (for secret phrases).
 */
@Composable
fun rememberCopyAction(sensitive: Boolean = false): Pair<Boolean, (String) -> Unit> {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1_500)
            copied = false
        }
    }
    return copied to { text: String ->
        scope.launch {
            val clip = ClipData.newPlainText("ETHnym", text)
            if (sensitive) {
                clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
            }
            clipboard.setClipEntry(ClipEntry(clip))
            copied = true
        }
    }
}

@Composable
fun CopyIconButton(text: String, modifier: Modifier = Modifier, sensitive: Boolean = false) {
    val (copied, copy) = rememberCopyAction(sensitive)
    IconButton(onClick = { copy(text) }, enabled = text.isNotEmpty(), modifier = modifier) {
        Icon(
            painter = painterResource(if (copied) R.drawable.ic_check else R.drawable.ic_content_copy),
            contentDescription = stringResource(if (copied) R.string.copied else R.string.copy),
        )
    }
}

@Composable
fun CopyButton(text: String, modifier: Modifier = Modifier, sensitive: Boolean = false) {
    val (copied, copy) = rememberCopyAction(sensitive)
    OutlinedButton(onClick = { copy(text) }, enabled = text.isNotEmpty() && !copied, modifier = modifier) {
        Text(stringResource(if (copied) R.string.copied else R.string.copy))
    }
}

/** An (i) button that explains a field, like the web wallet's information dialogs. */
@Composable
fun InfoIconButton(title: String, text: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier) {
        Icon(painter = painterResource(R.drawable.ic_info), contentDescription = title)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.ok)) } },
        )
    }
}

/** Label on the left, value on the right; used for key/value detail lists. */
@Composable
fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Primary action (with a spinner while busy) and Reset, as on every web wallet form. */
@Composable
fun FormButtons(
    primary: String,
    onPrimary: () -> Unit,
    primaryEnabled: Boolean,
    busy: Boolean,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onPrimary, enabled = primaryEnabled) {
            if (busy) BusyIndicator() else Text(primary)
        }
        OutlinedButton(onClick = onReset, enabled = !busy) { Text(stringResource(R.string.reset)) }
    }
}
