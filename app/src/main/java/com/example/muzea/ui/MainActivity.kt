package com.example.muzea.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.muzea.R
import com.example.muzea.data.api.ChatRetrofitClient
import com.example.muzea.data.repository.ChatAuthManager
import com.example.muzea.data.repository.ChatRepository
import com.example.muzea.databinding.ActivityMainBinding
import com.example.muzea.ui.chat.ChatListFragment
import com.example.muzea.ui.contact.ContactListFragment
import com.example.muzea.ui.news.NewsListFragment
import com.example.muzea.ui.profile.ProfileFragment
import com.example.muzea.ui.video.VideoListFragment
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var chatRepository: ChatRepository

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermissionIfNeeded()

        // Показываем первый фрагмент
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, NewsListFragment())
                .commit()
        }

        setupBottomNavigation()
        setupUnreadBadge()
        startPresenceHeartbeat()
    }

    /**
     * Heartbeat «приложение на переднем плане» на всё приложение.
     *
     * Именно он, а не список контактов, продлевает серверное окно «в сети»:
     * пользователь может час читать новости, ни разу не открыв контакты, и всё
     * это время должен оставаться «в сети». Цикл привязан к STARTED, поэтому в
     * фоне и после сворачивания приложения запросов нет — сервер догасит
     * статус сам по TTL.
     */
    private fun startPresenceHeartbeat() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    chatRepository.sendHeartbeat()
                    delay(HEARTBEAT_INTERVAL_MS)
                }
            }
        }
    }

    private fun setupUnreadBadge() {
        val tokenManager = TokenManager(applicationContext)
        val apiService = ChatRetrofitClient(tokenManager).apiService
        chatRepository = ChatRepository(apiService, ChatAuthManager(apiService, tokenManager))

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    refreshUnreadBadge()
                    delay(UNREAD_REFRESH_INTERVAL_MS)
                }
            }
        }
    }

    private suspend fun refreshUnreadBadge() {
        chatRepository.getTotalUnreadCount().collect { result ->
            if (result is NetworkResult.Success) {
                updateChatBadge(result.data ?: 0L)
            }
        }
    }

    private fun updateChatBadge(count: Long) {
        if (count > 0) {
            val badge = binding.bottomNavigation.getOrCreateBadge(R.id.chatFragment)
            badge.isVisible = true
            badge.number = count.coerceAtMost(999).toInt()
        } else {
            binding.bottomNavigation.removeBadge(R.id.chatFragment)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.newsListFragment -> {
                    replaceFragment(NewsListFragment())
                    true
                }
                R.id.chatFragment -> {
                    replaceFragment(ChatListFragment())
                    true
                }
                R.id.contactFragment -> {
                    replaceFragment(ContactListFragment())
                    true
                }
                R.id.videoListFragment -> {
                    replaceFragment(VideoListFragment())
                    true
                }
                R.id.profileFragment -> {
                    replaceFragment(ProfileFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun replaceFragment(fragment: androidx.fragment.app.Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private companion object {
        private const val UNREAD_REFRESH_INTERVAL_MS = 10_000L

        /**
         * Интервал heartbeat. Сервер считает пользователя онлайном 45 секунд
         * после последнего heartbeat, поэтому 15 секунд переживают потерю пары
         * запросов в плохой сети и при этом не грузят сервер зря.
         */
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
    }
}