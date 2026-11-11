package dev.pyte.data.remote

sealed interface ListIdResult {
    data class Valid(val listId: String) : ListIdResult
    data class Invalid(val reason: String) : ListIdResult
}

/** Turns a pasted YouTube / YouTube Music link into a playlist id. Pure logic, unit tested. */
object ListIdParser {
    private val listParam = Regex("""[?&]list=([A-Za-z0-9_-]+)""")
    private val hostPattern = Regex("""^https?://([^/?#]+)""", RegexOption.IGNORE_CASE)
    private val bareId = Regex("""^(PL|OL|UU|VL|FL|RDCLAK)[A-Za-z0-9_-]+$""")
    private val supportedHost = Regex("""(^|\.)(youtube\.com|youtu\.be)$""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): ListIdResult {
        val input = raw.trim()
        if (input.isEmpty()) return ListIdResult.Invalid("Paste a playlist link to get started")

        val id: String = listParam.find(input)?.groupValues?.get(1)?.let { candidate ->
            val host = hostPattern.find(input)?.groupValues?.get(1)?.substringBefore(':')
            if (host == null || !supportedHost.containsMatchIn(host)) {
                return ListIdResult.Invalid("Only YouTube and YouTube Music links are supported")
            }
            candidate
        } ?: if (bareId.matches(input)) {
            input
        } else {
            return ListIdResult.Invalid("That doesn't look like a YouTube playlist link")
        }

        return when {
            id.startsWith("RD") && !id.startsWith("RDCLAK") -> ListIdResult.Invalid("Radio mixes are endless and can't be imported")
            id.startsWith("LM") || id.startsWith("LL") || id.startsWith("WL") ->
                ListIdResult.Invalid("Liked and Watch Later need a YouTube account. Import a regular playlist instead")
            else -> ListIdResult.Valid(id)
        }
    }
}
