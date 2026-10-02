package com.example.muzea.ui.news

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.data.api.ChatRetrofitClient
import com.example.muzea.data.api.RetrofitClient
import com.example.muzea.data.model.NewsResponse
import com.example.muzea.data.repository.ChatAuthManager
import com.example.muzea.data.repository.ChatRepository
import com.example.muzea.data.repository.NewsRepository
import com.example.muzea.databinding.FragmentNewsListBinding
import com.example.muzea.ui.openDetailScreen
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import kotlinx.coroutines.launch

class NewsListFragment : Fragment() {

    private var _binding: FragmentNewsListBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: NewsViewModel
    private lateinit var adapter: NewsAdapter

    private var isLoading = false
    private var isRefreshing = false
    private var myUsername: String? = null

    private companion object {
        const val PAGE_SIZE = 20
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewsListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViewModel()
        setupRecyclerView()
        setupSwipeRefresh()
        setupPagination()
        setupFab()
        observeViewModel()
        loadNews()
    }

    private fun initViewModel() {
        val tokenManager = TokenManager(requireContext())
        myUsername = tokenManager.getUsername()
        val apiService = RetrofitClient(tokenManager).apiService
        val newsRepository = NewsRepository(apiService)
        val chatApiService = ChatRetrofitClient(tokenManager).apiService
        val chatRepository = ChatRepository(chatApiService, ChatAuthManager(chatApiService, tokenManager))
        viewModel = NewsViewModel(newsRepository, chatRepository)
    }

    private fun setupRecyclerView() {
        adapter = NewsAdapter(
            onItemClick = ::openNewsDetail
        )
        binding.recyclerViewNews.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@NewsListFragment.adapter
        }
    }

    private fun openNewsDetail(newsId: Long) {
        val fragment = NewsDetailFragment().apply {
            arguments = Bundle().apply { putLong("newsId", newsId) }
        }
        openDetailScreen(fragment)
    }

    private fun setupPagination() {
        val scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || isLoading || isRefreshing) return

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
                val totalItemCount = layoutManager.itemCount

                if (lastVisiblePosition >= totalItemCount - 1 && totalItemCount >= PAGE_SIZE) {
                    loadMoreNews()
                }
            }
        }
        binding.recyclerViewNews.addOnScrollListener(scrollListener)
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener { refreshNews() }
    }

    private fun refreshNews() {
        if (isRefreshing) return
        isRefreshing = true
        // Не чистим список: DiffUtil обновит строки на месте, и лента не мигает.
        viewModel.loadNews(PAGE_SIZE, myUsername)
    }

    private fun loadNews() {
        if (isLoading) return
        if (adapter.currentList.isEmpty()) {
            binding.progressBar.visibility = View.VISIBLE
        }
        viewModel.loadNews(PAGE_SIZE, myUsername)
    }

    private fun loadMoreNews() {
        viewModel.loadMoreNews()
    }

    private fun setupFab() {
        binding.fabAddNews.setOnClickListener {
            startActivity(Intent(requireContext(), CreateNewsActivity::class.java))
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.newsResult.collect { result ->
                when (result) {
                    is NetworkResult.Loading -> handleLoadingState()
                    is NetworkResult.Success -> handleSuccessState(result.data ?: emptyList())
                    is NetworkResult.Error -> handleErrorState(result.message ?: "Unknown error")
                }
            }
        }
    }

    private fun handleLoadingState() {
        val showProgress = !binding.swipeRefresh.isRefreshing && !isRefreshing && adapter.currentList.isEmpty()
        if (showProgress) {
            binding.progressBar.visibility = View.VISIBLE
        }
    }

    private fun handleSuccessState(newNews: List<NewsResponse>) {
        binding.progressBar.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false
        isLoading = false
        isRefreshing = false

        adapter.submitList(newNews)

        if (newNews.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recyclerViewNews.visibility = View.GONE
            binding.tvError.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerViewNews.visibility = View.VISIBLE
            binding.tvError.visibility = View.GONE
        }
    }

    private fun handleErrorState(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false
        isLoading = false
        isRefreshing = false

        if (adapter.currentList.isEmpty()) {
            binding.tvError.text = message
            binding.tvError.visibility = View.VISIBLE
            binding.recyclerViewNews.visibility = View.GONE
        } else {
            Toast.makeText(requireContext(), "Error: $message", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        // Обновляем список при возвращении на экран
        refreshNews()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        // Вкладка живёт постоянно и при переключении не получает onResume,
        // поэтому догружаем данные в фоне при каждом показе.
        if (!hidden) refreshNews()
    }
}