package com.example.muzea.data.mapper

import com.example.muzea.domain.model.User

/** Преобразование сетевого DTO пользователя в доменную модель. */
fun com.example.muzea.data.model.UserResponse.toDomain(): User = User(
    id = id,
    userName = userName,
    email = email,
    fullName = fullName,
    avatarUrl = avatarUrl,
    role = role,
    createdAt = createdAt,
    enabled = enabled
)
