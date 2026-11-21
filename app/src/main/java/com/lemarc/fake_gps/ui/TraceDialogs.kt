package com.lemarc.fake_gps.ui

import android.content.Context
import android.text.format.DateFormat
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.lemarc.fake_gps.R
import com.lemarc.fake_gps.data.TraceStore
import com.lemarc.fake_gps.databinding.DialogTraceNameBinding
import java.util.Date

/** Boîtes de dialogue des traces enregistrées : nommer, lister, charger, supprimer. */
object TraceDialogs {

    /**
     * Demande le nom de la trace à enregistrer ; si [exists] indique qu'il est déjà pris,
     * demande confirmation avant de remplacer.
     */
    fun askName(context: Context, defaultName: String, exists: (String) -> Boolean, onSave: (String) -> Unit) {
        val content = DialogTraceNameBinding.inflate(LayoutInflater.from(context))
        content.nameInput.setText(defaultName)
        content.nameInput.selectAll()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.menu_save_trace)
            .setView(content.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = content.nameInput.text?.toString()?.trim().orEmpty().ifEmpty { defaultName }
                if (exists(name)) {
                    MaterialAlertDialogBuilder(context)
                        .setMessage(context.getString(R.string.trace_exists, TraceStore.sanitize(name)))
                        .setPositiveButton(R.string.replace) { _, _ -> onSave(name) }
                        .setNegativeButton(R.string.cancel, null)
                        .show()
                } else {
                    onSave(name)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Liste des traces (la plus récente en premier) ; un appui propose de charger ou supprimer. */
    fun showList(
        context: Context,
        traces: List<TraceStore.Trace>,
        onLoad: (TraceStore.Trace) -> Unit,
        onDelete: (TraceStore.Trace) -> Unit,
    ) {
        val dateFormat = DateFormat.getDateFormat(context)
        val timeFormat = DateFormat.getTimeFormat(context)
        val labels = traces.map {
            val date = Date(it.modified)
            "${it.name}\n${dateFormat.format(date)} ${timeFormat.format(date)}"
        }.toTypedArray()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.menu_my_traces)
            .setItems(labels) { _, which -> showActions(context, traces[which], onLoad, onDelete) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showActions(
        context: Context,
        trace: TraceStore.Trace,
        onLoad: (TraceStore.Trace) -> Unit,
        onDelete: (TraceStore.Trace) -> Unit,
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(trace.name)
            .setPositiveButton(R.string.load) { _, _ -> onLoad(trace) }
            .setNegativeButton(R.string.delete) { _, _ -> onDelete(trace) }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }
}
