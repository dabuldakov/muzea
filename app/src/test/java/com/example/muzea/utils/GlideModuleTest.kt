package com.example.muzea.utils

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.engine.cache.DiskCache
import com.bumptech.glide.load.engine.cache.DiskLruCacheFactory
import com.bumptech.glide.load.model.GlideUrl
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.InputStream

/**
 * Покрывает [GlideModule] на чистой JVM: аргументы Glide подменяются MockK,
 * а клиент OkHttp достаётся рефлексией из созданного загрузчика, чтобы прогнать
 * интерцептор авторизации (в т.ч. запрет на утечку токена на внешние хосты).
 */
class GlideModuleTest {

    private lateinit var context: Context
    private lateinit var filesDir: File
    private lateinit var securePrefs: FakeSharedPreferences
    private lateinit var securePrefsField: java.lang.reflect.Field
    private val module = GlideModule()

    @Before
    fun setUp() {
        filesDir = createTempDir("glide-files")
        securePrefs = FakeSharedPreferences()
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        every { context.filesDir } returns filesDir
        every { context.getSharedPreferences(any(), any()) } returns FakeSharedPreferences()
        securePrefsField = SecureStore::class.java.getDeclaredField("prefs").apply { isAccessible = true }
        securePrefsField.set(SecureStore, securePrefs)
    }

    @After
    fun tearDown() {
        securePrefsField.set(SecureStore, null)
        filesDir.deleteRecursively()
    }

    @Test
    fun `manifest parsing is disabled`() {
        assertFalse(module.isManifestParsingEnabled())
    }

    @Test
    fun `applyOptions installs a disk cache under filesDir`() {
        val builder = mockk<GlideBuilder>(relaxed = true)
        val factorySlot = slot<DiskCache.Factory>()
        every { builder.setDiskCache(capture(factorySlot)) } returns builder

        module.applyOptions(context, builder)

        verify(exactly = 1) { builder.setDiskCache(any()) }
        val factory = factorySlot.captured
        assertTrue(factory is DiskLruCacheFactory)
        assertNotNull(factory.build())
        assertTrue(File(filesDir, GLIDE_DISK_CACHE_DIR).isDirectory)
    }

    @Test
    fun `registerComponents replaces the GlideUrl loader`() {
        val glide = mockk<Glide>(relaxed = true)
        val registry = mockk<Registry>(relaxed = true)

        module.registerComponents(context, glide, registry)

        verify(exactly = 1) {
            registry.replace(GlideUrl::class.java, InputStream::class.java, any())
        }
    }

    @Test
    fun `interceptor adds a bearer header for an authorized host`() {
        securePrefs.edit().putString("auth_token", "tok").commit()

        val run = InterceptorRun(interceptor())
        run.call("https://api-muzea.su/api/news/x")

        assertEquals("Bearer tok", run.proceeded?.header("Authorization"))
    }

    @Test
    fun `interceptor does not leak the token to a foreign host`() {
        securePrefs.edit().putString("auth_token", "tok").commit()

        val run = InterceptorRun(interceptor())
        run.call("https://api.klipy.com/gifs")

        assertNull(run.proceeded?.header("Authorization"))
    }

    @Test
    fun `interceptor skips the header when there is no token`() {
        val run = InterceptorRun(interceptor())
        run.call("https://api-muzea.su/api/news/x")

        assertNull(run.proceeded?.header("Authorization"))
    }

    /** Собирает граф и достаёт интерцептор из клиента, зашитого в Glide-загрузчик. */
    private fun interceptor(): Interceptor {
        val registry = mockk<Registry>(relaxed = true)
        val factorySlot = slot<OkHttpUrlLoader.Factory>()
        every {
            registry.replace(GlideUrl::class.java, InputStream::class.java, capture(factorySlot))
        } returns registry
        module.registerComponents(context, mockk(relaxed = true), registry)

        val clientField = factorySlot.captured.javaClass.getDeclaredField("client")
            .apply { isAccessible = true }
        val client = clientField.get(factorySlot.captured) as OkHttpClient
        return client.interceptors.first()
    }

    /** Прогоняет один запрос через интерцептор и запоминает пропущенный дальше запрос. */
    private class InterceptorRun(private val interceptor: Interceptor) {
        var proceeded: Request? = null
            private set

        fun call(url: String) {
            val chain = mockk<Interceptor.Chain>()
            every { chain.request() } returns Request.Builder().url(url).build()
            every { chain.proceed(any()) } answers {
                proceeded = firstArg<Request>()
                mockk<Response>(relaxed = true)
            }
            interceptor.intercept(chain)
        }
    }

    private fun createTempDir(prefix: String): File =
        File.createTempFile(prefix, "").let {
            it.delete()
            it.mkdirs()
            it
        }
}
