package com.example.muzea.ui.news

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.R
import com.example.muzea.data.api.RetrofitClient
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
    private var isLoading = false
    private var currentPage = 0
    private var hasMorePages = true
    private val pageSize = 20
    private var isRefreshing = false

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

        // Создаем ViewModel вручную
        val tokenManager = TokenManager(requireContext())
        val apiService = RetrofitClient(tokenManager).apiService
        val newsRepository = NewsRepository(apiService)
        viewModel = NewsViewModel(newsRepository)

        setupRecyclerView()
        setupSwipeRefresh()
        setupPagination()
        setupFab()
        observeViewModel()
        loadNews()
    }

    private fun setupRecyclerView() {
        adapter = NewsAdapter(
            onItemClick = { newsId ->
                val fragment = NewsDetailFragment()
                val bundle = Bundle().apply {
                    putLong("newsId", newsId)
                }
                fragment.arguments = bundle

                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .addToBackStack(null)
                    .commit()
            },
            lifecycleScope = lifecycleScope
        )
        binding.recyclerViewNews.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@NewsListFragment.adapter
        }
    }

    private fun setupPagination() {
        binding.recyclerViewNews.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoading && hasMorePages && !isRefreshing && dy > 0) {  // Добавили dy > 0
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0
                        && totalItemCount >= pageSize
                    ) {
                        loadMoreNews()
                    }
                }
            }
        })
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            refreshNews()
        }
    }

    private fun refreshNews() {
        if (isRefreshing) return
        isRefreshing = true
        currentPage = 0
        hasMorePages = true
        isLoading = false

        // Очищаем адаптер
        adapter.submitList(emptyList())

        // Загружаем новости заново
        loadNews()
    }

    private fun loadNews() {
        if (isLoading && !isRefreshing) return
        isLoading = true
        viewModel.loadNews(currentPage, pageSize)
    }

    private fun loadMoreNews() {
        if (!hasMorePages || isRefreshing) return
        currentPage++
        loadNews()
    }

    private fun setupFab() {
        binding.fabAddNews.setOnClickListener {
            val intent = Intent(requireContext(), CreateNewsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.newsResult.collect { result ->
                when (result) {
                    is NetworkResult.Loading -> {
                        if (!binding.swipeRefresh.isRefreshing && currentPage == 0 && !isRefreshing) {
                            binding.progressBar.visibility = View.VISIBLE
                        }
                    }

                    is NetworkResult.Success -> {
                        binding.progressBar.visibility = View.GONE
                        binding.swipeRefresh.isRefreshing = false
                        isLoading = false

                        val newNews = result.data ?: emptyList()
                        hasMorePages = newNews.size == pageSize

                        android.util.Log.d("NewsListFragment", "Page: $currentPage, News count: ${newNews.size}, IsRefreshing: $isRefreshing")

                        if (currentPage == 0) {
                            // При обновлении принудительно создаем новый список
                            if (isRefreshing) {
                                android.util.Log.d("NewsListFragment", "Refresh complete, showing ${newNews.size} items")
                                // Создаем новый список чтобы принудительно обновить адаптер
                                adapter.updateList(newNews)
                                isRefreshing = false
                            } else {
                                adapter.submitList(newNews)
                            }
                        } else {
                            // Добавляем к существующему списку
                            val currentList = adapter.currentList.toMutableList()
                            currentList.addAll(newNews)
                            adapter.submitList(currentList.toList())
                        }

                        // Обновляем UI состояние
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

                    is NetworkResult.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.swipeRefresh.isRefreshing = false
                        isLoading = false
                        isRefreshing = false

                        android.util.Log.e("NewsListFragment", "Error: ${result.message}")

                        if (adapter.currentList.isEmpty()) {
                            binding.tvError.text = result.message
                            binding.tvError.visibility = View.VISIBLE
                            binding.recyclerViewNews.visibility = View.GONE
                        } else {
                            android.widget.Toast.makeText(
                                requireContext(),
                                "Error: ${result.message}",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}