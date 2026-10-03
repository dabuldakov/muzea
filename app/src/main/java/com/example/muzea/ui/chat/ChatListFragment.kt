package com.example.muzea.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.domain.model.Chat
import com.example.muzea.domain.model.Contact
import com.example.muzea.data.repository.ChatMessagesCache
import com.example.muzea.domain.repository.MessageRepository
import com.example.muzea.databinding.FragmentChatListBinding
import com.example.muzea.ui.openDetailScreen
import com.example.muzea.utils.NetworkResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ChatListFragment : Fragment() {

    private var _binding: FragmentChatListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ChatViewModel by viewModels()

    @Inject
    lateinit var messageRepository: MessageRepository

    private lateinit var adapter: ChatAdapter
    private var membersAdapter: GroupMemberAdapter? = null
    private var membersDialog: AlertDialog? = null
    private var prefetchJob: Job? = null

    /**
     * Последняя ошибка загрузки списка. Показывается вместо пустого состояния,
     * чтобы отличать «чатов нет» от «не удалось загрузить».
     */

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSwipeRefresh()
        setupCreateGroupFab()
        observeViewModel()
        loadChats()
        startAutoRefresh()
    }

    private fun startAutoRefresh() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    delay(AUTO_REFRESH_MS)
                    loadChats()
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatAdapter(
            onItemClick = { chatUuid ->
                openChat(chatUuid)
            }
        )
        binding.recyclerViewChats.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ChatListFragment.adapter
        }
    }

    private fun setupCreateGroupFab() {
        binding.fabCreateGroup.setOnClickListener {
            showCreateGroupDialog()
        }
    }

    private fun openChat(chatUuid: String) {
        val chat = adapter.currentList.firstOrNull { it.chatUuid == chatUuid }
        val title = chat?.title ?: "Chat"
        navigationToConversation(chatUuid, title, chat?.avatarUrl)
    }

    private fun navigationToConversation(chatUuid: String, title: String, avatarUrl: String?) {
        val fragment = ChatConversationFragment.newInstance(
            chatUuid,
            title,
            avatarUrl,
            0L
        )
        openDetailScreen(fragment)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadChats()
        }
    }

    private fun loadChats() {
        viewModel.loadChats()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { renderState(it) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.createGroupChatResult.collect { result ->
                when (result) {
                    is NetworkResult.Success -> result.data?.let { showAddMembersDialog(it) }
                    is NetworkResult.Error ->
                        Toast.makeText(requireContext(), result.message ?: "Error", Toast.LENGTH_SHORT).show()
                    is NetworkResult.Loading -> Unit
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.contactsResult.collect { result ->
                when (result) {
                    is NetworkResult.Success -> {
                        val adapter = membersAdapter ?: return@collect
                        val contacts = result.data ?: emptyList()
                        if (contacts.isEmpty()) {
                            Toast.makeText(requireContext(), "No contacts to add", Toast.LENGTH_SHORT).show()
                        }
                        adapter.updateList(contacts)
                        membersDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                    }
                    is NetworkResult.Error ->
                        Toast.makeText(requireContext(), result.message ?: "Error", Toast.LENGTH_SHORT).show()
                    is NetworkResult.Loading -> Unit
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.addParticipantsResult.collect { result ->
                when (result) {
                    is NetworkResult.Success ->
                        Toast.makeText(requireContext(), "Members added", Toast.LENGTH_SHORT).show()
                    is NetworkResult.Error ->
                        Toast.makeText(requireContext(), result.message ?: "Error", Toast.LENGTH_SHORT).show()
                    is NetworkResult.Loading -> Unit
                }
            }
        }
    }

    private fun showCreateGroupDialog() {
        val input = EditText(requireContext()).apply {
            hint = "Group name"
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Create group chat")
            .setView(input)
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel", null)
            .create()

        input.doOnTextChanged { text, _, _, _ ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                ?.isEnabled = !text.isNullOrBlank()
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                ?.setOnClickListener {
                    val title = input.text.toString().trim()
                    if (title.isNotEmpty()) {
                        viewModel.createGroupChat(title, emptyList())
                        dialog.dismiss()
                    }
                }
        }

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
    }

    private fun showAddMembersDialog(chat: Chat) {
        val recyclerView = RecyclerView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(380)
            )
            layoutManager = LinearLayoutManager(requireContext())
        }
        val adapter = GroupMemberAdapter { }
        membersAdapter = adapter
        recyclerView.adapter = adapter

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(chat.title ?: "Group chat")
            .setView(recyclerView)
            .setPositiveButton("Add members") { _, _ ->
                val uuids = adapter.selectedUserUuids.toList()
                if (uuids.isEmpty()) {
                    openGroupChat(chat)
                } else {
                    viewModel.addGroupParticipants(chat.chatUuid, uuids)
                    openGroupChat(chat)
                }
            }
            .setNegativeButton("Later") { _, _ ->
                openGroupChat(chat)
            }
            .setOnCancelListener { openGroupChat(chat) }
            .create()
        membersDialog = dialog

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
        }

        dialog.setOnDismissListener {
            membersAdapter = null
            membersDialog = null
        }
        dialog.show()
        viewModel.loadContacts()
    }

    private fun openGroupChat(chat: Chat) {
        navigationToConversation(chat.chatUuid, chat.title ?: "Group", chat.avatarUrl)
    }

    /**
     * Прогрузка переписок в фоне.
     *
     * Пока пользователь смотрит список, по одному в фоне подтягиваем сообщения
     * тех чатов, которых ещё нет в кэше. Тогда при входе переписка показывается
     * мгновенно, а не с индикатором загрузки. Уже закэшированные и уже
     * запущенные чаты пропускаем, список ограничиваем, чтобы не заваливать
     * сервер запросами.
     */
    private fun prefetchChatMessages(chats: List<Chat>) {
        val targets = chats.asSequence()
            .map { it.chatUuid }
            .filter { it.isNotBlank() && !ChatMessagesCache.has(it) }
            .take(MAX_PREFETCH_PER_PASS)
            .toList()

        if (targets.isEmpty() || prefetchJob?.isActive == true) return

        prefetchJob = viewLifecycleOwner.lifecycleScope.launch {
            for (chatUuid in targets) {
                messageRepository.loadMessages(chatUuid).collect { }
                delay(PREFETCH_DELAY_MS)
            }
        }
    }

    /**
     * Единственное место, где состояние списка превращается в видимость view'ов.
     * Решение принимается по данным из [chatListViewState], а не по состоянию
     * адаптера: submitList() обновляет список асинхронно.
     */
    private fun renderState(state: ChatListUiState) {
        binding.swipeRefresh.isRefreshing = false
        adapter.updateList(state.chats)
        prefetchChatMessages(state.chats)

        when (chatListViewState(state.chats, state.isLoading, state.error)) {
            ChatListViewState.LIST -> {
                binding.tvEmpty.visibility = View.GONE
                binding.tvError.visibility = View.GONE
                binding.recyclerViewChats.visibility = View.VISIBLE
                binding.progressBar.visibility = View.GONE
            }

            ChatListViewState.LOADING -> {
                binding.tvEmpty.visibility = View.GONE
                binding.tvError.visibility = View.GONE
                binding.recyclerViewChats.visibility = View.GONE
                binding.progressBar.visibility = View.VISIBLE
            }

            ChatListViewState.ERROR -> {
                binding.tvEmpty.visibility = View.GONE
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = state.error
                binding.recyclerViewChats.visibility = View.GONE
                binding.progressBar.visibility = View.GONE
            }

            ChatListViewState.EMPTY -> {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvError.visibility = View.GONE
                binding.recyclerViewChats.visibility = View.GONE
                binding.progressBar.visibility = View.GONE
            }
        }

        // Список из кэша остаётся на экране — сообщаем тостом и не затираем его.
        if (state.error != null && state.chats.isNotEmpty()) {
            Toast.makeText(requireContext(), state.error, Toast.LENGTH_SHORT).show()
            viewModel.chatsErrorShown()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        loadChats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        private const val AUTO_REFRESH_MS = 8_000L
        private const val MAX_PREFETCH_PER_PASS = 20
        private const val PREFETCH_DELAY_MS = 250L
    }
}