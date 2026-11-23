package com.example.muzea.ui.chat

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.muzea.R
import com.example.muzea.data.model.LinkPreview
import com.example.muzea.data.repository.LinkPreviewStore
import com.example.muzea.domain.model.Message
import com.example.muzea.utils.MediaPolicy
import com.example.muzea.utils.MessageLinks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Текст сообщения или превью-карточка ссылки. Если сообщение — целиком одна
 * ссылка, показываем карточку (картинка + название + домен), сама ссылка
 * скрыта за названием. Если превью недоступно — деградируем до кликабельного
 * текста-ссылки.
 */
internal fun bindTextOrPreview(
    tvMessage: TextView,
    card: View,
    ivImage: ImageView,
    tvTitle: TextView,
    tvDomain: TextView,
    message: Message,
    linkPreviews: LinkPreviewStore,
    scope: CoroutineScope
) {
    // Удалённое сообщение показываем курсивной заглушкой, без ссылок.
    if (message.isDeleted) {
        showMessageText(tvMessage, card, tvMessage.context.getString(R.string.message_deleted))
        tvMessage.setTypeface(null, Typeface.ITALIC)
        return
    }
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

    // На слабой сети превью ссылки не запрашиваем — оставляем кликабельный текст.
    if (!MediaPolicy.autoLoad) {
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
    // Сбрасываем курсив: холдер переиспользуется и мог показать «удалено».
    tvMessage.setTypeface(null, Typeface.NORMAL)
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
 * прокрутке и обычному нажатию (открывает меню действий).
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
    } catch (e: ActivityNotFoundException) {
        Log.w("MessageRowBinding", "No activity to open $uri", e)
        Toast.makeText(context, R.string.link_open_failed, Toast.LENGTH_SHORT).show()
    }
}
