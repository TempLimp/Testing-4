package io.github.aedev.flow.data.local

// Pulled forward from upstream's BackupRepository.kt (S19) for the stores' getSettingsBackup/restoreData (S06).
// S19 ports the rest of that file beside this class.

data class SettingsBackup(
    val strings: Map<String, String> = emptyMap(),
    val booleans: Map<String, Boolean> = emptyMap(),
    val ints: Map<String, Int> = emptyMap(),
    val floats: Map<String, Float> = emptyMap(),
    val longs: Map<String, Long> = emptyMap(),
    val stringSets: Map<String, Set<String>> = emptyMap(),
)