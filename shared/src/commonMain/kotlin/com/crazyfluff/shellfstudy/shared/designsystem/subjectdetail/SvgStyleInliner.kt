package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

private val CSS_VAR = Regex("""var\(\s*--[\w-]+\s*,\s*([^()]+)\)""")
// [\s\S] rather than `.` with DOT_MATCHES_ALL, which common Kotlin does not have.
private val STYLE_ELEMENT = Regex("""<style[^>]*>([\s\S]*?)</style>""")
private val CSS_RULE = Regex("""([^{}]+)\{([^{}]*)\}""")
private val START_TAG = Regex("""<([A-Za-z][\w:-]*)((?:\s+[\w:-]+\s*=\s*(?:"[^"]*"|'[^']*'))*)\s*(/?)>""")
private val ATTRIBUTE = Regex("""([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)')""")

/**
 * Rewrites a WaniKani `character_images` SVG so that every style it relies on is a plain presentation
 * attribute on the element it applies to.
 *
 * Those SVGs style their strokes through a `<style>` block of class rules, with the color behind a
 * CSS custom property — `.b{stroke:var(--color-text, #000);…}` — so one file themes itself on both
 * light and dark wanikani.com. Neither platform's SVG renderer copes with that as delivered:
 * - AndroidSVG has no `var()` support, and a declaration it cannot parse drops the *whole* rule, so
 *   every stroke decodes as `stroke:none` and the radical renders invisible without any error.
 * - Skia's SVG module, which Coil uses on iOS, does not apply `<style>` stylesheets at all.
 *
 * Presentation attributes are the one form both read, so the stylesheet is resolved here instead:
 * each `var(--x, fallback)` becomes its fallback, class rules are applied in stylesheet order (a later
 * rule wins, as in CSS), an element's own `style` attribute overrides those, and the `<style>` block
 * is removed. [SubjectGlyph] tints the result to the subject-type color, so the fallback's hardcoded
 * black never reaches the screen.
 *
 * Only what those files use is supported — `.class` selectors, possibly comma-separated. A rule with
 * any other selector is ignored rather than guessed at.
 */
fun inlineSvgStyles(svg: String): String {
    val resolved = CSS_VAR.replace(svg) { it.groupValues[1].trim() }
    val rules = STYLE_ELEMENT.findAll(resolved).flatMap { parseRules(it.groupValues[1]) }.toList()
    if (rules.isEmpty() && "style=" !in resolved) return resolved

    return START_TAG.replace(resolved.replace(STYLE_ELEMENT, "")) { tag ->
        val (name, attributeText, selfClosing) = tag.destructured
        val attributes = ATTRIBUTE.findAll(attributeText)
            .associateTo(LinkedHashMap()) { it.groupValues[1] to (it.groups[2] ?: it.groups[3])!!.value }
        val classes = attributes["class"]?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }?.toSet().orEmpty()
        val inlineStyle = attributes.remove("style")?.let(::parseDeclarations).orEmpty()
        if (classes.isEmpty() && inlineStyle.isEmpty()) return@replace tag.value

        rules.filter { it.className in classes }.forEach { attributes.putAll(it.declarations) }
        attributes.putAll(inlineStyle)
        buildString {
            append('<').append(name)
            attributes.forEach { (key, value) -> append(' ').append(key).append("=\"").append(value.replace("\"", "&quot;")).append('"') }
            append(selfClosing).append('>')
        }
    }
}

private class ClassRule(val className: String, val declarations: List<Pair<String, String>>)

private fun parseRules(css: String): List<ClassRule> = CSS_RULE.findAll(css).flatMap { rule ->
    val selectors = rule.groupValues[1].split(',').map { it.trim() }
    val declarations = parseDeclarations(rule.groupValues[2])
    if (selectors.all { it.matches(Regex("""\.[\w-]+""")) }) {
        selectors.map { ClassRule(it.removePrefix("."), declarations) }
    } else {
        emptyList()
    }
}.toList()

private fun parseDeclarations(css: String): List<Pair<String, String>> = css.split(';').mapNotNull { declaration ->
    val colon = declaration.indexOf(':')
    if (colon <= 0) return@mapNotNull null
    val property = declaration.substring(0, colon).trim()
    val value = declaration.substring(colon + 1).trim()
    if (property.isEmpty() || value.isEmpty()) null else property to value
}
