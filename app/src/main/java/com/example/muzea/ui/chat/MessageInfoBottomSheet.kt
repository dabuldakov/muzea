package com.example.muzea.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.muzea.R
import com.example.muzea.databinding.BottomSheetMessageInfoBinding
import com.example.muzea.databinding.ItemMessageInfoRowBinding
import com.example.muzea.utils.AvatarLoader
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.Serializable

/** Строка шторки «кто прочитал»: имя, аватар, статус и время. */
data class MessageStatusRow(
    val name: String,
    val avatarUrl: String?,
    /** Имя константы [com.example.muzea.domain.model.MessageDeliveryStatus]. */
    val status: String,
    val at: String?
) : Serializable

/**
 * Шторка статуса своего сообщения.
 *
 * Для приватного чата — одна строка («Доставлено/Прочитано»), для группы —
 * список участников, сгруппированный по статусу.
 */
class MessageInfoBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetMessageInfoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetMessageInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        @Suppress("UNCHECKED_CAST")
        val rows = arguments?.getSerializable(ARG_ROWS) as? ArrayList<MessageStatusRow> ?: arrayListOf()
        binding.tvTitle.setText(R.string.message_info_title)

        if (rows.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            return
        }
        binding.tvEmpty.visibility = View.GONE

        rows.forEach { row ->
            val item = ItemMessageInfoRowBinding.inflate(
                layoutInflater, binding.rowsContainer, false
            )
            AvatarLoader.load(item.ivAvatar, row.avatarUrl)
            item.tvName.text = row.name
            item.tvStatus.setText(statusLabel(row.status))
            val time = row.at
            item.tvTime.text = time
            item.tvTime.visibility = if (time.isNullOrBlank()) View.GONE else View.VISIBLE
            binding.rowsContainer.addView(item.root)
        }
    }

    private fun statusLabel(status: String): Int = when (status) {
        "READ" -> R.string.message_status_read
        "DELIVERED" -> R.string.message_status_delivered
        "FAILED" -> R.string.message_status_failed
        "SENDING" -> R.string.message_status_sending
        else -> R.string.message_status_sent
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ROWS = "rows"

        fun newInstance(rows: List<MessageStatusRow>): MessageInfoBottomSheet =
            MessageInfoBottomSheet().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_ROWS, ArrayList(rows))
                }
            }
    }
}
