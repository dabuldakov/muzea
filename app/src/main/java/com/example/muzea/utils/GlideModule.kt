package com.example.muzea.utils

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.engine.cache.DiskLruCacheFactory
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import okhttp3.OkHttpClient
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

/** Имя каталога дискового кэша Glide в filesDir (учитывается CacheManager). */
const val GLIDE_DISK_CACHE_DIR = "glide_disk_cache"

@GlideModule
class GlideModule : AppGlideModule() {

    /** Дисковый кэш Glide в filesDir: cacheDir система/OEM периодически очищает. */
    override fun applyOptions(context: Context, builder: GlideBuilder) {
        val dir = File(context.applicationContext.filesDir, GLIDE_DISK_CACHE_DIR)
        builder.setDiskCache(DiskLruCacheFactory(dir.absolutePath, DISK_CACHE_BYTES))
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val tokenManager = TokenManager(context.applicationContext)
        val client = OkHttpClient.Builder()
            // Bearer-токен шлём ТОЛЬКО на наши домены: Glide грузит и внешние
            // картинки (например, гифки Klipy), и без проверки хоста наш токен
            // утёк бы на сторонний сервис.
            .addInterceptor { chain ->
                val request = chain.request()
                val token = tokenManager.getToken()
                val authorized = if (!token.isNullOrEmpty() && request.url.host in AUTH_HOSTS) {
                    request.newBuilder().header("Authorization", "Bearer $token").build()
                } else {
                    request
                }
                chain.proceed(authorized)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            OkHttpUrlLoader.Factory(client)
        )
    }

    override fun isManifestParsingEnabled(): Boolean = false

    private companion object {
        /** Домены, которым можно отправлять наш токен. */
        val AUTH_HOSTS = setOf("api-muzea.su", "chat-muzea.su")

        const val DISK_CACHE_BYTES = 250L * 1024 * 1024
    }
}