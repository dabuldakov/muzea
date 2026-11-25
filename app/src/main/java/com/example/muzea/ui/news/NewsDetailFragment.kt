package com.example.muzea.ui.news

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.muzea.R
import com.example.muzea.databinding.FragmentNewsDetailBinding
import com.example.muzea.domain.model.News
import com.example.muzea.domain.model.NewsComment
import com.example.muzea.domain.model.Video
import com.example.muzea.ui.common.ReportDialog
import com.example.muzea.ui.video.VideoDetailActivity
import com.example.muzea.utils.TokenManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NewsDetailFragment : Fragment(), NewsActionsBottomSheet.Listener {

    private var _binding: FragmentNewsDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: NewsDetailViewModel by viewModels()

    @Inject
    lateinit var tokenManager: TokenManager

    private lateinit var headerAdapter: NewsDetailHeaderAdapter
    private lateinit var commentsAdapter: NewsCommentsAdapter

    private var newsId: Long = 0
    private var myUsername: String? = null
    private var currentNews: News? = null
    private var pendingComment: NewsComment? = null

    private val editLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            viewModel.reload()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNewsDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        myUsername = tokenManager.getUsername()
        newsId = arguments?.getLong("newsId", 0) ?: 0

        setupToolbar()
        setupRecyclerView()
        setupComposer()
        setupSwipeRefresh()

        if (newsId == 0L) {
            showFatalError(getString(R.string.news_invalid_id))
            return
        }

        observeViewModel()
        viewModel.load(newsId)
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.btnActions.setOnClickListener {
            val news = currentNews
            if (news == null) {
                Toast.makeText(requireContext(), R.string.news_not_loaded, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            pendingComment = null
            NewsActionsBottomSheet.forNews(canManage(news))
                .show(childFragmentManager, "news_actions")
        }
    }

    private fun setupRecyclerView() {
        headerAdapter = NewsDetailHeaderAdapter(
            onLikeClick = viewModel::toggleLike,
            onLoadOlderComments = viewModel::loadOlderComments,
            onWatchVideo = ::openVideo
        )
        commentsAdapter = NewsCommentsAdapter(::openCommentActions)
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = ConcatAdapter(headerAdapter, commentsAdapter)
            itemAnimator = null
        }
    }

    private fun setupComposer() {
        binding.btnSendComment.isEnabled = false
        binding.etComment.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val state = viewModel.commentsState.value
                binding.btnSendComment.isEnabled =
                    !s.isNullOrBlank() && !state.isSending
            }
        })
        binding.btnSendComment.setOnClickListener { sendComment() }
        binding.etComment.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendComment()
                true
            } else {
                false
            }
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener { viewModel.reload() }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.detailState.collect { state ->
                renderDetail(state)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.commentsState.collect { state ->
                commentsAdapter.submitList(state.comments)
                binding.btnSendComment.isEnabled =
                    binding.etComment.text.isNotBlank() && !state.isSending
                renderHeader(state)
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.message.collect { message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.commentAdded.collect {
                binding.etComment.setText("")
                binding.recyclerView.post {
                    if (commentsAdapter.itemCount > 0) {
                        binding.recyclerView.smoothScrollToPosition(
                            binding.recyclerView.adapter?.itemCount?.minus(1) ?: 0
                        )
                    }
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.newsDeleted.collect {
                Toast.makeText(requireContext(), R.string.news_deleted, Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
        }
    }

    private fun renderDetail(state: NewsDetailUiState) {
        currentNews = state.news
        val loading = state.isLoading
        binding.progressBar.visibility =
            if (loading && state.news == null) View.VISIBLE else View.GONE
        binding.swipeRefresh.isRefreshing = loading && state.news != null

        if (state.news == null && state.error != null) {
            showFatalError(state.error)
            return
        }
        binding.tvError.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE

        val news = state.news ?: return
        val comments = viewModel.commentsState.value
        headerAdapter.submit(
            NewsDetailHeaderAdapter.HeaderUi(
                news = news,
                commentsTotal = comments.total,
                hasMoreComments = comments.hasMore,
                isLoadingComments = comments.isLoading
            )
        )
    }

    private fun renderHeader(comments: NewsCommentsUiState) {
        val news = currentNews ?: return
        headerAdapter.submit(
            NewsDetailHeaderAdapter.HeaderUi(
                news = news,
                commentsTotal = comments.total,
                hasMoreComments = comments.hasMore,
                isLoadingComments = comments.isLoading
            )
        )
    }

    private fun showFatalError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.swipeRefresh.isRefreshing = false
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
        binding.recyclerView.visibility = View.GONE
    }

    private fun sendComment() {
        val text = binding.etComment.text.toString()
        if (text.isBlank()) return
        viewModel.addComment(text)
    }

    private fun openCommentActions(comment: NewsComment) {
        pendingComment = comment
        val news = currentNews
        val canDelete = comment.author == myUsername ||
            (news != null && news.author == myUsername)
        NewsActionsBottomSheet.forComment(canDelete)
            .show(childFragmentManager, "comment_actions")
    }

    override fun onNewsAction(actionId: String) {
        val comment = pendingComment
        if (comment != null) {
            pendingComment = null
            when (actionId) {
                NewsActionsBottomSheet.ACTION_DELETE -> confirmDeleteComment(comment)
                NewsActionsBottomSheet.ACTION_REPORT -> reportComment(comment)
            }
            return
        }

        when (actionId) {
            NewsActionsBottomSheet.ACTION_EDIT -> currentNews?.let {
                editLauncher.launch(CreateNewsActivity.intent(requireContext(), it.id))
            }
            NewsActionsBottomSheet.ACTION_DELETE -> confirmDeleteNews()
            NewsActionsBottomSheet.ACTION_REPORT -> ReportDialog.show(requireContext()) { reason ->
                reportNews(reason)
            }
        }
    }

    private fun confirmDeleteNews() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_news)
            .setMessage(R.string.delete_news_message)
            .setPositiveButton(R.string.action_delete) { _, _ -> viewModel.deleteNews() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun confirmDeleteComment(comment: NewsComment) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.news_delete_comment_title)
            .setMessage(R.string.news_delete_comment_message)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteComment(comment.id)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun reportNews(reason: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = viewModel.reportNews(reason)
            Toast.makeText(
                requireContext(),
                if (ok) R.string.report_sent else R.string.report_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun reportComment(comment: NewsComment) {
        ReportDialog.show(requireContext()) { reason ->
            viewLifecycleOwner.lifecycleScope.launch {
                val ok = viewModel.reportComment(comment.id, reason)
                Toast.makeText(
                    requireContext(),
                    if (ok) R.string.report_sent else R.string.report_failed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun openVideo(video: Video) {
        val intent = Intent(requireContext(), VideoDetailActivity::class.java)
        intent.putExtra("video_id", video.id)
        intent.putExtra("video_url", video.url)
        intent.putExtra("video_title", video.title)
        startActivity(intent)
    }

    private fun canManage(news: News): Boolean =
        !myUsername.isNullOrEmpty() && news.author == myUsername

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
