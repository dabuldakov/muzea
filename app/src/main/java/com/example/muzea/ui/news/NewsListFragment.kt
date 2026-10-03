package com.example.muzea.ui.news

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.domain.model.News
import com.example.muzea.databinding.FragmentNewsListBinding
import com.example.muzea.ui.openDetailScreen
import com.example.muzea.utils.TokenManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NewsListFragment : Fragment() {

    private var _binding: FragmentNewsListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: NewsViewModel by viewModels()

    @Inject
    lateinit var tokenManager: TokenManager

    private lateinit var adapter: NewsAdapter

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
        myUsername = tokenManager.getUsername()
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
                val state = viewModel.feedState.value
                if (dy <= 0 || state.isLoading || state.isRefreshing || state.endReached) return

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
        // Не чистим список: DiffUtil обновит строки на месте, и лента не мигает.
        viewModel.loadNews(PAGE_SIZE, myUsername, fromRefresh = true)
    }

    private fun loadNews() {
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
            viewModel.feedState.collect { render(it) }
        }
    }

    private fun render(state: NewsFeedUiState) {
        binding.swipeRefresh.isRefreshing = state.isRefreshing
        binding.progressBar.visibility =
            if (state.isLoading && state.news.isEmpty() && !state.isRefreshing) View.VISIBLE else View.GONE

        adapter.submitList(state.news)
        val hasNews = state.news.isNotEmpty()

        if (!hasNews && state.error != null) {
            binding.tvError.text = state.error
            binding.tvError.visibility = View.VISIBLE
            binding.tvEmpty.visibility = View.GONE
            binding.recyclerViewNews.visibility = View.GONE
        } else {
            binding.tvError.visibility = View.GONE
            binding.tvEmpty.visibility = if (hasNews || state.isLoading) View.GONE else View.VISIBLE
            binding.recyclerViewNews.visibility = if (hasNews) View.VISIBLE else View.GONE
            state.error?.let {
                Toast.makeText(requireContext(), "Error: $it", Toast.LENGTH_SHORT).show()
                viewModel.consumeError()
            }
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