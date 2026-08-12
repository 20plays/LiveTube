package com.livetube.player.ui

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.livetube.player.R
import com.livetube.player.databinding.FragmentLibraryBinding
import com.livetube.player.ui.adapters.LibraryAdapter
import kotlinx.coroutines.launch

class LibraryFragment : Fragment(R.layout.fragment_library) {

    private lateinit var binding: FragmentLibraryBinding

    private val vm: LibraryViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentLibraryBinding.bind(view)

        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.menu_refresh) {
                vm.refreshAll()
                true
            } else false
        }

        binding.fab.setOnClickListener {
            AddItemDialog().show(childFragmentManager, "add_item")
        }

        val adapter = LibraryAdapter { item ->
            findNavController().navigate(
                R.id.detail,
                bundleOf("itemId" to item.id),
            )
        }
        binding.rv.layoutManager = LinearLayoutManager(requireContext())
        binding.rv.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.items.collect { items ->
                        adapter.submitList(items)
                        binding.empty.isVisible = items.isEmpty()
                    }
                }
                launch {
                    vm.busy.collect { busy ->
                        binding.fab.isEnabled = !busy
                    }
                }
                launch {
                    vm.message.collect { text ->
                        Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}