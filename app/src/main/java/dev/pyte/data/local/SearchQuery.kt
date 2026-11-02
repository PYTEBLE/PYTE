package dev.pyte.data.local

/** Never feed user-entered operators or punctuation into SQLite MATCH syntax. */
internal fun searchQuery(input: String): String? = Regex("[\\p{L}\\p{N}]+")
    .findAll(input.take(500)).take(12).map { "\"${it.value}*\"" }.toList()
    .takeIf { it.isNotEmpty() }?.joinToString(" ")
