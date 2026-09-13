package com.crazyfluff.shellfstudy.shared.feature.leaderboard

/**
 * Everything the friends screen can ask its state holder to do, as one type — so the screen and its
 * dialogs take one parameter instead of a callback per action.
 *
 * `LeaderboardViewModel` is the production implementation and is what `LeaderboardRoute` passes down.
 * The interface exists, rather than the composables naming the concrete ViewModel, so a screen test can
 * substitute a recording stand-in and keep rendering arbitrary `LeaderboardUiState` values.
 *
 * Same shape as `LessonActions` / `ReviewActions`. Kept separate rather than shared because the action
 * sets are unrelated and no screen should be able to reach another feature's state holder.
 */
interface LeaderboardActions {
    fun onRefresh()

    fun onAddFriendNicknameChange(value: String)

    fun onAddFriendTokenChange(value: String)

    fun onAddFriendConfirm()

    fun onRemoveFriend(id: String)

    fun onEditNickname(id: String, nickname: String)
}
