package com.crazyfluff.shellfstudy.shared.feature.leaderboard

object LeaderboardScreenTestTags {
    const val ADD_FRIEND_BUTTON = "friends_add_button"
    const val ADD_FRIEND_ROW = "friends_add_row"
    const val ADD_FRIEND_CONFIRM = "friends_add_confirm"
    const val NICKNAME_FIELD = "friends_nickname_field"
    const val TOKEN_FIELD = "friends_token_field"
    const val ERROR_BANNER = "friends_error_banner"
    const val REMOVE_CONFIRM = "friends_remove_confirm"

    fun friendRow(friendId: String) = "friends_row_$friendId"
}
