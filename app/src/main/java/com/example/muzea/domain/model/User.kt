package com.example.muzea.domain.model

/** Доменная модель пользователя (имена полей совпадают с сетевым DTO). */
data class User(
    val id: Long,
    val userName: String,
    val email: String,
    val fullName: String?,
    val avatarUrl: String?,
    val role: String,
    val createdAt: String?,
    val enabled: Boolean
)
