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
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.muzea.R
import com.example.muzea.domain.repository.ChatSessionRepository
import com.example.muzea.databinding.ActivityMainBinding
import com.example.muzea.ui.chat.ChatListFragment
import com.example.muzea.ui.contact.ContactListFragment
import com.example.muzea.ui.news.NewsListFragment
import com.example.muzea.ui.profile.ProfileFragment
import com.example.muzea.ui.video.VideoListFragment
import com.example.muzea.utils.NetworkResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    @Inject
    lateinit var chatSessionRepository: ChatSessionRepository

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
                .add(
                    R.id.fragment_container,
                    NewsListFragment(),
                    TAB_TAGS.getValue(R.id.newsListFragment)
                )
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
                    chatSessionRepository.sendHeartbeat()
                    delay(HEARTBEAT_INTERVAL_MS)
                }
            }
        }
    }

    private fun setupUnreadBadge() {
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
        chatSessionRepository.getTotalUnreadCount().collect { result ->
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
            showTab(menuItem.itemId)
            true
        }
    }

    /**
     * Переключение вкладок без пересоздания фрагментов.
     *
     * Вкладки один раз добавляются в контейнер и дальше только прячутся и
     * показываются, поэтому их View, адаптеры и прокрутка живут всё время.
     * Переключение мгновенное: пользователь сразу видит уже загруженный список,
     * а данные обновляются в фоне (опрос/onHiddenChanged). Старый вариант с
     * replace() уничтожал View вкладки на каждом переключении и заново грузил
     * контент — отсюда была задержка.
     */
    private fun showTab(itemId: Int) {
        val tag = TAB_TAGS[itemId] ?: return

        // Закрываем экраны, открытые поверх вкладок (новость, чат, настройки
        // группы), чтобы новая вкладка не оказалась под ними.
        supportFragmentManager.popBackStackImmediate(
            null,
            FragmentManager.POP_BACK_STACK_INCLUSIVE
        )

        val fragmentManager = supportFragmentManager
        val target = fragmentManager.findFragmentByTag(tag)
        val transaction = fragmentManager.beginTransaction().setReorderingAllowed(true)

        for ((id, otherTag) in TAB_TAGS) {
            if (id == itemId) continue
            fragmentManager.findFragmentByTag(otherTag)?.let { other ->
                if (!other.isHidden) transaction.hide(other)
            }
        }

        if (target == null) {
            transaction.add(R.id.fragment_container, createTabFragment(itemId), tag)
        } else {
            transaction.show(target)
        }

        transaction.commit()
    }

    private fun createTabFragment(itemId: Int): Fragment = when (itemId) {
        R.id.newsListFragment -> NewsListFragment()
        R.id.chatFragment -> ChatListFragment()
        R.id.contactFragment -> ContactListFragment()
        R.id.videoListFragment -> VideoListFragment()
        R.id.profileFragment -> ProfileFragment()
        else -> NewsListFragment()
    }

    private companion object {
        private val TAB_TAGS = mapOf(
            R.id.newsListFragment to "tab_news",
            R.id.chatFragment to "tab_chat",
            R.id.contactFragment to "tab_contacts",
            R.id.videoListFragment to "tab_videos",
            R.id.profileFragment to "tab_profile"
        )

        private const val UNREAD_REFRESH_INTERVAL_MS = 10_000L

        /**
         * Интервал heartbeat. Сервер считает пользователя онлайном 45 секунд
         * после последнего heartbeat, поэтому 15 секунд переживают потерю пары
         * запросов в плохой сети и при этом не грузят сервер зря.
         */
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
    }
}