package com.example.muzea.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.muzea.R
import com.example.muzea.databinding.ActivityMainBinding
import com.example.muzea.ui.chat.ChatListFragment
import com.example.muzea.ui.contact.ContactListFragment
import com.example.muzea.ui.news.NewsListFragment
import com.example.muzea.ui.profile.ProfileFragment
import com.example.muzea.ui.video.VideoListFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Показываем первый фрагмент
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, NewsListFragment())
                .commit()
        }

        setupBottomNavigation()
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
}