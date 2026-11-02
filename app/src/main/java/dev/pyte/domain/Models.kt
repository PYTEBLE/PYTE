package dev.pyte.domain

data class Track(
    val videoId: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val thumbnailUrl: String?,
)

data class PlaylistSummary(
    val id: String,
    val title: String,
    val owner: String?,
    val thumbnailUrl: String?,
    val trackCount: Int,
    val sourceUrl: String,
    val importedAt: Long,
) {
    val isLocal: Boolean get() = id.startsWith("local:")
}
