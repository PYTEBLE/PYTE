package dev.pyte.data.remote

import java.net.URI

/** Only rewrite known, resizable YouTube image hosts; never alter signed arbitrary URLs. */
object ArtworkUrls {
    fun sized(url: String?, pixels: Int = 1200): String? {
        if (url == null) return null
        val host = runCatching { URI(url).host }.getOrNull() ?: return url
        if (host == "i.ytimg.com" && pixels >= 720) {
            return url.replace(Regex("/(?:default|mqdefault|hqdefault|sddefault)\\.(jpg|webp)(?=\\?|$)"), "/maxresdefault.$1")
        }
        if (host != "lh3.googleusercontent.com" && host != "lh3.google.com" &&
            !host.endsWith(".ggpht.com")) return url
        val size = pixels.coerceIn(96, 1600)
        return url.replace(Regex("([=-])w\\d+"), "$1w$size")
            .replace(Regex("-h\\d+"), "-h$size")
            .replace(Regex("=s\\d+"), "=s$size")
    }
}
