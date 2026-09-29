package com.crazyfluff.shellfstudy.shared.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The escaping `SubjectRepository.observeSearch` applies before `SubjectDao.observeSearch`'s
 * `LIKE ... ESCAPE '\'` — the half of the "a literal % matches that character" claim that the
 * real-SQL tests in `SubjectSearchQueryTest` take as given, since they hand the DAO a pattern that is
 * already escaped.
 *
 * The interesting case is the last one: escaping the escape character has to happen before the
 * wildcards, or the `\` this function inserts in front of a `%` would be escaped again by the same
 * pass, and the pattern would search for a backslash.
 */
class EscapeLikeWildcardsTest {

    @Test
    fun `leaves an ordinary query alone`() {
        assertEquals("mizu", escapeLikeWildcards("mizu"))
    }

    @Test
    fun `escapes a literal percent`() {
        assertEquals("50\\%", escapeLikeWildcards("50%"))
    }

    @Test
    fun `escapes a literal underscore`() {
        assertEquals("under\\_score", escapeLikeWildcards("under_score"))
    }

    @Test
    fun `escapes a literal backslash`() {
        assertEquals("back\\\\slash", escapeLikeWildcards("back\\slash"))
    }

    @Test
    fun `escapes the escape character before the wildcards it will insert`() {
        assertEquals("\\\\\\%", escapeLikeWildcards("\\%"))
        assertEquals("\\\\\\_", escapeLikeWildcards("\\_"))
    }
}
