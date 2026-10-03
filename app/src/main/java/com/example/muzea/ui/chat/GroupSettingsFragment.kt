package com.example.muzea.ui.chat

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.muzea.R
import com.example.muzea.databinding.FragmentGroupSettingsBinding
import com.example.muzea.utils.AvatarLoader
import com.example.muzea.ui.navigation.navigator
import com.example.muzea.utils.NetworkResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@AndroidEntryPoint
class GroupSettingsFragment : Fragment() {

    private var _binding: FragmentGroupSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: GroupSettingsViewModel by viewModels()
    private lateinit var adapter: ParticipantAdapter
    private var chatUuid: String = ""
    private var chatAvatar: String? = null

    private var membersAdapter: GroupMemberAdapter? = null
    private var membersDialog: AlertDialog? = null

    private val pickAvatar = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && _binding != null) uploadAvatar(uri)
    }

    companion object {
        private const val ARG_CHAT_UUID = "chat_uuid"
        private const val ARG_CHAT_TITLE = "chat_title"
        private const val ARG_CHAT_AVATAR = "chat_avatar"

        fun newInstance(
            chatUuid: String,
            chatTitle: String,
            avatarUrl: String? = null
        ): GroupSettingsFragment {
            return GroupSettingsFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_CHAT_UUID, chatUuid)
                    putString(ARG_CHAT_TITLE, chatTitle)
                    putString(ARG_CHAT_AVATAR, avatarUrl)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        chatUuid = arguments?.getString(ARG_CHAT_UUID) ?: ""
        chatAvatar = arguments?.getString(ARG_CHAT_AVATAR)

        binding.tvTitle.text = arguments?.getString(ARG_CHAT_TITLE) ?: "Group settings"
        AvatarLoader.load(binding.ivHeaderAvatar, chatAvatar)
        binding.btnBack.setOnClickListener {
            navigator.back()
        }
        val changeAvatar = { pickAvatar.launch(arrayOf("image/jpeg", "image/png")) }
        binding.btnChangeAvatar.setOnClickListener { changeAvatar() }
        binding.ivHeaderAvatar.setOnClickListener { changeAvatar() }
        binding.btnAddMembers.setOnClickListener { showAddMembersDialog() }

        setupRecyclerView()
        observeViewModel()
        viewModel.loadParticipants()
    }

    private fun setupRecyclerView() {
        adapter = ParticipantAdapter(viewModel.myUserUuid)
        binding.recyclerViewParticipants.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@GroupSettingsFragment.adapter
        }
    }

    private fun showAddMembersDialog() {
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
            .setTitle("Add members")
            .setView(recyclerView)
            .setPositiveButton("Add members") { _, _ ->
                val uuids = adapter.selectedUserUuids.toList()
                if (uuids.isNotEmpty()) {
                    viewModel.addMembers(uuids)
                }
            }
            .setNegativeButton("Cancel", null)
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

    private fun uploadAvatar(uri: Uri) {
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            var file: File? = null
            try {
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val extension = when {
                    mimeType.equals("image/png", ignoreCase = true) -> ".png"
                    mimeType.equals("image/jpeg", ignoreCase = true) -> ".jpg"
                    else -> ".png"
                }
                withContext(Dispatchers.IO) {
                    val target = File.createTempFile("chat_avatar_", extension, context.cacheDir)
                    file = target
                    val input = context.contentResolver.openInputStream(uri) ?: error("Cannot open image")
                    input.use { source ->
                        target.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var total = 0L
                            while (true) {
                                val count = source.read(buffer)
                                if (count < 0) break
                                total += count
                                check(total <= 5 * 1024 * 1024) { "Choose an image up to 5 MB" }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                }
                Glide.with(binding.ivHeaderAvatar).load(file).circleCrop()
                    .placeholder(R.drawable.ic_default_avatar)
                    .error(R.drawable.ic_default_avatar)
                    .into(binding.ivHeaderAvatar)
                viewModel.uploadAvatar(file!!, mimeType)
                file = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Cannot open image", Toast.LENGTH_LONG).show()
            } finally {
                file?.delete()
            }
        }
    }

    private fun renderParticipants(state: GroupSettingsUiState) {
        binding.progressBar.visibility =
            if (state.isLoading && state.participants.isEmpty()) View.VISIBLE else View.GONE

        adapter.updateList(state.participants)
        val hasParticipants = state.participants.isNotEmpty()

        if (!hasParticipants && state.error != null) {
            binding.tvError.text = state.error
            binding.tvError.visibility = View.VISIBLE
            binding.recyclerViewParticipants.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = if (hasParticipants) View.GONE else View.VISIBLE
            binding.tvError.visibility = View.GONE
            binding.recyclerViewParticipants.visibility = View.VISIBLE
            state.error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                viewModel.consumeError()
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { renderParticipants(it) }
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
                    is NetworkResult.Success -> {
                        Toast.makeText(requireContext(), "Members added", Toast.LENGTH_SHORT).show()
                        viewModel.loadParticipants()
                    }
                    is NetworkResult.Error ->
                        Toast.makeText(requireContext(), result.message ?: "Error", Toast.LENGTH_SHORT).show()
                    is NetworkResult.Loading -> Unit
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.avatarState.collect { result ->
                when (result) {
                    is NetworkResult.Loading -> Unit
                    is NetworkResult.Success -> {
                        Toast.makeText(requireContext(), "Chat avatar updated", Toast.LENGTH_SHORT).show()
                    }
                    is NetworkResult.Error -> {
                        AvatarLoader.load(binding.ivHeaderAvatar, chatAvatar)
                        Toast.makeText(requireContext(), result.message ?: "Upload failed", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}