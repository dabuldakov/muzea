package com.example.muzea.ui.contact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.domain.model.Chat
import com.example.muzea.domain.model.Contact
import com.example.muzea.domain.repository.ContactRepository
import com.example.muzea.domain.usecase.OpenPrivateChatUseCase
import com.example.muzea.core.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Единое состояние списка контактов (список + загрузка + ошибка). */
data class ContactListUiState(
    val contacts: List<Contact> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ContactViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val openPrivateChatUseCase: OpenPrivateChatUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactListUiState())
    val uiState: StateFlow<ContactListUiState> = _uiState.asStateFlow()

    private val _addContactResult = MutableSharedFlow<Resource<Contact>>()
    val addContactResult: SharedFlow<Resource<Contact>> = _addContactResult.asSharedFlow()

    private val _createChatResult = MutableSharedFlow<Resource<Chat>>()
    val createChatResult: SharedFlow<Resource<Chat>> = _createChatResult.asSharedFlow()

    fun loadContacts() {
        viewModelScope.launch {
            contactRepository.loadContacts().collect { result ->
                when (result) {
                    is Resource.Loading -> _uiState.value =
                        _uiState.value.copy(isLoading = _uiState.value.contacts.isEmpty())

                    is Resource.Success -> _uiState.value = _uiState.value.copy(
                        contacts = result.data ?: emptyList(),
                        isLoading = false,
                        error = null
                    )

                    is Resource.Error -> _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    /** Ошибку показали — сбрасываем, чтобы не повторялась. */
    fun consumeError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Обновляет только статусы, не трогая состав списка.
     *
     * Порядок и элементы сохраняются: меняется лишь пара полей у контактов,
     * чей статус реально обновился, поэтому DiffUtil перерисовывает только эти
     * строки и не сбрасывает скролл.
     */
    fun refreshPresence() {
        viewModelScope.launch {
            val uuids = _uiState.value.contacts.mapNotNull { it.contactUserUuid }
            if (uuids.isEmpty()) return@launch

            val presence = try {
                contactRepository.loadPresence(uuids)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch
            }

            // Пустой ответ — это сбой сети, а не «все офлайн»: молчаливый
            // rewrite в false показал бы неверный статус у всех подряд.
            if (presence.isEmpty()) return@launch

            _uiState.value = _uiState.value.copy(
                contacts = _uiState.value.contacts.map { contact ->
                    val uuid = contact.contactUserUuid
                    val fresh = uuid?.let { presence[it] }
                    if (fresh == null) {
                        contact
                    } else {
                        contact.copy(
                            isOnline = fresh.online,
                            lastSeenAt = fresh.lastSeenAt ?: contact.lastSeenAt
                        )
                    }
                }
            )
        }
    }

    fun addContact(username: String) {
        viewModelScope.launch {
            contactRepository.addContactByUsername(username).collect { result ->
                _addContactResult.emit(result)
            }
        }
    }

    /**
     * Открывает переписку с контактом: если приватный чат уже существует —
     * возвращает его, иначе создаёт новый. Так повторное нажатие на контакт
     * не плодит дубликаты чатов.
     */
    fun openPrivateChat(userUuid: String) {
        viewModelScope.launch {
            _createChatResult.emit(Resource.Loading())
            _createChatResult.emit(openPrivateChatUseCase(userUuid))
        }
    }
}
