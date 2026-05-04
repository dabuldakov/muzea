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
import com.example.muzea.R
import com.example.muzea.data.api.RetrofitClient
import com.example.muzea.data.model.NewsResponse
import com.example.muzea.data.repository.NewsRepository
import com.example.muzea.databinding.FragmentNewsListBinding
import com.example.muzea.utils.NetworkResult
import com.example.muzea.utils.TokenManager
import kotlinx.coroutines.launch

class NewsListFragment : Fragment() {

    private var _binding: FragmentNewsListBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: NewsViewModel
    private lateinit var adapter: NewsAdapter

    // Состояния загрузки
    private var isLoading = false
    private var isRefreshing = false
    private var currentPage = 0
    private var hasMorePages = true

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
        val apiService = RetrofitClient(tokenManager).apiService
        val newsRepository = NewsRepository(apiService)
        viewModel = NewsViewModel(newsRepository)
    }

    private fun setupRecyclerView() {
        adapter = NewsAdapter(
            onItemClick = ::openNewsDetail,
            lifecycleScope = lifecycleScope
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
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun setupPagination() {
        val scrollListener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || isLoading || isRefreshing || !hasMorePages) return

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
        currentPage = 0
        hasMorePages = true
        isLoading = false
        adapter.clearItems()
        loadNews()
    }

    private fun loadNews() {
        if (isLoading && !isRefreshing) return
        isLoading = true
        viewModel.loadNews(currentPage, PAGE_SIZE)
    }

    private fun loadMoreNews() {
        if (!hasMorePages || isRefreshing) return
        currentPage++
        loadNews()
    }

    private fun setupFab() {
        binding.fabAddNews.setOnClickListener {
            startActivity(Intent(requireContext(), CreateNewsActivity::class.java))
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
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
        val showProgress = !binding.swipeRefresh.isRefreshing && currentPage == 0 && !isRefreshing
        if (showProgress) {
            binding.progressBar.visibility = View.VISIBLE
        }
    }

    private fun handleSuccessState(newNews: List<NewsResponse>) {
        binding.progressBar.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false
        isLoading = false

        hasMorePages = newNews.size == PAGE_SIZE

        if (currentPage == 0) {
            handleInitialLoad(newNews)
        } else {
            handlePaginationLoad(newNews)
        }

        updateEmptyState()
    }

    private fun handleInitialLoad(newNews: List<NewsResponse>) {
        if (isRefreshing) {
            adapter.updateList(newNews)
            isRefreshing = false
        } else {
            adapter.submitList(newNews)
        }
    }

    private fun handlePaginationLoad(newNews: List<NewsResponse>) {
        val currentList = adapter.currentList.toMutableList()
        currentList.addAll(newNews)
        adapter.submitList(currentList.toList())
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

    private fun updateEmptyState() {
        if (adapter.currentList.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.recyclerViewNews.visibility = View.GONE
            binding.tvError.visibility = View.GONE
            binding.tvEmpty.text = "No news available"
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerViewNews.visibility = View.VISIBLE
            binding.tvError.visibility = View.GONE
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
}