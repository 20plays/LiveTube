package com.livetube.player.ui

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.livetube.player.R
import com.livetube.player.databinding.DialogAddBinding

class AddItemDialog : DialogFragment() {

    private val vm: LibraryViewModel by activityViewModels()

    private lateinit var binding: DialogAddBinding
    private var alert: androidx.appcompat.app.AlertDialog? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogAddBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.add_library_item)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok, null)
            .create()
        alert = dialog

        dialog.setOnShowListener {
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener { submit() }
        }
        return dialog
    }

    private fun submit() {
        val raw = binding.urlInput.text?.toString()?.trim().orEmpty()
        if (raw.isEmpty()) return
        val dlg = alert ?: return
        val addButton = dlg.getButton(Dialog.BUTTON_POSITIVE)
        addButton.isEnabled = false
        vm.addUrl(raw) { error ->
            if (error == null) {
                dlg.dismiss()
            } else {
                binding.error.text = error
                binding.error.isVisible = true
                addButton.isEnabled = true
            }
        }
    }
}