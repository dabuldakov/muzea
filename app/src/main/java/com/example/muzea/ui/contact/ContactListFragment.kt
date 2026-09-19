package com.example.muzea.ui.contact

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.muzea.R
import com.example.muzea.data.api.ChatRetrofitClient
import com.example.muzea.data.model.ContactResponse
import com.example.muzea.data.repository.ChatRepository
import com.example.muzea.databinding.FragmentContactListBinding
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import kotlinx.coroutines.launch

class ContactListFragment : Fragment() {

    private var _binding: FragmentContactListBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: ContactViewModel
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
        initViewModel()
        setupRecyclerView()
        setupSwipeRefresh()
        setupFab()
        observeContacts()
        observeAddContact()
        loadContacts()
    }

    private fun initViewModel() {
        val tokenManager = TokenManager(requireContext())
        val apiService = ChatRetrofitClient(tokenManager).apiService
        val chatRepository = ChatRepository(apiService, tokenManager)
        viewModel = ContactViewModel(chatRepository)
    }

    private fun setupRecyclerView() {
        adapter = ContactAdapter()
        binding.recyclerViewContacts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@ContactListFragment.adapter
        }
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

    private fun observeContacts() {
        lifecycleScope.launch {
            viewModel.contactsResult.collect { result ->
                when (result) {
                    is NetworkResult.Loading -> {
                        if (!binding.swipeRefresh.isRefreshing) {
                            binding.progressBar.visibility = View.VISIBLE
                        }
                    }
                    is NetworkResult.Success -> handleSuccessState(result.data ?: emptyList())
                    is NetworkResult.Error -> handleErrorState(result.message ?: "Unknown error")
                }
            }
        }
    }

    private fun observeAddContact() {
        lifecycleScope.launch {
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

    private fun handleSuccessState(contacts: List<ContactResponse>) {
        binding.progressBar.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false

        adapter.updateList(contacts)
        updateEmptyState()
    }

    private fun handleErrorState(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false

        if (adapter.currentList.isEmpty()) {
            binding.tvError.text = message
            binding.tvError.visibility = View.VISIBLE
            binding.recyclerViewContacts.visibility = View.GONE
        } else {
            Toast.makeText(requireContext(), "Error: $message", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateEmptyState() {
        if (adapter.currentList.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recyclerViewContacts.visibility = View.GONE
            binding.tvError.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerViewContacts.visibility = View.VISIBLE
            binding.tvError.visibility = View.GONE
        }
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