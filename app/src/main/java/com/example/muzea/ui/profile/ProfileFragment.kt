package com.example.muzea.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.muzea.databinding.FragmentProfileBinding
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
        viewModel = ProfileViewModel(userRepository, tokenManager)

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
    }

    private fun enableEditing() {
        isEditing = true
        binding.btnEditProfile.text = "Save"
        binding.etFullName.isEnabled = true
        binding.etEmail.isEnabled = true
        binding.btnEditProfile.setBackgroundColor(requireContext().getColor(android.R.color.holo_green_dark))
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
        lifecycleScope.launch {
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

        lifecycleScope.launch {
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
        binding.btnEditProfile.setBackgroundColor(requireContext().getColor(android.R.color.holo_blue_dark))
    }

    private fun logout() {
        val tokenManager = TokenManager(requireContext())
        tokenManager.clearToken()
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