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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.R
import com.example.muzea.data.api.ChatRetrofitClient
import com.example.muzea.data.model.ChatResponse
import com.example.muzea.data.model.ContactResponse
import com.example.muzea.data.repository.ChatAuthManager
import com.example.muzea.data.repository.ChatRepository
import com.example.muzea.databinding.FragmentChatListBinding
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ChatListFragment : Fragment() {

    private var _binding: FragmentChatListBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: ChatViewModel
    private lateinit var adapter: ChatAdapter
    private var membersAdapter: GroupMemberAdapter? = null
    private var membersDialog: AlertDialog? = null

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
        initViewModel()
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

    private fun initViewModel() {
        val tokenManager = TokenManager(requireContext())
        val apiService = ChatRetrofitClient(tokenManager).apiService
        val chatRepository = ChatRepository(apiService, ChatAuthManager(apiService, tokenManager))
        viewModel = ChatViewModel(chatRepository)
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
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
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
            viewModel.chatsResult.collect { result ->
                when (result) {
                    is NetworkResult.Loading -> handleLoadingState()
                    is NetworkResult.Success -> handleSuccessState(result.data ?: emptyList())
                    is NetworkResult.Error -> handleErrorState(result.message ?: "Unknown error")
                }
            }
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

    private fun showAddMembersDialog(chat: ChatResponse) {
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

    private fun openGroupChat(chat: ChatResponse) {
        navigationToConversation(chat.chatUuid, chat.title ?: "Group", chat.avatarUrl)
    }

    private fun handleLoadingState() {
        // Не мигаем прогрессом при фоновом автообновлении, если список уже показан.
        if (!binding.swipeRefresh.isRefreshing && adapter.itemCount == 0) {
            binding.progressBar.visibility = View.VISIBLE
        }
    }

    private fun handleSuccessState(chats: List<ChatResponse>) {
        binding.progressBar.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false

        adapter.updateList(chats)
        updateEmptyState()
    }

    private fun handleErrorState(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false

        if (adapter.currentList.isEmpty()) {
            binding.tvError.text = message
            binding.tvError.visibility = View.VISIBLE
            binding.recyclerViewChats.visibility = View.GONE
        } else {
            Toast.makeText(requireContext(), "Error: $message", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateEmptyState() {
        if (adapter.currentList.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recyclerViewChats.visibility = View.GONE
            binding.tvError.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerViewChats.visibility = View.VISIBLE
            binding.tvError.visibility = View.GONE
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
    }
}