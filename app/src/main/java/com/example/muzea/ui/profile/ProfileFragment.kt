package com.example.muzea.ui.profile

import android.os.Bundle
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.muzea.data.api.ChatRetrofitClient
import com.example.muzea.data.repository.ChatAuthManager
import com.example.muzea.data.repository.ChatRepository
import com.example.muzea.utils.AvatarLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.muzea.R
import com.example.muzea.databinding.FragmentProfileBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.example.muzea.data.api.RetrofitClient
import com.example.muzea.data.repository.UserRepository
import com.example.muzea.ui.auth.LoginActivity
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ProfileViewModel
    private var isEditing = false
    private lateinit var avatarViewModel: AvatarViewModel
    private val pickAvatar = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && _binding != null) uploadAvatar(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Создаем ViewModel вручную
        val tokenManager = TokenManager(requireContext())
        val apiService = RetrofitClient(tokenManager).apiService
        val userRepository = UserRepository(apiService)
        val chatApi = ChatRetrofitClient(tokenManager).apiService
        val chatRepository = ChatRepository(chatApi, ChatAuthManager(chatApi, tokenManager))
        viewModel = ProfileViewModel(
            requireActivity().application,
            userRepository,
            chatRepository,
            tokenManager
        )
        avatarViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AvatarViewModel(chatRepository) as T
        })[AvatarViewModel::class.java]

        binding.btnChangeAvatar.setOnClickListener { pickAvatar.launch(arrayOf("image/jpeg", "image/png")) }
        binding.btnDeleteAvatar.setOnClickListener { avatarViewModel.delete() }
        binding.btnRetryAvatar.setOnClickListener { avatarViewModel.load() }
        viewLifecycleOwner.lifecycleScope.launch {
            avatarViewModel.state.collect { result ->
                val loading = result is NetworkResult.Loading
                binding.avatarProgress.visibility = if (loading) View.VISIBLE else View.GONE
                binding.btnChangeAvatar.isEnabled = !loading
                binding.btnDeleteAvatar.isEnabled = !loading
                binding.tvAvatarError.visibility = if (result is NetworkResult.Error) View.VISIBLE else View.GONE
                binding.btnRetryAvatar.visibility = if (result is NetworkResult.Error) View.VISIBLE else View.GONE
                when (result) {
                    is NetworkResult.Success -> {
                        AvatarLoader.load(binding.ivAvatar, result.data?.avatarUrl)
                        binding.btnDeleteAvatar.isEnabled = !result.data?.avatarUrl.isNullOrBlank()
                    }
                    is NetworkResult.Error -> binding.tvAvatarError.text = result.message
                    is NetworkResult.Loading -> Unit
                }
            }
        }

        setupClickListeners()
        observeViewModel()
        viewModel.loadUserProfile()
    }

    private fun setupClickListeners() {
        binding.btnEditProfile.setOnClickListener {
            if (isEditing) {
                saveChanges()
            } else {
                enableEditing()
            }
        }

        binding.btnLogout.setOnClickListener {
            logout()
        }

        binding.btnOperatorInfo.setOnClickListener {
            startActivity(android.content.Intent(requireContext(), OperatorInfoActivity::class.java))
        }

        binding.btnDeleteAccount.setOnClickListener {
            confirmDeleteAccount()
        }
    }

    private fun confirmDeleteAccount() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_account_title)
            .setMessage(R.string.delete_account_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deleteAccount() }
            .show()
    }

    private fun uploadAvatar(uri: Uri) {
        val context = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            var file: File? = null
            try {
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                withContext(Dispatchers.IO) {
                    val target = File.createTempFile("avatar_", ".image", context.cacheDir)
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
                avatarViewModel.upload(file!!, mimeType)
                file = null // ViewModel owns the temporary file until the request completes.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Cannot open image", Toast.LENGTH_LONG).show()
            } finally {
                file?.delete()
            }
        }
    }

    private fun enableEditing() {
        isEditing = true
        binding.btnEditProfile.text = "Save"
        binding.etFullName.isEnabled = true
        binding.etEmail.isEnabled = true
    }

    private fun saveChanges() {
        val fullName = binding.etFullName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()

        if (fullName.isEmpty()) {
            binding.etFullName.error = "Full name required"
            return
        }

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.error = "Valid email required"
            return
        }

        viewModel.updateUserProfile(fullName, email)
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.userProfileResult.collect { result ->
                when (result) {
                    is NetworkResult.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    is NetworkResult.Success -> {
                        binding.progressBar.visibility = View.GONE
                        displayUserProfile(result.data!!)
                    }
                    is NetworkResult.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.tvError.text = result.message
                        binding.tvError.visibility = View.VISIBLE
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.updateProfileResult.collect { result ->
                when (result) {
                    is NetworkResult.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    is NetworkResult.Success -> {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
                        disableEditing()
                        viewModel.loadUserProfile()
                    }
                    is NetworkResult.Error -> {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.deleteAccountResult.collect { result ->
                when (result) {
                    is NetworkResult.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.btnDeleteAccount.isEnabled = false
                    }
                    is NetworkResult.Success -> {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(requireContext(), R.string.delete_account_done, Toast.LENGTH_LONG).show()
                        goToLogin()
                    }
                    is NetworkResult.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.btnDeleteAccount.isEnabled = true
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.delete_account_title)
                            .setMessage(getString(R.string.delete_account_failed, getString(R.string.operator_email)))
                            .setNegativeButton(R.string.action_cancel, null)
                            .setPositiveButton(R.string.delete_account_logout) { _, _ -> logout() }
                            .show()
                    }
                }
            }
        }
    }

    private fun displayUserProfile(user: com.example.muzea.data.model.UserResponse) {
        binding.tvUsername.text = "@${user.userName}"
        binding.etFullName.setText(user.userName)
        binding.etEmail.setText(user.email)
        binding.tvRole.text = "Role: ${user.role}"

        disableEditing()
    }

    private fun disableEditing() {
        isEditing = false
        binding.btnEditProfile.text = "Edit Profile"
        binding.etFullName.isEnabled = false
        binding.etEmail.isEnabled = false
    }

    private fun logout() {
        val tokenManager = TokenManager(requireContext())
        tokenManager.clearToken()
        goToLogin()
    }

    private fun goToLogin() {
        // Чужие переписки не должны остаться в памяти после смены пользователя.
        com.example.muzea.data.repository.ChatMessagesCache.clear()
        com.example.muzea.data.repository.ChatListCache.clear()
        val intent = android.content.Intent(requireContext(), LoginActivity::class.java)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        activity?.finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
