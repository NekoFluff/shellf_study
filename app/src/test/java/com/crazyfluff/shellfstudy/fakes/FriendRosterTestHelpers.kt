package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.shared.data.FriendAdded
import com.crazyfluff.shellfstudy.shared.data.FriendRepository
import com.crazyfluff.shellfstudy.shared.data.model.FriendEntry

/**
 * [FriendRepository.addFriend] for tests that start from a readable roster and only want the new
 * entry back. Fails loudly if the roster turned out to be unreadable, which no caller in that
 * position expects — the refusal path has its own tests in `FriendRepositoryTest`.
 */
suspend fun FriendRepository.addFriendOrFail(nickname: String, plainToken: String): FriendEntry =
    when (val added = addFriend(nickname, plainToken)) {
        is FriendAdded.Saved -> added.entry
        is FriendAdded.RosterUnreadable -> error("roster unexpectedly unreadable")
    }
