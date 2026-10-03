package com.example.muzea.ui.contact

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.muzea.domain.model.Chat
import com.example.muzea.domain.model.Contact
import com.example.muzea.domain.usecase.OpenPrivateChatUseCase
import com.example.muzea.domain.repository.ContactRepository
import com.example.muzea.utils.NetworkResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ContactViewModel @Inject constructor(
    private val contactRepository: ContactRepository,
    private val openPrivateChatUseCase: OpenPrivateChatUseCase
) : ViewModel() {

    private val _contactsResult = MutableSharedFlow<NetworkResult<List<Contact>>>()
    val contactsResult: SharedFlow<NetworkResult<List<Contact>>> = _contactsResult.asSharedFlow()

    private val _addContactResult = MutableSharedFlow<NetworkResult<Contact>>()
    val addContactResult: SharedFlow<NetworkResult<Contact>> = _addContactResult.asSharedFlow()

    private val _createChatResult = MutableSharedFlow<NetworkResult<Chat>>()
    val createChatResult: SharedFlow<NetworkResult<Chat>> = _createChatResult.asSharedFlow()

    /**
     * Актуальный список контактов вместе со статусом «в сети».
     *
     * Отдельный StateFlow, а не расширение contactsResult: список контактов
     * меняется редко, а статус — каждые несколько секунд. Смешали бы в одном
     * потоке — пришлось бы перезагружать /api/contacts ради смены индикатора,
     * то есть дёргать тяжёлый запрос каждые 20 секунд.
     */
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    fun loadContacts() {
        viewModelScope.launch {
            contactRepository.loadContacts().collect { result ->
                if (result is NetworkResult.Success) {
                    _contacts.value = result.data ?: emptyList()
                }
                _contactsResult.emit(result)
            }
        }
    }

    /**
     * Обновляет только статусы, не трогая сам список контактов.
     *
     * Порядок и состав элементов сохраняются: меняется лишь пара полей у
     * контактов, чей статус реально обновился, поэтому DiffUtil перерисовывает
     * только эти строки и не сбрасывает скролл.
     */
    fun refreshPresence() {
        viewModelScope.launch {
            val uuids = _contacts.value.mapNotNull { it.contactUserUuid }
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

            _contacts.value = _contacts.value.map { contact ->
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
            _createChatResult.emit(NetworkResult.Loading())
            _createChatResult.emit(openPrivateChatUseCase(userUuid))
        }
    }
}