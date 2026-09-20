package com.example.muzea.ui.chat

import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.muzea.R
import com.example.muzea.data.api.ChatRetrofitClient
import com.example.muzea.data.repository.ChatAuthManager
import com.example.muzea.data.repository.ChatRepository
import com.example.muzea.databinding.FragmentGroupSettingsBinding
import com.example.muzea.utils.AvatarLoader
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class GroupSettingsFragment : Fragment() {

    private var _binding: FragmentGroupSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: GroupSettingsViewModel
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
            requireActivity().supportFragmentManager.popBackStack()
        }
        val changeAvatar = { pickAvatar.launch(arrayOf("image/jpeg", "image/png")) }
        binding.btnChangeAvatar.setOnClickListener { changeAvatar() }
        binding.ivHeaderAvatar.setOnClickListener { changeAvatar() }
        binding.btnAddMembers.setOnClickListener { showAddMembersDialog() }

        initViewModel()
        setupRecyclerView()
        observeViewModel()
        viewModel.loadParticipants()
    }

    private fun initViewModel() {
        val tokenManager = TokenManager(requireContext())
        val apiService = ChatRetrofitClient(tokenManager).apiService
        val chatRepository = ChatRepository(apiService, ChatAuthManager(apiService, tokenManager))
        viewModel = GroupSettingsViewModel(chatUuid, chatRepository)
    }

    private fun setupRecyclerView() {
        val tokenManager = TokenManager(requireContext())
        adapter = ParticipantAdapter(extractMyUserUuid(tokenManager))
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

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.participants.collect { result ->
                when (result) {
                    is NetworkResult.Loading -> {
                        if (adapter.currentList.isEmpty()) {
                            binding.progressBar.visibility = View.VISIBLE
                        }
                    }
                    is NetworkResult.Success -> {
                        binding.progressBar.visibility = View.GONE
                        val list = result.data ?: emptyList()
                        adapter.updateList(list)
                        binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                        binding.tvError.visibility = View.GONE
                        binding.recyclerViewParticipants.visibility = View.VISIBLE
                    }
                    is NetworkResult.Error -> {
                        binding.progressBar.visibility = View.GONE
                        if (adapter.currentList.isEmpty()) {
                            binding.tvError.text = result.message
                            binding.tvError.visibility = View.VISIBLE
                            binding.recyclerViewParticipants.visibility = View.GONE
                        } else {
                            Toast.makeText(requireContext(), result.message ?: "Error", Toast.LENGTH_SHORT).show()
                        }
                    }
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

    private fun extractMyUserUuid(tokenManager: TokenManager): String? {
        val token = tokenManager.getChatToken() ?: return null
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return null
            val decoded = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP)
            JSONObject(String(decoded, Charsets.UTF_8)).getString("sub")
        } catch (e: Exception) {
            null
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}