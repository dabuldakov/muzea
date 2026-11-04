package com.example.muzea.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.muzea.databinding.BottomSheetForwardPickerBinding
import com.example.muzea.domain.model.Chat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Шторка выбора чата для пересылки сообщения. Переиспользует [ChatAdapter]:
 * тап по чату возвращается в [OnChatForwardTargetSelected].
 */
@AndroidEntryPoint
class ForwardChatPickerBottomSheet : BottomSheetDialogFragment() {

    interface OnChatForwardTargetSelected {
        fun onForwardTargetSelected(chat: Chat)
    }

    private var _binding: BottomSheetForwardPickerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ForwardChatPickerViewModel by viewModels()
    private lateinit var adapter: ChatAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetForwardPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = ChatAdapter { chatUuid ->
            val chat = viewModel.state.value.chats.firstOrNull { it.chatUuid == chatUuid }
            if (chat != null) {
                (parentFragment as? OnChatForwardTargetSelected)?.onForwardTargetSelected(chat)
            }
            dismiss()
        }
        binding.rvForwardChats.layoutManager = LinearLayoutManager(requireContext())
        binding.rvForwardChats.adapter = adapter

        val currentChatUuid = arguments?.getString(ForwardChatPickerViewModel.ARG_CURRENT_CHAT)
        viewModel.load(currentChatUuid)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.collect { render(it) }
        }
    }

    private fun render(state: ForwardPickerUiState) {
        adapter.updateList(state.chats)
        binding.progressForward.visibility =
            if (state.isLoading && state.chats.isEmpty()) View.VISIBLE else View.GONE
        binding.tvForwardEmpty.visibility =
            if (!state.isLoading && state.chats.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(currentChatUuid: String?): ForwardChatPickerBottomSheet =
            ForwardChatPickerBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ForwardChatPickerViewModel.ARG_CURRENT_CHAT, currentChatUuid)
                }
            }
    }
}
