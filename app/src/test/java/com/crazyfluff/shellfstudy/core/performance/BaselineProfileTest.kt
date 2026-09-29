package com.crazyfluff.shellfstudy.core.performance

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Guards `app/src/main/baseline-prof.txt` against the two ways it can rot silently.
 *
 * A malformed profile fails the build, so that case is already covered — but only for release
 * builds, and the friends distribution builds `assembleDebug`. Worse, the failure mode of a profile
 * that *parses* but is wrong is invisible: it changes dex layout and AOT compilation with no error,
 * and the only symptom is a startup profile that predates a refactor and no longer covers it. Neither
 * AGP nor any existing test notices that, which is why this exists.
 *
 * The signatures themselves cannot be validated here — they must be checked against the dex of a
 * release build, and the procedure for doing that is documented in the profile's own header. What
 * this test pins is the structure: flags present on every rule, no duplicate rules, and classes that
 * are actually part of this app rather than a stale package name.
 */
class BaselineProfileTest {

    private val profileFile = File("src/main/baseline-prof.txt")

    private val rules: List<String>
        get() = profileFile.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

    @Test
    fun `profile exists and is not empty`() {
        assertThat(profileFile.exists()).isTrue()
        assertThat(rules).isNotEmpty()
    }

    /**
     * Every rule needs a bracketed flag block naming at least one of the tags ART understands —
     * `HOT_STARTUP`, `STARTUP`, `POST_STARTUP` (or the bare `H`/`S`/`P` forms). Without one,
     * `compileReleaseArtProfile` fails with "At least one of flags 'H', 'S', 'P' must be specified" —
     * which is how this file first broke, and worth catching before a release build does.
     */
    @Test
    fun `every rule declares at least one H S P flag`() {
        val knownTags = setOf("HOT_STARTUP", "STARTUP", "POST_STARTUP", "H", "S", "P")

        val malformed = rules.filter { rule ->
            val tags = Regex("""^\[([^\]]*)]""").find(rule)?.groupValues?.get(1)
                ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            tags.isNullOrEmpty() || tags.none { it in knownTags }
        }

        assertThat(malformed).isEmpty()
    }

    /** A rule has no spaces once the flag prefix is removed — the descriptor itself never contains one. */
    @Test
    fun `every rule is a single descriptor after its flags`() {
        val malformed = rules.mapNotNull { rule ->
            val withoutFlags = rule.substringAfter("] ", missingDelimiterValue = "")
            if (withoutFlags.isEmpty() || withoutFlags.contains(' ')) rule else null
        }

        assertThat(malformed).isEmpty()
    }

    @Test
    fun `no duplicate rules`() {
        val descriptors = rules.map { it.substringAfter("] ") }
        val duplicates = descriptors.groupingBy { it }.eachCount().filterValues { it > 1 }.keys

        assertThat(duplicates).isEmpty()
    }

    /**
     * Every rule names a class in this app. The Kotlin package is
     * `com.crazyfluff.shellfstudy`, and the Android entry points share that prefix. A rule pointing
     * at a package that no longer exists is the signature of a profile that was renamed or moved and
     * never regenerated — exactly the rot this test is for.
     */
    @Test
    fun `every rule targets this app's own classes`() {
        val wrongPackage = rules.map { it.substringAfter("] ") }
            .filterNot { it.startsWith("Lcom/crazyfluff/shellfstudy/") }

        assertThat(wrongPackage).isEmpty()
    }

    /**
     * The splash path is the one screen every cold start composes, so it is the least defensible thing
     * to lose from this profile. Spelled out as its own test so a future edit that trims the file
     * fails here with the reason rather than only in review.
     */
    @Test
    fun `profile covers the cold-start path`() {
        val descriptors = rules.map { it.substringAfter("] ") }

        assertThat(descriptors.any { it.contains("ShellfStudyApplication") }).isTrue()
        assertThat(descriptors.any { it.contains("MainActivity") }).isTrue()
        assertThat(descriptors.any { it.contains("ShellfStudyAppKt") }).isTrue()
        assertThat(descriptors.any { it.contains("feature/splash/SplashScreenKt") }).isTrue()
    }
}
