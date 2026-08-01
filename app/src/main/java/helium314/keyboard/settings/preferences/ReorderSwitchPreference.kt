// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.utils.encodeToHex
import helium314.keyboard.latin.utils.getTextForToolbarKey
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.dialogs.ReorderDialog
import helium314.keyboard.settings.GetIconOrEmpty

@Composable
fun ReorderSwitchPreference(setting: Setting, default: String) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    Preference(
        name = setting.title,
        description = setting.description,
        onClick = { showDialog = true },
    )
    if (showDialog) {
        val ctx = LocalContext.current
        val prefs = ctx.prefs()
        val initialItems = remember(setting.key) {
            prefs.getString(setting.key, default)!!.split(Separators.ENTRY).map {
                val both = it.split(Separators.KV)
                KeyAndState(both.first(), both.last().toBoolean())
            }.toMutableList()
        }
        var items by remember { mutableStateOf(initialItems) }

        ReorderDialog(
            onConfirmed = { reorderedItems ->
                val value = reorderedItems.joinToString(Separators.ENTRY) { it.name + Separators.KV + it.state }
                prefs.edit { putString(setting.key, value) }
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
            },
            onDismissRequest = { showDialog = false },
            onNeutral = { prefs.edit { remove(setting.key)} },
            neutralButtonText = if (prefs.contains(setting.key)) stringResource(R.string.button_default) else null,
            items = items,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(setting.title, Modifier.weight(1f))
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(painterResource(R.drawable.ic_plus), contentDescription = "Add character")
                    }
                }
            },
            displayItem = { item ->
                var checked by rememberSaveable(item.name) { mutableStateOf(item.state) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val text = getTextForToolbarKey(item.name)
                    if (text != null) {
                        Text(text, Modifier.weight(1f))
                        IconButton(onClick = {
                            items = items.toMutableList().apply { remove(item) }
                        }) {
                            Icon(
                                painterResource(R.drawable.ic_bin),
                                contentDescription = "Delete",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        KeyboardIconsSet.instance.GetIconOrEmpty(item.name)
                        val localizedText = item.name.lowercase().getStringResourceOrName("", ctx)
                        val actualText = if (localizedText != item.name.lowercase()) localizedText
                        else item.name.lowercase().getStringResourceOrName("popup_keys_", ctx)
                        Text(actualText, Modifier.weight(1f))
                    }
                    Switch(
                        checked = checked,
                        onCheckedChange = { item.state = it; checked = it }
                    )
                }
            },
            getKey = { it.name }
        )
        if (showAddDialog) {
            var newText by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("Add text to toolbar") },
                text = {
                    TextField(
                        value = newText,
                        onValueChange = { newText = it },
                        label = { Text("Text") },
                        singleLine = true,
                        supportingText = { Text("Use | to specify cursor position, e.g. (|)") }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newText.isNotEmpty()) {
                                val name = "__str_${newText.encodeToHex()}"
                                if (items.none { it.name == name }) {
                                    items = (items + KeyAndState(name, true)).toMutableList()
                                }
                            }
                            showAddDialog = false
                        }
                    ) { Text(stringResource(android.R.string.ok)) }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }
    }
}

private class KeyAndState(var name: String, var state: Boolean)
