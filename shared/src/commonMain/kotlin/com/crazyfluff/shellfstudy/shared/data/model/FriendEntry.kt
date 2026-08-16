package com.crazyfluff.shellfstudy.shared.data.model

import kotlinx.serialization.Serializable

@Serializable
data class FriendEntry(
    val id: String,
    val nickname: String,
    val encryptedToken: String
)
