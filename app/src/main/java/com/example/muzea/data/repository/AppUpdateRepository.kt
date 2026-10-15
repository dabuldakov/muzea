package com.example.muzea.data.repository

import android.content.Context
import com.example.muzea.data.api.GitHubApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Доступное обновление: версия из тега релиза, ссылка на скачивание и «что нового». */
data class AppUpdate(
    val versionName: String,
    val downloadUrl: String,
    val changelog: List<ChangelogGroup> = emptyList()
)

/**
 * Проверка новых релизов на GitHub.
 *
 * Нужна, чтобы в профиле показать текущую версию и, если вышла свежее,
 * красную точку «требуется участие пользователя» + предложение обновиться.
 * Результат держим в StateFlow: вкладка профиля и бейдж на нижней навигации
 * читают одно и то же состояние.
 */
@Singleton
class AppUpdateRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val gitHubApi: GitHubApi
) {

    private val appContext = context.applicationContext

    private val _availableUpdate = MutableStateFlow<AppUpdate?>(null)
    val availableUpdate: StateFlow<AppUpdate?> = _availableUpdate.asStateFlow()

    val currentVersionName: String by lazy {
        runCatching {
            appContext.packageManager
                .getPackageInfo(appContext.packageName, 0)
                .versionName
        }.getOrNull() ?: FALLBACK_VERSION
    }

    /** Разово опрашивает GitHub. При ошибке сети оставляет прошлое значение. */
    suspend fun checkForUpdate() {
        val release = runCatching { gitHubApi.getLatestRelease() }
            .getOrNull()
            ?.takeIf { it.isSuccessful }
            ?.body()
            ?: return

        val latest = release.tagName.removePrefix("v").trim()
        if (latest.isNotEmpty() && isNewerVersion(latest, currentVersionName)) {
            val apkUrl = release.assets
                .firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
                ?.downloadUrl
            _availableUpdate.value = AppUpdate(
                versionName = latest,
                downloadUrl = apkUrl ?: release.htmlUrl,
                changelog = parseChangelog(release.body)
            )
        } else {
            _availableUpdate.value = null
        }
    }

    private companion object {
        const val FALLBACK_VERSION = "0"
    }
}

/**
 * Сравнение версий вида «1.2.0». Нечисловые части (например, суффикс «-beta»)
 * трактуются как 0 — этого достаточно для релизных тегов приложения.
 */
internal fun isNewerVersion(latest: String, current: String): Boolean {
    val latestParts = latest.split('.')
    val currentParts = current.split('.')
    for (i in 0 until maxOf(latestParts.size, currentParts.size)) {
        val l = latestParts.getOrNull(i)?.toIntOrNull() ?: 0
        val c = currentParts.getOrNull(i)?.toIntOrNull() ?: 0
        if (l != c) return l > c
    }
    return false
}
