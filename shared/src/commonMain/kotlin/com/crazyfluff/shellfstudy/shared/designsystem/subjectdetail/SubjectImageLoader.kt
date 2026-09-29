package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.svg.Svg
import coil3.svg.SvgDecoder
import okio.Buffer

/**
 * The app's Coil [ImageLoader], the same on both platforms — installed once by `ShellfStudyApp`.
 *
 * Radicals with no Unicode glyph (e.g. "Death Star") render from `character_images`, which WaniKani
 * only ever supplies as SVG, so the decoder is registered explicitly. Fetching goes through Ktor,
 * which picks up the engine each platform already ships (OkHttp on Android, Darwin on iOS).
 */
fun newSubjectImageLoader(context: PlatformContext): ImageLoader =
    ImageLoader.Builder(context)
        .components {
            add(KtorNetworkFetcherFactory())
            add(SvgDecoder.Factory(parser = WaniKaniSvgParser))
        }
        .build()

/** Coil's own SVG parser, handed WaniKani's SVGs with their stylesheet already inlined — see
 *  [inlineSvgStyles] for why neither platform's renderer can take them as delivered. */
private val WaniKaniSvgParser = Svg.Parser { source ->
    Svg.Parser.DEFAULT.parse(Buffer().writeUtf8(inlineSvgStyles(source.readUtf8())))
}
