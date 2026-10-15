package com.example.muzea.data.repository

import android.content.Context
import com.example.muzea.data.api.GitHubApi
import com.example.muzea.data.api.GitHubRelease
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

/** Результат ручной проверки обновления (тап по версии в профиле). */
sealed interface UpdateCheckResult {
    data class Available(val update: AppUpdate) : UpdateCheckResult
    data object UpToDate : UpdateCheckResult
    data object Failed : UpdateCheckResult
}

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

    /**
     * Опрашивает GitHub. Возвращает результат для ручной проверки, а заодно
     * обновляет [availableUpdate] (его слушают бейдж и точка в профиле).
     * При ошибке сети прошлое значение не трогаем — вернём [UpdateCheckResult.Failed].
     *
     * Берём не только последний релиз: если пользователь отстал на несколько
     * версий, в список «что нового» соберём изменения всех пропущенных релизов.
     */
    suspend fun checkForUpdate(): UpdateCheckResult {
        val releases = runCatching { gitHubApi.getReleases() }
            .getOrNull()
            ?.takeIf { it.isSuccessful }
            ?.body()
            ?: return UpdateCheckResult.Failed

        val skipped = releases
            .filter { !it.draft && !it.prerelease }
            .filter { isNewerVersion(it.version(), currentVersionName) }
            .sortedWith { a, b -> compareVersions(a.version(), b.version()) }

        val latest = skipped.lastOrNull()
            ?: run {
                _availableUpdate.value = null
                return UpdateCheckResult.UpToDate
            }

        val apkUrl = latest.assets
            .firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?.downloadUrl

        val update = AppUpdate(
            versionName = latest.version(),
            downloadUrl = apkUrl ?: latest.htmlUrl,
            // Релизы от старых к новым: пункты внутри групп пойдут по порядку.
            changelog = mergeChangelog(skipped.map { it.body })
        )
        _availableUpdate.value = update
        return UpdateCheckResult.Available(update)
    }

    private fun GitHubRelease.version(): String = tagName.removePrefix("v").trim()

    private companion object {
        const val FALLBACK_VERSION = "0"
    }
}

/**
 * Сравнение версий вида «1.2.0». Нечисловые части (например, суффикс «-beta»)
 * трактуются как 0 — этого достаточно для релизных тегов приложения.
 */
internal fun compareVersions(a: String, b: String): Int {
    val aParts = a.split('.')
    val bParts = b.split('.')
    for (i in 0 until maxOf(aParts.size, bParts.size)) {
        val av = aParts.getOrNull(i)?.toIntOrNull() ?: 0
        val bv = bParts.getOrNull(i)?.toIntOrNull() ?: 0
        if (av != bv) return av.compareTo(bv)
    }
    return 0
}

internal fun isNewerVersion(latest: String, current: String): Boolean =
    compareVersions(latest, current) > 0
