package com.lemarc.fake_gps.ui

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.lemarc.fake_gps.R
import com.lemarc.fake_gps.data.TraceStore
import com.lemarc.fake_gps.engine.SpeedExpression
import com.lemarc.fake_gps.engine.SpeedPresets
import com.lemarc.fake_gps.map.MapStyle
import java.util.Date

/** Boîtes de dialogue de l'écran principal. Chacune se ferme via [onDismiss]. */

@Composable
fun SpeedHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.speed_help_title)) },
        text = { Text(stringResource(R.string.speed_help), Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
    )
}

@Composable
fun MapStyleDialog(current: MapStyle, onSelect: (MapStyle) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.map_style)) },
        text = {
            Column(Modifier.selectableGroup()) {
                MapStyle.entries.forEach { style ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = style == current, role = Role.RadioButton) { onSelect(style) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = style == current, onClick = null)
                        Text(stringResource(style.labelRes), Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Signaux de vitesse préenregistrés, avec un aperçu de chacun sur 5 minutes. */
@Composable
fun SpeedPresetsDialog(onSelect: (SpeedPresets.Preset) -> Unit, onDismiss: () -> Unit) {
    val samples = remember {
        SpeedPresets.ALL.map { it to SpeedPresets.sample(SpeedExpression.parse(it.expression)) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.presets_title)) },
        text = {
            LazyColumn {
                item { Text(stringResource(R.string.presets_subtitle), Modifier.padding(bottom = 4.dp)) }
                items(samples, key = { it.first.expression }) { (preset, values) ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(preset) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(stringResource(preset.nameRes), style = MaterialTheme.typography.titleSmall)
                        Text(
                            preset.expression,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        Sparkline(
                            values,
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .height(36.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Saisie du nom de la trace à enregistrer (nom par défaut présélectionné). */
@Composable
fun SaveTraceDialog(defaultName: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(TextFieldValue(defaultName, TextRange(0, defaultName.length))) }
    val focusRequester = remember { FocusRequester() }
    val confirm = { onSave(name.text.trim().ifEmpty { defaultName }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_save_trace)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.trace_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        confirmButton = { TextButton(onClick = confirm) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
fun ReplaceTraceDialog(name: String, onReplace: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(R.string.trace_exists, TraceStore.sanitize(name))) },
        confirmButton = { TextButton(onClick = onReplace) { Text(stringResource(R.string.replace)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Traces enregistrées, la plus récente en premier. */
@Composable
fun TraceListDialog(traces: List<TraceStore.Trace>, onSelect: (TraceStore.Trace) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val dateFormat = remember { DateFormat.getDateFormat(context) }
    val timeFormat = remember { DateFormat.getTimeFormat(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_my_traces)) },
        text = {
            LazyColumn {
                items(traces, key = { it.file.path }) { trace ->
                    val date = Date(trace.modified)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(trace) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(trace.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${dateFormat.format(date)} ${timeFormat.format(date)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun TraceActionsDialog(trace: TraceStore.Trace, onLoad: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(trace.name) },
        confirmButton = { TextButton(onClick = onLoad) { Text(stringResource(R.string.load)) } },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
            }
        },
    )
}

/** L'appli n'est pas l'application de position fictive : explique comment la choisir. */
@Composable
fun MockSettingsDialog(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.mock_title)) },
        text = { Text(stringResource(R.string.mock_message)) },
        confirmButton = { TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.mock_open_settings)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun MessageDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
    )
}
