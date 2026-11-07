package com.example.muzea.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.muzea.R
import com.example.muzea.databinding.BottomSheetMessageActionsBinding
import com.example.muzea.databinding.ItemMessageActionBinding
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
 * Описание действия в ряду иконок. Хранится в аргументах фрагмента, поэтому
 * сериализуется; сама обработка живёт в [Listener] родительского фрагмента.
 */
data class MessageActionSpec(
    val id: String,
    val iconRes: Int,
    val labelRes: Int
) : Serializable

/**
 * Шторка действий над сообщением: ряд иконок сверху и статус прочтения снизу.
 *
 * Открывается по нажатию на сообщение и по тапу на галочку/время.
 * Чат остаётся виден за затемнением — это обычный bottom sheet.
 */
class MessageActionsBottomSheet : BottomSheetDialogFragment() {

    /** Родительский фрагмент обрабатывает выбранное действие. */
    interface Listener {
        fun onMessageAction(messageUuid: String, actionId: String)
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

        val messageUuid = arguments?.getString(ARG_MESSAGE_UUID).orEmpty()
        @Suppress("UNCHECKED_CAST")
        val actions = arguments?.getSerializable(ARG_ACTIONS) as? ArrayList<MessageActionSpec>
            ?: arrayListOf()
        @Suppress("UNCHECKED_CAST")
        val rows = arguments?.getSerializable(ARG_ROWS) as? ArrayList<MessageStatusRow>
            ?: arrayListOf()
        val showStatus = arguments?.getBoolean(ARG_SHOW_STATUS) ?: false

        renderActions(messageUuid, actions)
        renderStatus(showStatus, rows)
    }

    private fun renderActions(messageUuid: String, actions: List<MessageActionSpec>) {
        if (actions.isEmpty()) {
            binding.actionsScroll.visibility = View.GONE
            return
        }
        binding.actionsScroll.visibility = View.VISIBLE
        actions.forEach { spec ->
            val item = ItemMessageActionBinding.inflate(
                layoutInflater, binding.actionsContainer, false
            )
            item.ivAction.setImageResource(spec.iconRes)
            item.ivAction.contentDescription = getString(spec.labelRes)
            item.root.setOnClickListener {
                (parentFragment as? Listener)?.onMessageAction(messageUuid, spec.id)
                dismiss()
            }
            binding.actionsContainer.addView(item.root)
        }
    }

    private fun renderStatus(showStatus: Boolean, rows: List<MessageStatusRow>) {
        if (!showStatus) {
            binding.statusSection.visibility = View.GONE
            return
        }
        binding.statusSection.visibility = View.VISIBLE

        // Компактно: только те, кто прочитал, аватары внахлёст. Полный
        // список с датами открывается тапом по этому ряду.
        val readers = rows.filter { it.status == "READ" }
        binding.readersRow.removeAllViews()
        binding.readersRow.setOnClickListener(null)
        binding.rowsScroll.visibility = View.GONE

        if (readers.isEmpty()) {
            binding.readersRow.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rowsScroll.visibility = View.GONE
        } else {
            binding.tvEmpty.visibility = View.GONE
            binding.readersRow.visibility = View.VISIBLE
            val size = dp(38)
            val overlap = -dp(12)
            readers.take(MAX_AVATARS).forEach { row ->
                val avatar = ImageView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(size, size).apply {
                        marginEnd = overlap
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = row.name
                    AvatarLoader.load(this, row.avatarUrl)
                }
                binding.readersRow.addView(avatar)
            }
            if (readers.size > MAX_AVATARS) {
                val more = TextView(requireContext()).apply {
                    text = "+${readers.size - MAX_AVATARS}"
                    textSize = 13f
                    setPadding(dp(20), 0, 0, 0)
                }
                binding.readersRow.addView(more)
            }
            binding.readersRow.setOnClickListener {
                binding.rowsScroll.visibility =
                    if (binding.rowsScroll.visibility == View.VISIBLE) View.GONE
                    else View.VISIBLE
            }
        }

        if (rows.isEmpty()) return

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

        binding.rowsContainer.post {
            val maxHeight = dp(MAX_ROWS_HEIGHT_DP)
            if (binding.rowsContainer.height > maxHeight) {
                binding.rowsScroll.layoutParams = binding.rowsScroll.layoutParams.apply {
                    height = maxHeight
                }
            }
        }
    }

    private fun dp(value: Int): Int =
        (resources.displayMetrics.density * value).toInt()

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
        private const val ARG_MESSAGE_UUID = "message_uuid"
        private const val ARG_ACTIONS = "actions"
        private const val ARG_ROWS = "rows"
        private const val ARG_SHOW_STATUS = "show_status"
        private const val MAX_ROWS_HEIGHT_DP = 300
        private const val MAX_AVATARS = 4

        // Идентификаторы действий. Обработка — в ChatConversationFragment.
        const val ACTION_COPY = "copy"
        const val ACTION_REPORT = "report"
        const val ACTION_BLOCK = "block"
        const val ACTION_RETRY = "retry"
        const val ACTION_REPLY = "reply"
        const val ACTION_EDIT = "edit"
        const val ACTION_DELETE = "delete"
        const val ACTION_FORWARD = "forward"
        const val ACTION_PIN = "pin"
        const val ACTION_UNPIN = "unpin"
        const val ACTION_SELECT = "select"

        fun newInstance(
            messageUuid: String,
            actions: List<MessageActionSpec>,
            rows: List<MessageStatusRow>,
            showStatus: Boolean
        ): MessageActionsBottomSheet = MessageActionsBottomSheet().apply {
            arguments = Bundle().apply {
                putString(ARG_MESSAGE_UUID, messageUuid)
                putSerializable(ARG_ACTIONS, ArrayList(actions))
                putSerializable(ARG_ROWS, ArrayList(rows))
                putBoolean(ARG_SHOW_STATUS, showStatus)
            }
        }
    }
}
