package com.example.muzea.data.model

import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Контракт разбора ответов чат-сервера на стороне клиента.
 *
 * <p>Регрессия, которую закрывает этот тест: бэкенд отдаёт ключ `"online"`, а
 * клиент читал `@SerializedName("isOnline")`. Gson не падает на отсутствующем
 * ключе — он подставляет значение по умолчанию, то есть `false` для примитива
 * `Boolean`. Индикатор «в сети» поэтому не горел ни у кого, и никаких ошибок
 * в логах не было.
 *
 * <p>Тест разбирает реальную форму ответа и проверяет именно факт разбора
 * `true`, а не наличие поля: одно лишь `assertTrue(json.contains("online"))`
 * пропустило бы прежний баг.
 */
class PresenceDeserializationTest {

    private val gson = GsonBuilder().create()

    @Test
    fun `contact status is read from online key`() {
        val json = """
            {
              "contactUuid": "c-1",
              "contactUserId": 9,
              "contactUserUuid": "u-9",
              "username": "alice",
              "fullName": "Alice Smith",
              "avatarUrl": null,
              "contactName": null,
              "online": true,
              "lastSeenAt": "2026-09-22T12:00:00Z",
              "addedAt": "2026-09-20T09:00:00Z"
            }
        """.trimIndent()

        val contact = gson.fromJson(json, ContactResponse::class.java)

        assertTrue(
            "ключ online обязан читаться как true, иначе «в сети» не горит",
            contact.isOnline
        )
        assertEquals("2026-09-22T12:00:00Z", contact.lastSeenAt)
        assertEquals("Alice Smith", contact.displayName())
    }

    @Test
    fun `chat user status is read from online key`() {
        val json = """
            {
              "userUuid": "u-9",
              "username": "alice",
              "email": "alice@example.com",
              "firstName": "Alice",
              "lastName": "Smith",
              "fullName": "Alice Smith",
              "avatarUrl": null,
              "online": true
            }
        """.trimIndent()

        assertTrue(gson.fromJson(json, ChatUserResponse::class.java).isOnline)
    }

    @Test
    fun `presence batch response is parsed`() {
        val json = """
            [
              {"userUuid": "u-1", "online": true,  "lastSeenAt": "2026-09-22T12:00:00Z"},
              {"userUuid": "u-2", "online": false, "lastSeenAt": "2026-09-22T09:30:00Z"},
              {"userUuid": "u-3", "online": false, "lastSeenAt": null}
            ]
        """.trimIndent()

        val presence = gson.fromJson(json, Array<PresenceResponse>::class.java).toList()

        assertEquals(3, presence.size)
        assertTrue(presence[0].online)
        assertFalse(presence[1].online)
        assertEquals("2026-09-22T09:30:00Z", presence[1].lastSeenAt)
        // Пользователь, ни разу не заходивший, остаётся без времени визита:
        // «был(а) N назад» для него показывать нечего.
        assertEquals(null, presence[2].lastSeenAt)
    }

    @Test
    fun `legacy isOnline key does not resurrect the old bug`() {
        // Если кто-то вернёт @SerializedName("isOnline"), тест упадёт на
        // первом кейсе, а не на проде у пользователей.
        val legacy = """{"contactUuid":"c-1","contactUserId":9,"contactUserUuid":"u-9","isOnline":true}"""

        assertFalse(gson.fromJson(legacy, ContactResponse::class.java).isOnline)
    }
}