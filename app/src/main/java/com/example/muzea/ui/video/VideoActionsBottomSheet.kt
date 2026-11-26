package com.example.muzea.ui.video

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.muzea.R
import com.example.muzea.databinding.BottomSheetMessageActionsBinding
import com.example.muzea.databinding.ItemMessageActionBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.Serializable

/** Действие над видео в нижней шторке. */
data class VideoActionSpec(
    val id: String,
    val iconRes: Int,
    val labelRes: Int
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}

/**
 * Шторка действий видео в том же стиле, что и у новостей/сообщений:
 * только иконки, без текстовых пунктов нативного overflow-меню.
 */
class VideoActionsBottomSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onVideoAction(actionId: String)
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
        val actions = arguments?.getSerializable(ARG_ACTIONS) as? ArrayList<VideoActionSpec>
            ?: arrayListOf()

        actions.forEach { spec ->
            val item = ItemMessageActionBinding.inflate(
                layoutInflater, binding.actionsContainer, false
            )
            item.ivAction.setImageResource(spec.iconRes)
            item.ivAction.contentDescription = getString(spec.labelRes)
            item.root.setOnClickListener {
                ((parentFragment as? Listener) ?: (activity as? Listener))
                    ?.onVideoAction(spec.id)
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
        const val ACTION_DELETE = "DELETE"
        const val ACTION_REPORT = "REPORT"
        private const val ARG_ACTIONS = "actions"

        fun forVideo(canDelete: Boolean): VideoActionsBottomSheet =
            VideoActionsBottomSheet().apply {
                arguments = Bundle().apply {
                    putSerializable(
                        ARG_ACTIONS,
                        ArrayList(
                            if (canDelete) {
                                listOf(
                                    VideoActionSpec(
                                        ACTION_DELETE,
                                        R.drawable.ic_action_delete,
                                        R.string.action_delete
                                    )
                                )
                            } else {
                                listOf(
                                    VideoActionSpec(
                                        ACTION_REPORT,
                                        R.drawable.ic_action_report,
                                        R.string.action_report
                                    )
                                )
                            }
                        )
                    )
                }
            }
    }
}
