package com.example.muzea.data.repository

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.example.muzea.data.api.GitHubApi
import com.example.muzea.data.api.GitHubAsset
import com.example.muzea.data.api.GitHubRelease
import com.example.muzea.data.httpError
import com.example.muzea.data.httpOk
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRepositoryTest {

    private val context = mockk<Context>(relaxed = true)
    private val packageManager = mockk<PackageManager>()
    private val gitHubApi = mockk<GitHubApi>()

    private fun repository(version: String = "1.7.0"): AppUpdateRepository {
        val info = PackageInfo().apply { versionName = version }
        every { context.applicationContext } returns context
        every { context.packageName } returns "com.cyber.muzea"
        every { context.packageManager } returns packageManager
        every { packageManager.getPackageInfo("com.cyber.muzea", 0) } returns info
        return AppUpdateRepository(context, gitHubApi)
    }

    @Test
    fun `same version means up to date`() = runTest {
        coEvery { gitHubApi.getReleases() } returns httpOk(listOf(release("v1.7.0")))

        val result = repository().checkForUpdate()

        assertEquals(UpdateCheckResult.UpToDate, result)
    }

    @Test
    fun `newer stable release becomes available`() = runTest {
        coEvery { gitHubApi.getReleases() } returns httpOk(
            listOf(
                release("v1.7.1", body = "## Added\n- one", apk = true),
                release("v1.7.2", body = "## Added\n- two\n## Fixed\n- bug", apk = true)
            )
        )

        val result = repository().checkForUpdate()

        val update = (result as UpdateCheckResult.Available).update
        assertEquals("1.7.2", update.versionName)
        assertEquals("https://github.com/download/1.7.2.apk", update.downloadUrl)
        assertEquals(listOf("one", "two"), update.changelog.first { it.title == "Added" }.items)
        assertEquals(listOf("bug"), update.changelog.first { it.title == "Fixed" }.items)
    }

    @Test
    fun `falls back to latest release when the list request fails`() = runTest {
        coEvery { gitHubApi.getReleases() } returns httpError(504)
        coEvery { gitHubApi.getLatestRelease() } returns httpOk(release("v1.7.1"))

        val result = repository().checkForUpdate()

        assertEquals("1.7.1", (result as UpdateCheckResult.Available).update.versionName)
    }

    @Test
    fun `drafts and prereleases are ignored`() = runTest {
        coEvery { gitHubApi.getReleases() } returns httpOk(
            listOf(
                release("v2.0.0", draft = true),
                release("v1.9.0", prerelease = true),
                release("v1.7.1")
            )
        )

        val result = repository().checkForUpdate()

        assertEquals("1.7.1", (result as UpdateCheckResult.Available).update.versionName)
    }

    @Test
    fun `falls back to the html url when no apk asset exists`() = runTest {
        coEvery { gitHubApi.getReleases() } returns httpOk(listOf(release("v1.7.1", apk = false)))

        val update = (repository().checkForUpdate() as UpdateCheckResult.Available).update

        assertEquals("https://github.com/releases/1.7.1", update.downloadUrl)
    }

    @Test
    fun `network failure reports Failed and keeps the previous update`() = runTest {
        coEvery { gitHubApi.getReleases() } returnsMany listOf(
            httpOk(listOf(release("v1.7.1"))),
            httpError(500)
        )
        val repository = repository()

        assertTrue(repository.checkForUpdate() is UpdateCheckResult.Available)
        assertEquals("1.7.1", repository.availableUpdate.value?.versionName)

        assertEquals(UpdateCheckResult.Failed, repository.checkForUpdate())
        assertEquals("1.7.1", repository.availableUpdate.value?.versionName)
    }

    @Test
    fun `becoming up to date clears the available update`() = runTest {
        coEvery { gitHubApi.getReleases() } returnsMany listOf(
            httpOk(listOf(release("v1.7.1"))),
            httpOk(listOf(release("v1.7.0")))
        )
        val repository = repository()

        repository.checkForUpdate()
        assertEquals(UpdateCheckResult.UpToDate, repository.checkForUpdate())
        assertNull(repository.availableUpdate.value)
    }

    private fun release(
        tag: String,
        body: String? = null,
        draft: Boolean = false,
        prerelease: Boolean = false,
        apk: Boolean = true
    ): GitHubRelease {
        val version = tag.removePrefix("v")
        return GitHubRelease(
            tagName = tag,
            htmlUrl = "https://github.com/releases/$version",
            body = body,
            draft = draft,
            prerelease = prerelease,
            assets = if (apk) {
                listOf(GitHubAsset("muzea-$version.apk", "https://github.com/download/$version.apk"))
            } else {
                emptyList()
            }
        )
    }
}
