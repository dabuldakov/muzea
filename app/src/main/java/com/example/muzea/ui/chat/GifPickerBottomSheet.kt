package com.example.muzea.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.R
import com.example.muzea.databinding.BottomSheetGifPickerBinding
import com.example.muzea.domain.model.Gif
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Пикер гифок: вкладки «Мои»/«Этот чат»/«Поиск», сетка 3 колонки, поиск и
 * постраничная догрузка. Выбор гифки возвращается в [OnGifSelected].
 */
@AndroidEntryPoint
class GifPickerBottomSheet : BottomSheetDialogFragment() {

    interface OnGifSelected {
        fun onGifSelected(gif: Gif)
    }

    private var _binding: BottomSheetGifPickerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GifPickerViewModel by viewModels()
    private lateinit var adapter: GifPickerAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetGifPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = GifPickerAdapter { gif ->
            (parentFragment as? OnGifSelected)?.onGifSelected(gif)
            dismiss()
        }
        binding.rvGifs.layoutManager = GridLayoutManager(requireContext(), GRID_SPAN)
        binding.rvGifs.adapter = adapter
        binding.rvGifs.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - GRID_SPAN) {
                    viewModel.loadNextPage()
                }
            }
        })

        setupTabs()

        binding.etGifSearch.setOnEditorActionListener { _, _, _ ->
            viewModel.setQuery(binding.etGifSearch.text?.toString()?.trim().orEmpty())
            true
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { render(it) }
        }
    }

    private fun setupTabs() {
        val tabs = listOf(
            R.string.gif_tab_recent to GifTab.RECENT,
            R.string.gif_tab_chat to GifTab.CHAT,
            R.string.gif_tab_search to GifTab.SEARCH
        )
        binding.tabGifTabs.removeAllTabs()
        tabs.forEach { (titleRes, tab) ->
            binding.tabGifTabs.addTab(binding.tabGifTabs.newTab().setText(titleRes).setTag(tab))
        }
        binding.tabGifTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                (tab.tag as? GifTab)?.let(viewModel::selectTab)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun render(state: GifPickerUiState) {
        adapter.submitList(state.items)
        binding.progressGif.visibility =
            if (state.isLoading && state.items.isEmpty()) View.VISIBLE else View.GONE
        binding.tvGifEmpty.visibility =
            if (!state.isLoading && state.items.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val GRID_SPAN = 3

        fun newInstance(chatUuid: String): GifPickerBottomSheet =
            GifPickerBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(GifPickerViewModel.ARG_CHAT_UUID, chatUuid)
                }
            }
    }
}
