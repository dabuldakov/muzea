package com.example.muzea.ui.news

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.muzea.R
import com.example.muzea.databinding.BottomSheetMessageActionsBinding
import com.example.muzea.databinding.ItemMessageActionBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.Serializable

/** Описание действия над новостью/комментарием в шторке. */
data class NewsActionSpec(
    val id: String,
    val iconRes: Int,
    val labelRes: Int
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Шторка действий в едином стиле с сообщениями: ряд иконок с подписями.
 * Используется для действий над новостью и комментарием.
 */
class NewsActionsBottomSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onNewsAction(actionId: String)
    }

    private var _binding: BottomSheetMessageActionsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetMessageActionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.statusSection.visibility = View.GONE

        @Suppress("UNCHECKED_CAST")
        val actions = arguments?.getSerializable(ARG_ACTIONS) as? ArrayList<NewsActionSpec>
            ?: arrayListOf()

        actions.forEach { spec ->
            val item = ItemMessageActionBinding.inflate(
                layoutInflater, binding.actionsContainer, false
            )
            item.ivAction.setImageResource(spec.iconRes)
            item.ivAction.contentDescription = getString(spec.labelRes)
            item.root.setOnClickListener {
                (parentFragment as? Listener)?.onNewsAction(spec.id)
                dismiss()
            }
            binding.actionsContainer.addView(item.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ACTION_EDIT = "EDIT"
        const val ACTION_DELETE = "DELETE"
        const val ACTION_REPORT = "REPORT"

        private const val ARG_ACTIONS = "actions"

        fun newInstance(actions: List<NewsActionSpec>): NewsActionsBottomSheet =
            NewsActionsBottomSheet().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_ACTIONS, ArrayList(actions))
                }
            }

        fun forNews(canEdit: Boolean): NewsActionsBottomSheet = newInstance(
            if (canEdit) {
                listOf(
                    NewsActionSpec(ACTION_EDIT, R.drawable.ic_action_edit, R.string.action_edit),
                    NewsActionSpec(ACTION_DELETE, R.drawable.ic_action_delete, R.string.action_delete)
                )
            } else {
                listOf(
                    NewsActionSpec(ACTION_REPORT, R.drawable.ic_action_report, R.string.action_report)
                )
            }
        )

        fun forComment(canDelete: Boolean): NewsActionsBottomSheet = newInstance(
            if (canDelete) {
                listOf(
                    NewsActionSpec(ACTION_DELETE, R.drawable.ic_action_delete, R.string.action_delete)
                )
            } else {
                listOf(
                    NewsActionSpec(ACTION_REPORT, R.drawable.ic_action_report, R.string.action_report)
                )
            }
        )
    }
}
