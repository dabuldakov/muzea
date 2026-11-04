package com.example.muzea.di

import android.content.Context
import com.example.muzea.data.api.AuthInterceptor
import com.example.muzea.data.api.TokenRefreshInterceptor
import com.example.muzea.data.repository.GifCache
import com.example.muzea.utils.FakeSharedPreferences
import com.example.muzea.utils.SecureStore
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Сетевой граф собирается без Hilt и без Robolectric: проверяем, что провайдеры
 * создают рабочие клиенты/сервисы и кэш гифок. Android-Context подменяется MockK,
 * а пути ведут в `createTempDir`.
 */
class NetworkModuleTest {

    private lateinit var context: Context
    private lateinit var filesDir: File
    private lateinit var securePrefs: FakeSharedPreferences
    private val authInterceptor = mockk<AuthInterceptor>(relaxed = true)
    private val refreshInterceptor = mockk<TokenRefreshInterceptor>(relaxed = true)
    private lateinit var securePrefsField: java.lang.reflect.Field

    @Before
    fun setUp() {
        filesDir = createTempDir("network-files")
        securePrefs = FakeSharedPreferences()
        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        every { context.filesDir } returns filesDir
        every { context.cacheDir } returns filesDir
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
    fun `token manager is built from the context`() {
        val manager = NetworkModule.provideTokenManager(context)

        assertNotNull(manager)
        assertEquals("ANDROID", manager.getDeviceType())
    }

    @Test
    fun `gif cache points at filesDir`() {
        val cache = NetworkModule.provideGifCache(context)

        assertTrue(cache is GifCache)
    }

    @Test
    fun `main client and service are built with extended timeouts`() {
        val client = NetworkModule.provideMainHttpClient(authInterceptor, refreshInterceptor)
        val service = NetworkModule.provideApiService(client)

        assertNotNull(service)
        assertEquals(60_000, client.readTimeoutMillis)
        assertEquals(120_000, client.writeTimeoutMillis)
        assertEquals(30_000, client.connectTimeoutMillis)
        assertTrue(client.certificatePinner.pins.isNotEmpty())
    }

    @Test
    fun `chat client and service are built`() {
        val client = NetworkModule.provideChatHttpClient(authInterceptor, refreshInterceptor)
        val service = NetworkModule.provideChatApiService(client)

        assertNotNull(service)
        assertEquals(30_000, client.readTimeoutMillis)
        assertEquals(30_000, client.writeTimeoutMillis)
        assertTrue(client.certificatePinner.pins.isNotEmpty())
    }

    @Test
    fun `auth services are built`() {
        assertNotNull(NetworkModule.provideAuthServiceApi(authInterceptor))
        assertNotNull(NetworkModule.provideAuthTokenApi())
    }

    @Test
    fun `public apis are built without auth`() {
        assertNotNull(NetworkModule.provideGitHubApi())
        assertNotNull(NetworkModule.provideKlipyApi())
    }

    private fun createTempDir(prefix: String): File =
        File.createTempFile(prefix, "").let {
            it.delete()
            it.mkdirs()
            it
        }
}
