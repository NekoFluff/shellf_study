package com.crazyfluff.shellfstudy.core.designsystem.subjectdetail

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

private val CSS_VAR = Regex("""var\(\s*--[\w-]+\s*,\s*([^()]+)\)""")

/**
 * WaniKani's glyph-less radical images (`character_images`) theme their stroke color with a CSS
 * custom property — e.g. `stroke:var(--color-text, #000)` — so the same SVG renders correctly on
 * both light and dark wanikani.com. AndroidSVG 1.4 (which coil-svg's `SvgDecoder` wraps) has no
 * `var()` support: parsing it as a color throws, which silently drops the *entire* CSS rule it
 * appeared in (not just the one bad declaration) — so every one of these radicals decodes with
 * `stroke:none` and renders completely invisible, even though the fetch and decode both "succeed"
 * with no logged error. This substitutes each `var(--x, fallback)` with its literal fallback
 * before AndroidSVG ever sees the bytes. [SubjectGlyph] then tints the (now-visible, flat black)
 * result to the current subject-type color instead of trusting that hardcoded fallback.
 */
object SvgCssVariableInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body ?: return response
        val contentType = body.contentType()
        if (contentType?.toString()?.contains("svg", ignoreCase = true) != true) return response

        val patched = CSS_VAR.replace(body.string()) { match -> match.groupValues[1].trim() }
        return response.newBuilder()
            .body(patched.toResponseBody(contentType))
            .build()
    }
}
