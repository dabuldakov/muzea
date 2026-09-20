package com.example.muzea.utils

import android.widget.ImageView
import com.bumptech.glide.Glide
import com.example.muzea.R

object AvatarLoader {
    fun load(view: ImageView, path: String?) {
        val url = MediaUrl.chat(path)
        // Always bind, including null, to clear recycled avatars.
        Glide.with(view).load(url).circleCrop()
            .placeholder(R.drawable.ic_default_avatar)
            .fallback(R.drawable.ic_default_avatar)
            .error(R.drawable.ic_default_avatar).into(view)
    }
}