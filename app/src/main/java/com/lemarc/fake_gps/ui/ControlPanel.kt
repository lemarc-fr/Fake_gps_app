package com.lemarc.fake_gps.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lemarc.fake_gps.R
import com.lemarc.fake_gps.data.routing.RoutingProfile

/** Actions déclenchées depuis le panneau de commandes. */
class ControlPanelActions(
    val onSpeedTextChange: (TextFieldValue) -> Unit,
    val onShowSpeedPresets: () -> Unit,
    val onShowSpeedHelp: () -> Unit,
    val onLoopChange: (Boolean) -> Unit,
    val onRoutingProfileChange: (RoutingProfile) -> Unit,
    val onSaveTrace: () -> Unit,
    val onShowTraces: () -> Unit,
    val onImportGpx: () -> Unit,
    val onExportGpx: () -> Unit,
    val onUndo: () -> Unit,
    val onClear: () -> Unit,
    val onTeleport: () -> Unit,
    val onMove: () -> Unit,
    val onStop: () -> Unit,
)

/** Panneau du bas : état, vitesse, options de tracé, édition du trajet et commandes de simulation. */
@Composable
fun ControlPanel(
    status: String,
    info: String,
    speedText: TextFieldValue,
    speedError: String?,
    loop: Boolean,
    routingProfile: RoutingProfile,
    stopEnabled: Boolean,
    actions: ControlPanelActions,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(status, style = MaterialTheme.typography.titleMedium)
            Text(info, style = MaterialTheme.typography.bodySmall)
            SpeedField(speedText, speedError, actions)
            LoopCheckbox(loop, actions.onLoopChange)
            RoutingSelector(routingProfile, actions.onRoutingProfileChange)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TracesMenuButton(actions, Modifier.weight(1f))
                PanelButton(R.string.btn_undo, actions.onUndo, ButtonStyle.OUTLINED)
                PanelButton(R.string.btn_clear, actions.onClear, ButtonStyle.OUTLINED)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelButton(R.string.btn_teleport, actions.onTeleport, ButtonStyle.TONAL)
                PanelButton(R.string.btn_move, actions.onMove, ButtonStyle.FILLED)
                PanelButton(R.string.btn_stop, actions.onStop, ButtonStyle.OUTLINED, enabled = stopEnabled)
            }
        }
    }
}

@Composable
private fun SpeedField(text: TextFieldValue, error: String?, actions: ControlPanelActions) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = text,
        onValueChange = actions.onSpeedTextChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        label = { Text(stringResource(R.string.speed_hint)) },
        leadingIcon = {
            IconButton(onClick = actions.onShowSpeedPresets) {
                Icon(painterResource(R.drawable.ic_signal), stringResource(R.string.presets_title))
            }
        },
        trailingIcon = {
            IconButton(onClick = actions.onShowSpeedHelp) {
                Icon(painterResource(R.drawable.ic_help), stringResource(R.string.speed_help_title))
            }
        },
        suffix = { Text(stringResource(R.string.speed_unit)) },
        supportingText = {
            Text(
                if (error != null) stringResource(R.string.speed_invalid, error) else stringResource(R.string.speed_helper),
            )
        },
        isError = error != null,
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
    )
}

@Composable
private fun LoopCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Text(stringResource(R.string.loop))
    }
}

@Composable
private fun RoutingSelector(selected: RoutingProfile, onSelect: (RoutingProfile) -> Unit) {
    val profiles = RoutingProfile.entries
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        profiles.forEachIndexed { index, profile ->
            val description = stringResource(profile.descriptionRes)
            SegmentedButton(
                selected = profile == selected,
                onClick = { onSelect(profile) },
                shape = SegmentedButtonDefaults.itemShape(index, profiles.size),
                icon = {},
                modifier = Modifier.semantics { contentDescription = description },
                label = {
                    // Les emojis 🚗 🚲 🚶 sont peu lisibles à la taille de texte par défaut.
                    val emoji = profile != RoutingProfile.STRAIGHT
                    Text(stringResource(profile.labelRes), maxLines = 1, fontSize = if (emoji) 20.sp else TextUnit.Unspecified)
                },
            )
        }
    }
}

@Composable
private fun TracesMenuButton(actions: ControlPanelActions, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = ButtonPadding,
        ) {
            ButtonLabel(R.string.btn_traces)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                R.string.menu_save_trace to actions.onSaveTrace,
                R.string.menu_my_traces to actions.onShowTraces,
                R.string.menu_import_gpx to actions.onImportGpx,
                R.string.menu_export_gpx to actions.onExportGpx,
            ).forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    onClick = {
                        expanded = false
                        action()
                    },
                )
            }
        }
    }
}

private enum class ButtonStyle { FILLED, TONAL, OUTLINED }

/** Les boutons sont trois par ligne : marges réduites et libellé sur deux lignes au besoin. */
private val ButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)

@Composable
private fun RowScope.PanelButton(
    @StringRes label: Int,
    onClick: () -> Unit,
    style: ButtonStyle,
    enabled: Boolean = true,
) {
    val modifier = Modifier.weight(1f)
    when (style) {
        ButtonStyle.FILLED -> Button(onClick, modifier, enabled, contentPadding = ButtonPadding) { ButtonLabel(label) }
        ButtonStyle.TONAL ->
            FilledTonalButton(onClick, modifier, enabled, contentPadding = ButtonPadding) { ButtonLabel(label) }
        ButtonStyle.OUTLINED ->
            OutlinedButton(onClick, modifier, enabled, contentPadding = ButtonPadding) { ButtonLabel(label) }
    }
}

@Composable
private fun ButtonLabel(@StringRes label: Int) {
    Text(stringResource(label), textAlign = TextAlign.Center, maxLines = 2)
}

@get:StringRes
private val RoutingProfile.labelRes: Int
    get() = when (this) {
        RoutingProfile.STRAIGHT -> R.string.routing_straight
        RoutingProfile.CAR -> R.string.routing_car_icon
        RoutingProfile.BIKE -> R.string.routing_bike_icon
        RoutingProfile.FOOT -> R.string.routing_foot_icon
    }

@get:StringRes
private val RoutingProfile.descriptionRes: Int
    get() = when (this) {
        RoutingProfile.STRAIGHT -> R.string.routing_straight
        RoutingProfile.CAR -> R.string.routing_car
        RoutingProfile.BIKE -> R.string.routing_bike
        RoutingProfile.FOOT -> R.string.routing_foot
    }
