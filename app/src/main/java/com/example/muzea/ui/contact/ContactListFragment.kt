package com.example.muzea.ui.contact

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.muzea.domain.model.Chat
import com.example.muzea.domain.model.Contact
import com.example.muzea.databinding.FragmentContactListBinding
import com.example.muzea.ui.navigation.navigator
import com.example.muzea.utils.NetworkResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ContactListFragment : Fragment() {

    private companion object {
        /**
         * Интервал опроса статусов контактов.
         *
         * Сервер держит «онлайн» 45 секунд после последнего heartbeat, поэтому
         * 20 секунд дают запас: пара пропущенных запросов не гасит индикатор.
         */
        const val PRESENCE_POLL_INTERVAL_MS = 20_000L
    }

    private var _binding: FragmentContactListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: ContactViewModel by viewModels()
    private lateinit var adapter: ContactAdapter
    private var isAddingContact = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContactListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupSwipeRefresh()
        setupFab()
        observeUiState()
        observeAddContact()
        observeCreateChat()
        startPresencePolling()
        loadContacts()
    }

    /**
     * Опрос статусов, пока экран на переднем плане.
     *
     * repeatOnLifecycle(STARTED) сам останавливает и возобновляет цикл, поэтому
     * в фоне запросов нет, а при возврате на экран первый запрос уходит сразу,
     * не дожидаясь интервала. Heartbeat при этом шлёт MainActivity: он нужен
     * всему приложению, а не только этому экрану.
     */
    private fun startPresencePolling() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    viewModel.refreshPresence()
                    delay(PRESENCE_POLL_INTERVAL_MS)
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ContactAdapter { contact -> onContactClick(contact) }
        binding.recyclerViewContacts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ContactListFragment.adapter
        }
    }

    private fun onContactClick(contact: Contact) {
        val userUuid = contact.contactUserUuid
        if (userUuid.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Cannot create chat: no user uuid", Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.openPrivateChat(userUuid)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener { loadContacts() }
    }

    private fun setupFab() {
        binding.fabAddContact.setOnClickListener { showAddContactDialog() }
    }

    private fun showAddContactDialog() {
        val input = EditText(requireContext())
        input.hint = "Enter username"

        AlertDialog.Builder(requireContext())
            .setTitle("Add contact")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val username = input.text.toString().trim()
                if (username.isEmpty()) {
                    Toast.makeText(requireContext(), "Enter a username", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.addContact(username)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadContacts() {
        viewModel.loadContacts()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { render(it) }
        }
    }

    private fun render(state: ContactListUiState) {
        binding.swipeRefresh.isRefreshing = false
        binding.progressBar.visibility =
            if (state.isLoading && state.contacts.isEmpty()) View.VISIBLE else View.GONE

        adapter.updateList(state.contacts)
        val hasContacts = state.contacts.isNotEmpty()

        if (!hasContacts && state.error != null) {
            binding.tvError.text = state.error
            binding.tvError.visibility = View.VISIBLE
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerViewContacts.visibility = View.GONE
        } else {
            binding.tvError.visibility = View.GONE
            // Пустоту определяем по самому списку, а не по adapter.currentList:
            // ListAdapter.submitList применяет DiffUtil асинхронно.
            binding.tvEmpty.visibility = if (hasContacts || state.isLoading) View.GONE else View.VISIBLE
            binding.recyclerViewContacts.visibility = if (hasContacts) View.VISIBLE else View.GONE
            state.error?.let {
                Toast.makeText(requireContext(), "Error: $it", Toast.LENGTH_SHORT).show()
                viewModel.consumeError()
            }
        }
    }

    private fun observeAddContact() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.addContactResult.collect { result ->
                when (result) {
                    is NetworkResult.Loading -> isAddingContact = true
                    is NetworkResult.Success -> {
                        isAddingContact = false
                        Toast.makeText(requireContext(), "Contact added", Toast.LENGTH_SHORT).show()
                        loadContacts()
                    }
                    is NetworkResult.Error -> {
                        isAddingContact = false
                        Toast.makeText(requireContext(), result.message ?: "Failed", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun observeCreateChat() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.createChatResult.collect { result ->
                when (result) {
                    is NetworkResult.Loading -> Unit
                    is NetworkResult.Success -> result.data?.let { openConversation(it) }
                    is NetworkResult.Error -> {
                        Toast.makeText(requireContext(), result.message ?: "Failed to open chat", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun openConversation(chat: Chat) {
        navigator.openConversation(chat.chatUuid, chat.title ?: "Chat", chat.avatarUrl, 0L)
    }

    override fun onResume() {
        super.onResume()
        if (!isAddingContact) {
            loadContacts()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}