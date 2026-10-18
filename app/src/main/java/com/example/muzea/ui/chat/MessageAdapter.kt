package com.example.muzea.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.DiffUtil
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.muzea.R
import com.example.muzea.data.model.LinkPreview
import com.example.muzea.data.repository.LinkPreviewStore
import com.example.muzea.domain.model.Message
import com.example.muzea.utils.AvatarLoader
import com.example.muzea.utils.ChatTimeFormatter
import com.example.muzea.utils.MediaUrl
import com.example.muzea.utils.MessageLinks
import com.example.muzea.databinding.ItemMessageIncomingBinding
import com.example.muzea.databinding.ItemMessageOutgoingBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessageAdapter(
    private val myUserUuid: String?,
    private val linkPreviews: LinkPreviewStore
) : ListAdapter<Message, RecyclerView.ViewHolder>(MessageDiffCallback()) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Вызывать при уничтожении вью: отменяет фоновые загрузки превью. */
    fun release() {
        scope.cancel()
    }

    override fun getItemViewType(position: Int): Int {
        val message = getItem(position)
        return if (isMine(message)) TYPE_OUTGOING else TYPE_INCOMING
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_OUTGOING) {
            OutgoingViewHolder(
                ItemMessageOutgoingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        } else {
            IncomingViewHolder(
                ItemMessageIncomingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is OutgoingViewHolder -> holder.bind(message, linkPreviews, scope)
            is IncomingViewHolder -> holder.bind(message, linkPreviews, scope)
        }
    }

    private fun isMine(message: Message): Boolean {
        val sender = message.senderUuid ?: return false
        return myUserUuid != null && sender == myUserUuid
    }

    class IncomingViewHolder(
        private val binding: ItemMessageIncomingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message, linkPreviews: LinkPreviewStore, scope: CoroutineScope) {
            AvatarLoader.load(binding.ivAvatar, message.senderAvatar)
            bindMedia(message)
            bindTextOrPreview(
                binding.tvMessage, binding.linkPreview,
                binding.ivLinkImage, binding.tvLinkTitle, binding.tvLinkDomain,
                message, linkPreviews, scope
            )
            binding.tvMessage.setOnLongClickListener { view ->
                copyMessageToClipboard(view, message.text ?: "")
                true
            }

            if (!message.senderName.isNullOrEmpty()) {
                binding.tvSenderName.text = message.senderName
                binding.tvSenderName.visibility = View.VISIBLE
            } else {
                binding.tvSenderName.visibility = View.GONE
            }

            binding.tvTime.text = formatTime(message.createdAt)
        }

        private var player: ExoPlayer? = null

        private fun bindMedia(message: Message) {
            val mp4 = gifMp4Url(message)?.let { MediaUrl.chat(it) }
            if (mp4.isNullOrBlank()) {
                releasePlayer()
                binding.playerGif.visibility = View.GONE
                bindGif(binding.tvMessage, binding.ivGif, message)
                return
            }
            binding.tvMessage.visibility = View.GONE
            binding.ivGif.visibility = View.GONE
            binding.playerGif.visibility = View.VISIBLE
            val exo = player ?: ExoPlayer.Builder(binding.root.context).build().also {
                it.repeatMode = Player.REPEAT_MODE_ONE
                it.volume = 0f
                it.playWhenReady = true
                player = it
            }
            binding.playerGif.player = exo
            exo.setMediaItem(MediaItem.fromUri(mp4))
            exo.prepare()
        }

        fun releasePlayer() {
            player?.release()
            player = null
            binding.playerGif.player = null
        }
    }

    class OutgoingViewHolder(
        private val binding: ItemMessageOutgoingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(message: Message, linkPreviews: LinkPreviewStore, scope: CoroutineScope) {
            bindMedia(message)
            bindTextOrPreview(
                binding.tvMessage, binding.linkPreview,
                binding.ivLinkImage, binding.tvLinkTitle, binding.tvLinkDomain,
                message, linkPreviews, scope
            )
            binding.tvMessage.setOnLongClickListener { view ->
                copyMessageToClipboard(view, message.text ?: "")
                true
            }
            binding.tvTime.text = formatTime(message.createdAt)
        }

        private var player: ExoPlayer? = null

        private fun bindMedia(message: Message) {
            val mp4 = gifMp4Url(message)?.let { MediaUrl.chat(it) }
            if (mp4.isNullOrBlank()) {
                releasePlayer()
                binding.playerGif.visibility = View.GONE
                bindGif(binding.tvMessage, binding.ivGif, message)
                return
            }
            binding.tvMessage.visibility = View.GONE
            binding.ivGif.visibility = View.GONE
            binding.playerGif.visibility = View.VISIBLE
            val exo = player ?: ExoPlayer.Builder(binding.root.context).build().also {
                it.repeatMode = Player.REPEAT_MODE_ONE
                it.volume = 0f
                it.playWhenReady = true
                player = it
            }
            binding.playerGif.player = exo
            exo.setMediaItem(MediaItem.fromUri(mp4))
            exo.prepare()
        }

        fun releasePlayer() {
            player?.release()
            player = null
            binding.playerGif.player = null
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        when (holder) {
            is IncomingViewHolder -> holder.releasePlayer()
            is OutgoingViewHolder -> holder.releasePlayer()
        }
    }

    fun updateList(newList: List<Message>) {
        submitList(newList)
    }

    companion object {
        private const val TYPE_INCOMING = 0
        private const val TYPE_OUTGOING = 1

        private fun formatTime(iso: String?): String {
            return ChatTimeFormatter.format(iso)
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem.messageUuid == newItem.messageUuid
        }

        override fun areContentsTheSame(oldItem: Message, newItem: Message): Boolean {
            return oldItem == newItem
        }
    }
}

private fun gifUrl(message: Message): String? =
    message.attachments
        .firstOrNull { it.type?.equals("GIF", ignoreCase = true) == true }
        ?.fileUrl

/** MP4-вариант гифки, если есть (для воспроизведения вместо WebP/GIF). */
private fun gifMp4Url(message: Message): String? =
    message.attachments
        .firstOrNull { it.type?.equals("GIF", ignoreCase = true) == true }
        ?.mp4Url

/**
 * Если сообщение — гифка, показываем её (Glide, анимированный GIF, кэш на
 * устройстве) вместо текста. Иначе — обычный текстовый бабл.
 */
private fun bindGif(textView: TextView, imageView: ImageView, message: Message) {
    // Гифка из «Библиотеки» хранится у нас и приходит относительным URL —
    // резолвим в абсолютный адрес чата (Klipy-URL уже абсолютные).
    val url = gifUrl(message)?.let { MediaUrl.chat(it) }
    if (url.isNullOrBlank()) {
        imageView.visibility = View.GONE
        textView.visibility = View.VISIBLE
        return
    }
    textView.visibility = View.GONE
    imageView.visibility = View.VISIBLE
    Glide.with(imageView)
        .load(url)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .into(imageView)
}

/**
 * Текст сообщения или превью-карточка ссылки. Если сообщение — целиком одна
 * ссылка, показываем карточку (картинка + название + домен), сама ссылка
 * скрыта за названием. Если превью недоступно — деградируем до кликабельного
 * текста-ссылки.
 */
private fun bindTextOrPreview(
    tvMessage: TextView,
    card: View,
    ivImage: ImageView,
    tvTitle: TextView,
    tvDomain: TextView,
    message: Message,
    linkPreviews: LinkPreviewStore,
    scope: CoroutineScope
) {
    val url = MessageLinks.singleUrl(message.text)
    if (url == null) {
        showMessageText(tvMessage, card, message.text ?: "")
        return
    }

    card.tag = url
    linkPreviews.cached(url)?.let {
        renderPreview(tvMessage, card, ivImage, tvTitle, tvDomain, it)
        return
    }
    if (linkPreviews.isLoaded(url)) {
        showMessageText(tvMessage, card, url)
        return
    }

    // Заглушка, пока грузится превью.
    tvMessage.visibility = View.GONE
    card.visibility = View.VISIBLE
    ivImage.visibility = View.GONE
    tvTitle.text = url
    tvDomain.text = hostOf(url)
    card.setOnClickListener { openUrl(card.context, url) }

    scope.launch {
        val preview = linkPreviews.load(url)
        withContext(Dispatchers.Main) {
            if (card.tag == url) {
                if (preview != null) {
                    renderPreview(tvMessage, card, ivImage, tvTitle, tvDomain, preview)
                } else {
                    showMessageText(tvMessage, card, url)
                }
            }
        }
    }
}

private fun showMessageText(tvMessage: TextView, card: View, text: String) {
    card.visibility = View.GONE
    card.setOnClickListener(null)
    card.tag = null
    bindMessageText(tvMessage, text)
}

private fun renderPreview(
    tvMessage: TextView,
    card: View,
    ivImage: ImageView,
    tvTitle: TextView,
    tvDomain: TextView,
    preview: LinkPreview
) {
    tvMessage.visibility = View.GONE
    card.visibility = View.VISIBLE
    tvTitle.text = preview.title ?: preview.siteName ?: hostOf(preview.url)
    tvDomain.text = preview.siteName ?: hostOf(preview.url)

    if (!preview.imageUrl.isNullOrBlank()) {
        ivImage.visibility = View.VISIBLE
        Glide.with(ivImage)
            .load(preview.imageUrl)
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .into(ivImage)
    } else {
        ivImage.visibility = View.GONE
    }
    card.setOnClickListener { openUrl(card.context, preview.url) }
}

private fun hostOf(url: String): String = Uri.parse(url).host.orEmpty()

/**
 * Делает ссылки в тексте кликабельными (открываются внешним приложением).
 * Если ссылок нет — просто ставит текст без MovementMethod, чтобы не мешать
 * прокрутке и long-press-копированию.
 */
private fun bindMessageText(textView: TextView, text: String) {
    val links = MessageLinks.matches(text)
    if (links.isEmpty()) {
        textView.movementMethod = null
        textView.text = text
        return
    }
    val spannable = SpannableString(text)
    for (link in links) {
        spannable.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) = openUrl(widget.context, link.url)
                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = false
                }
            },
            link.start,
            link.end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
    textView.text = spannable
    textView.movementMethod = LinkMovementMethod.getInstance()
    // Убираем синюю подсветку при тапе по баблу; ссылку видно по цвету текста.
    textView.highlightColor = android.graphics.Color.TRANSPARENT
}

private fun openUrl(context: Context, raw: String) {
    val uri = Uri.parse(MessageLinks.normalize(raw))
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: Exception) {
        Toast.makeText(context, R.string.link_open_failed, Toast.LENGTH_SHORT).show()
    }
}

private fun copyMessageToClipboard(view: View, text: String) {
    if (text.isBlank()) return
    val clipboard = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
    Toast.makeText(view.context, R.string.message_copied, Toast.LENGTH_SHORT).show()
}
