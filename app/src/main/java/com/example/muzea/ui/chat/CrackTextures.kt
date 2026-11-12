package com.example.muzea.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Прозрачные текстуры трещин, сгенерированные по примерам.
 *
 * Декодируем один раз на процесс и уменьшаем в 2 раза: на бабле текстура
 * всё равно масштабируется, а память и время отрисовки важнее.
 */
internal object CrackTextures {

    @Volatile
    private var cache: List<Bitmap>? = null

    fun get(context: Context): List<Bitmap> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val loaded = runCatching {
                listOf(
                    com.example.muzea.R.drawable.crack_1,
                    com.example.muzea.R.drawable.crack_2,
                    com.example.muzea.R.drawable.crack_3,
                    com.example.muzea.R.drawable.crack_4,
                    com.example.muzea.R.drawable.crack_5
                ).mapNotNull { id ->
                    BitmapFactory.decodeResource(
                        context.resources, id,
                        BitmapFactory.Options().apply { inSampleSize = 2 }
                    )
                }
            }.getOrDefault(emptyList())
            cache = loaded
            return loaded
        }
    }
}
