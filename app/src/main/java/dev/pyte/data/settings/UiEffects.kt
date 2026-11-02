package dev.pyte.data.settings

enum class EffectLevel(val label: String) {
    SUBTLE("Dezent"), LIVELY("Lebendig"), INTENSE("Intensiv");

    companion object {
        fun fromStored(value: String?): EffectLevel = entries.firstOrNull { it.name == value } ?: LIVELY
    }
}
