package com.example.muzea.ui.chat

import com.example.muzea.core.Resource
import com.example.muzea.domain.model.Message
import com.example.muzea.domain.repository.MessageRepository
import com.example.muzea.utils.LocalTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Входящий поток переписки: первая страница, дельта по времени, сверка правок
 * и догрузка истории. Слияние в состояние делает ViewModel через колбэк.
 */
internal class ChatInboxController(
    private val chatUuid: String,
    private val scope: CoroutineScope,
    private val repository: MessageRepository,
    private val state: () -> ChatConversationUiState,
    private val setState: (ChatConversationUiState) -> Unit,
    private val mergeMessages: (List<Message>) -> Unit,
    private val cacheNewGifs: (List<Message>) -> Unit,
    private val refreshReceipts: suspend (Boolean) -> Unit,
    private val serverSince: () -> String?,
    private val rememberServerTime: (List<Message>) -> Unit,
    private val updatedSince: () -> String?,
    private val rememberUpdatedCheck: (String) -> Unit
) {

    /** Первая загрузка экрана уже завершилась: больше не мигаем спиннером. */
    private var initialLoadDone = false

    /**
     * Догрузка истории вверх: подтягивает сообщения старше самого раннего в
     * списке. Пока идёт запрос или история исчерпана, повторные вызовы
     * игнорируются. Локальные (ещё не отправленные) сообщения пропускаем —
     * у них нет серверного uuid.
     */
    fun loadOlder() {
        val current = state()
        if (current.isLoadingMore || current.historyReached) return

        val oldest = current.messages.firstOrNull {
            it.messageUuid.isNotBlank() && !it.messageUuid.startsWith(LOCAL_MESSAGE_PREFIX)
        } ?: return

        setState(current.copy(isLoadingMore = true))
        scope.launch {
            repository.loadMessagesBefore(chatUuid, oldest.messageUuid, PAGE_SIZE)
                .collect { result ->
                    when (result) {
                        is Resource.Loading -> Unit
                        is Resource.Success -> {
                            val older = result.data.orEmpty()
                            mergeMessages(older)
                            setState(
                                state().copy(
                                    isLoadingMore = false,
                                    // Неполная страница означает, что история кончилась.
                                    historyReached = older.size < PAGE_SIZE
                                )
                            )
                        }
                        is Resource.Error ->
                            setState(state().copy(isLoadingMore = false))
                    }
                }
        }
    }

    /** @return true, если запрос завершился успешно (нужно для backoff-а). */
    suspend fun loadOnce(incremental: Boolean = false): Boolean {
        var success = true
        val since = serverSince()
        val source = if (incremental && since != null) {
            repository.loadMessagesAfter(chatUuid, since)
        } else {
            repository.loadMessages(chatUuid)
        }
        source.collect { result ->
            when (result) {
                // Спиннер показываем только когда показать нечего: переписка из
                // кэша уже на экране, и мигать индикатором при входе незачем.
                is Resource.Loading -> setState(
                    state().copy(
                        isLoading = state().messages.isEmpty() && !initialLoadDone
                    )
                )

                is Resource.Success -> {
                    initialLoadDone = true
                    setState(state().copy(isLoading = false))
                    val incoming = result.data ?: emptyList()
                    rememberServerTime(incoming)
                    cacheNewGifs(incoming)
                    mergeMessages(incoming)
                    refreshReceipts(incoming.isNotEmpty())
                }

                is Resource.Error -> {
                    success = false
                    initialLoadDone = true
                    setState(state().copy(isLoading = false, error = result.message))
                }
            }
        }
        return success
    }

    /**
     * Сверка правок без полной страницы: запрашиваем только сообщения,
     * изменённые с прошлой проверки. Полная страница на 50 сообщений весит
     * ~67 КБ, а изменений обычно нет вовсе — ответ пустой, ~2 байта.
     *
     * @return true, если запрос завершился успешно.
     */
    suspend fun loadUpdatedOnce(): Boolean {
        // Сдвиг в прошлое страхует от разницы часов и от строк, зафиксированных
        // уже после запроса: повторная обработка тех же сообщений безвредна.
        val since = updatedSince()
            ?: LocalTimeFormatter.serverTimestamp(UPDATED_OVERLAP_SECONDS)
        val updated = repository.loadUpdatedMessages(chatUuid, since) ?: return false
        rememberUpdatedCheck(LocalTimeFormatter.serverTimestamp(UPDATED_OVERLAP_SECONDS))
        if (updated.isEmpty()) return true
        cacheNewGifs(updated)
        mergeMessages(updated)
        // Квитанции не форсируем: /updated приходит и с повторами из окна
        // перекрытия, а галочки живут в своём ритме (30 с).
        refreshReceipts(false)
        return true
    }

    private companion object {
        const val PAGE_SIZE = 50
        /**
         * Перекрытие окна сверки правок, секунды. Больше шага сверки, чтобы
         * не потерять строку, зафиксированную между запросами, но и не
         * пересылать одно и то же слишком много раз.
         */
        const val UPDATED_OVERLAP_SECONDS = 15L
    }
}
