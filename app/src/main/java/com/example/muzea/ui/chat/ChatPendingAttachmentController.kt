package com.example.muzea.ui.chat

import android.content.Context
import android.net.Uri
import com.example.muzea.R
import com.example.muzea.core.Resource
import com.example.muzea.data.repository.PendingAttachmentStore
import com.example.muzea.data.repository.StoredPendingAttachment
import com.example.muzea.domain.model.MessageAttachment
import com.example.muzea.domain.repository.MessageRepository
import com.example.muzea.utils.MediaFileReader
import com.example.muzea.utils.NetworkMonitor
import com.example.muzea.utils.PreparedAttachment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Выбранные вложения: подготовка файлов, загрузка в хранилище, повторы и
 * сохранение черновика между запусками. Состояние живёт в общем
 * [ChatConversationUiState], контроллер только меняет его через колбэки.
 */
internal class ChatPendingAttachmentController(
    private val chatUuid: String,
    private val scope: CoroutineScope,
    private val repository: MessageRepository,
    private val mediaFileReader: MediaFileReader,
    private val store: PendingAttachmentStore,
    private val networkMonitor: NetworkMonitor,
    private val context: Context,
    private val state: () -> ChatConversationUiState,
    private val setState: (ChatConversationUiState) -> Unit,
    private val emitError: suspend (String) -> Unit
) {

    fun restore() {
        val stored = store.load(chatUuid)
        if (stored.isEmpty()) return
        setState(state().copy(pendingAttachments = stored.map { toPending(it) }))
        if (networkMonitor.status.value.online) retryQueued()
    }

    fun retryQueued() {
        val ids = state().pendingAttachments
            .filter { !it.isUploaded && it.error == null }
            .map { it.id }
        if (ids.isEmpty()) return
        scope.launch {
            for (id in ids) upload(id)
        }
    }

    /** Добавляет выбранные в системном пикере файлы и сразу загружает их. */
    fun add(uris: List<Uri>) {
        if (uris.isEmpty()) return

        scope.launch {
            setState(state().copy(isUploadingAttachments = true))
            try {
                for (uri in uris) {
                    val prepared = prepare(uri) ?: continue

                    val id = UUID.randomUUID().toString()
                    val pending = PendingAttachment(
                        id = id,
                        name = prepared.name,
                        size = prepared.size,
                        mimeType = prepared.mimeType,
                        type = prepared.type,
                        prepared = prepared
                    )
                    setState(state().copy(pendingAttachments = state().pendingAttachments + pending))
                    persist()
                    upload(id)
                }
            } finally {
                setState(state().copy(isUploadingAttachments = false))
            }
        }
    }

    fun remove(id: String) {
        val pending = state().pendingAttachments.firstOrNull { it.id == id } ?: return
        mediaFileReader.cleanup(pending.prepared)
        setState(state().copy(pendingAttachments = state().pendingAttachments.filterNot { it.id == id }))
        persist()
    }

    fun retry(id: String) {
        scope.launch {
            setState(state().copy(isUploadingAttachments = true))
            try {
                upload(id)
            } finally {
                setState(state().copy(isUploadingAttachments = false))
            }
        }
    }

    /** Убирает все вложения после успешной отправки и чистит их файлы. */
    fun clearAll() {
        state().pendingAttachments.forEach { mediaFileReader.cleanup(it.prepared) }
        store.clear(chatUuid)
        setState(state().copy(pendingAttachments = emptyList()))
    }

    /**
     * Провайдеры content:// бросают разные исключения (нет файла, нет прав,
     * неожиданный формат). Ошибку показываем пользователю, а не роняем экран.
     */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun prepare(uri: Uri): PreparedAttachment? =
        try {
            mediaFileReader.prepare(uri)
        } catch (e: Exception) {
            reportAttachError(e)
            null
        }

    private suspend fun reportAttachError(error: Exception) {
        emitError(error.message ?: context.getString(R.string.attachment_cannot_attach))
    }

    private suspend fun upload(id: String) {
        val pending = state().pendingAttachments.firstOrNull { it.id == id } ?: return
        val prepared = pending.prepared ?: return
        setState(
            state().copy(
                pendingAttachments = state().pendingAttachments.map {
                    if (it.id == id) it.copy(error = null) else it
                }
            )
        )

        when (val result = repository.uploadAttachment(chatUuid, prepared) { percent ->
            setState(
                state().copy(
                    pendingAttachments = state().pendingAttachments.map {
                        if (it.id == id) it.copy(progress = percent) else it
                    }
                )
            )
        }) {
            is Resource.Success -> {
                setState(
                    state().copy(
                        pendingAttachments = state().pendingAttachments.map {
                            if (it.id == id) it.copy(uploaded = result.data) else it
                        }
                    )
                )
                persist()
            }
            is Resource.Error -> setState(
                state().copy(
                    pendingAttachments = state().pendingAttachments.map {
                        if (it.id == id) it.copy(error = result.message) else it
                    }
                )
            )
            is Resource.Loading -> Unit
        }
    }

    private fun persist() {
        val stored = state().pendingAttachments.mapNotNull { pending ->
            val prepared = pending.prepared ?: return@mapNotNull null
            StoredPendingAttachment(
                id = pending.id,
                name = pending.name,
                size = pending.size,
                mimeType = pending.mimeType,
                type = pending.type,
                filePath = prepared.file.absolutePath,
                thumbnailPath = prepared.thumbnail?.absolutePath,
                width = prepared.width,
                height = prepared.height,
                duration = prepared.duration,
                uploadedUuid = pending.uploaded?.attachmentUuid
            )
        }
        store.save(chatUuid, stored)
    }

    private fun toPending(stored: StoredPendingAttachment): PendingAttachment {
        val file = File(stored.filePath)
        val prepared = PreparedAttachment(
            file = file,
            thumbnail = stored.thumbnailPath?.let(::File)?.takeIf { it.exists() },
            name = stored.name,
            size = stored.size,
            mimeType = stored.mimeType,
            type = stored.type,
            width = stored.width,
            height = stored.height,
            duration = stored.duration
        )
        val uploaded = stored.uploadedUuid?.let { uuid ->
            MessageAttachment(
                fileUrl = "",
                thumbnailUrl = null,
                type = stored.type,
                width = stored.width,
                height = stored.height,
                attachmentUuid = uuid,
                fileName = stored.name,
                fileSize = stored.size,
                mimeType = stored.mimeType,
                duration = stored.duration
            )
        }
        return PendingAttachment(
            id = stored.id,
            name = stored.name,
            size = stored.size,
            mimeType = stored.mimeType,
            type = stored.type,
            prepared = prepared,
            uploaded = uploaded
        )
    }
}
