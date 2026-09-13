package com.crazyfluff.shellfstudy.architecture

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

/**
 * Enforces, mechanically, the parts of CLAUDE.md's "Composables and ViewModels" rule that are cheap to
 * violate by accident. A written rule with no enforcement point loses to a one-line addition — which
 * is exactly how `LessonScreen` reached 27 callbacks with the rule already documented.
 *
 * Two invariants:
 *
 * 1. **`koinViewModel()` is only constructed at a boundary that owns the ViewModel** — an `XxxRoute`,
 *    the app root, or a sheet host that deliberately owns one. A nested content composable calling it
 *    silently acquires its *own* scoped instance instead of the caller's, which stays invisible until
 *    a shared-state bug or a test shows up.
 * 2. **Nothing under `designsystem/` mentions a `ViewModel`.** Those composables are shared across
 *    features (`QuizQuestionContent` by both Lesson and Review, `SubjectDetailContent` by four
 *    surfaces), so one feature's state holder cannot be a dependency of them.
 *
 * Deliberate exceptions live in [VM_OWNING_COMPOSABLES] with a reason, so widening either rule is a
 * reviewed edit rather than silent drift.
 */
class ArchitectureConventionsTest {

    @Test
    fun `koinViewModel is only constructed at a composable that owns the ViewModel`() {
        val offenders = mainSources().flatMap { file ->
            val source = file.readText().withoutComments()
            KOIN_VIEW_MODEL.findAll(source).mapNotNull { match ->
                val owner = enclosingFunctionName(source, match.range.first)
                val allowed = owner.endsWith("Route") || (file.name to owner) in VM_OWNING_COMPOSABLES
                if (allowed) null else "${file.name}: $owner()"
            }
        }.toList()

        assertThat(offenders.joinToString("\n")).isEmpty()
    }

    @Test
    fun `designsystem composables never depend on a ViewModel`() {
        val offenders = mainSources()
            .filter { "/designsystem/" in it.path.withSlashes() }
            .filter { "ViewModel" in it.readText().withoutComments() }
            .map { it.name }
            .toList()

        assertThat(offenders.joinToString("\n")).isEmpty()
    }

    /** The main (non-test) Kotlin sources of both modules. */
    private fun mainSources(): Sequence<File> = sequenceOf("shared/src", "app/src")
        .map { File(repoRoot(), it) }
        .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" } }
        .filterNot { file ->
            val path = file.path.withSlashes()
            "/build/" in path || TEST_SOURCE_SET.containsMatchIn(path)
        }

    /** Walks up from the test's working directory to the Gradle root, so the scan is CWD-independent. */
    private fun repoRoot(): File {
        var dir = File(System.getProperty("user.dir")).absoluteFile
        while (dir.parentFile != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return dir
    }

    /** The nearest enclosing `fun name(` above [offset], or "?" if the match sits outside a function. */
    private fun enclosingFunctionName(source: String, offset: Int): String =
        FUNCTION_DECL.findAll(source.substring(0, offset)).lastOrNull()?.groupValues?.get(1) ?: "?"

    /** Comments are stripped first: prose *about* `koinViewModel()` is not a call to it. */
    private fun String.withoutComments(): String =
        replace(BLOCK_COMMENT, "").replace(LINE_COMMENT, "")

    private fun String.withSlashes(): String = replace('\\', '/')

    private companion object {
        val KOIN_VIEW_MODEL = Regex("""koinViewModel\(\)""")
        val FUNCTION_DECL = Regex("""\bfun\s+(?:<[^>]*>\s*)?(?:[\w.<>?,\s]+\.)?(\w+)\s*\(""")
        val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
        val LINE_COMMENT = Regex("""//[^\n]*""")
        val TEST_SOURCE_SET = Regex("""/(common|android|ios|desktop|jvm)Test/|/test/|/androidTest/""")

        /**
         * Composable boundaries that own a ViewModel without being named `XxxRoute`, and why.
         */
        val VM_OWNING_COMPOSABLES = setOf(
            // The iOS app root, the mirror of MainActivity's setContent below.
            "ShellfStudyApp.kt" to "ShellfStudyApp",
            // The Android app root.
            "MainActivity.kt" to "onCreate",
            // The shared detail sheet's host: it owns one instance across every host screen so a
            // drill-down stack survives, and is only composed once the sheet is open.
            "SubjectDetailSheet.kt" to "SubjectDetailBody"
        )
    }
}
