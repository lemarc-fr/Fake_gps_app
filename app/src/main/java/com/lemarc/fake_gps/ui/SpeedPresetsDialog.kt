package com.lemarc.fake_gps.ui

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lemarc.fake_gps.R
import com.lemarc.fake_gps.databinding.DialogSpeedPresetsBinding
import com.lemarc.fake_gps.databinding.ItemSpeedPresetBinding
import com.lemarc.fake_gps.engine.SpeedExpression
import com.lemarc.fake_gps.engine.SpeedPresets

/** Liste des signaux de vitesse préenregistrés, avec un aperçu de chacun sur 5 minutes. */
object SpeedPresetsDialog {

    fun show(context: Context, onSelected: (SpeedPresets.Preset) -> Unit) {
        val inflater = LayoutInflater.from(context)
        val content = DialogSpeedPresetsBinding.inflate(inflater)
        val lineColor = MaterialColors.getColor(content.root, androidx.appcompat.R.attr.colorPrimary)
        val axisColor = MaterialColors.getColor(content.root, com.google.android.material.R.attr.colorOutline)

        lateinit var dialog: AlertDialog
        for (preset in SpeedPresets.ALL) {
            val row = ItemSpeedPresetBinding.inflate(inflater, content.presetList, false)
            row.name.setText(preset.nameRes)
            row.expression.text = preset.expression
            row.sparkline.setValues(SpeedPresets.sample(SpeedExpression.parse(preset.expression)), lineColor, axisColor)
            row.root.setOnClickListener {
                onSelected(preset)
                dialog.dismiss()
            }
            content.presetList.addView(row.root)
        }
        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.presets_title)
            .setView(content.root)
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
