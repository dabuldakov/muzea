package com.example.muzea.data.model

import com.google.gson.annotations.SerializedName

/**
 * Статус присутствия одного пользователя из `GET /api/presence`.
 *
 * `online` — вычислено сервером из `lastSeenAt` по TTL, а не прочитано из флага
 * в БД: убитый клиент просто выпадает из окна TTL. `lastSeenAt` приходит
 * всегда, пока пользователь хоть раз был в сети, поэтому его наличие важнее
 * самого флага.
 */
data class PresenceResponse(
    @SerializedName("userUuid")
    val userUuid: String,
    @SerializedName("online")
    val online: Boolean,
    @SerializedName("lastSeenAt")
    val lastSeenAt: String?
)