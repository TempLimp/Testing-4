package io.github.aedev.flow.data.profile

import io.github.aedev.flow.testing.REPO_ROOT
import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.testing.assertWithMessage
import io.github.aedev.flow.testing.requireFixture
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlin.test.Test

/**
 * Every place the app keeps data is classified in `profiles/store_inventory.tsv` as DEVICE, PROFILE, CACHE or
 * DEFAULT_ONLY, so nothing can leak between profiles through a store nobody placed. A source scan rather than a
 * runtime registry, because it also catches a plain `Path(appFilesDir(), "x")`. A new store fails here until it
 * has a row, and a row whose code is gone fails too.
 *
 * Ported to the port's sources: upstream's inventory is read as upstream ships it, beside the port's own rows for
 * stores only the port has (`store_inventory_ios.tsv`). The scan reads commonMain and iosMain (an `X.ios.kt` file
 * counts as its common `X.kt`; jvmMain is test-only and never shipped), and looks for the port's calls: Android's
 * `filesDir`, `noBackupFilesDir` and `cacheDir` are `appFilesDir()`, `appNoBackupDir()` and `appCacheDir()`,
 * `getSharedPreferences` is `sharedPreferences`, Room's builder takes the path first, and a web view is a WKWebView.
 * An upstream row whose file no session has ported yet is pending, not stale; its session's code must then match it.
 * Upstream's last case, which DAO comes from which database in Hilt's DatabaseModule, comes with S13's Koin module.
 */
class StoreScopeGuardTest {
    private data class Row(
        val kind: String,
        val name: String,
        val source: String,
        val scope: String,
        val storeClass: String,
        val reason: String,
    )

    private data class Hit(
        val kind: String,
        val source: String,
        val name: String?,
        val scope: String? = null,
    )

    private class NamedCall(
        val kind: String,
        val start: Regex,
        val parameter: String,
        val position: Int,
    )

    private val rows: List<Row> by lazy {
        (parse(requireFixture("profiles/store_inventory.tsv")) + parse(read(Path(REPO_ROOT, PORT_INVENTORY))))
    }

    private fun parse(text: String): List<Row> =
        text
            .lines()
            .drop(1)
            .filter(String::isNotBlank)
            .map { line ->
                val cells = line.split('\t')
                check(cells.size == 6) { "Expected 6 columns: $line" }
                Row(cells[0], cells[1], cells[2], cells[3], cells[4], cells[5])
            }

    private val sources: Map<String, String> by lazy {
        SOURCE_SETS
            .flatMap { set ->
                val root = Path(REPO_ROOT, "shared/src/$set/kotlin/$PACKAGE_PATH")
                walk(root).map { file ->
                    val relative = file.toString().replace('\\', '/').removePrefix(root.toString().replace('\\', '/') + "/")
                    "main:${relative.removeSuffix(".ios.kt").removeSuffix(".kt")}.kt" to code(file)
                }
            }.groupBy({ it.first }, { it.second })
            .mapValues { (_, texts) -> texts.joinToString("\n") }
    }

    /** Rows whose file the port has; the others wait for the session that ports their code. */
    private val portedRows: List<Row> by lazy { rows.filter { it.source in sources && (it.kind to it.source) !in NOT_IN_PORT } }

    private val hits: Set<Hit> by lazy {
        sources
            .flatMap { (source, text) ->
                NAMED_CALLS.flatMap { call ->
                    call.start.findAll(text).map { Hit(call.kind, source, nameOf(call, text, it.range.last)) }
                } +
                    UNNAMED_PATTERNS.filter { (_, pattern) -> pattern.containsMatchIn(text) }.map { (kind, _) -> Hit(kind, source, null) } +
                    DIRECTORY_ROOTS.filter { (_, root) -> root.containsMatchIn(text) }.map { (kind, _) -> Hit(kind, source, null) } +
                    profileFileHits(source, text)
            }.toSet()
    }

    /**
     * Calls into ProfileFiles that state a scope. A call that passes a scope through (the preferences wrapper) is
     * checked where its caller states it instead.
     */
    private fun profileFileHits(
        source: String,
        text: String,
    ): List<Hit> =
        PROFILE_FILE_CALL
            .findAll(text)
            .mapNotNull { match ->
                val arguments = arguments(text, match.range.last)
                val call = match.groupValues[1]
                val scope =
                    when (call) {
                        in UNSCOPED_CALLS -> {
                            null
                        }

                        in IMPLICIT_SCOPES -> {
                            IMPLICIT_SCOPES.getValue(call)
                        }

                        else -> {
                            arguments.firstOrNull { it.startsWith("StoreScope.") }?.removePrefix("StoreScope.")
                                ?: return@mapNotNull null
                        }
                    }
                val name = if (call == "noBackupRoot") null else arguments.lastOrNull()?.let { resolved(it, text) }
                Hit("profile_file", source, name, scope)
            }.toList()

    @Test
    fun everyRowIsWellFormed() {
        val problems =
            rows.flatMap { row ->
                listOfNotNull(
                    "unknown kind".takeIf { row.kind !in KINDS },
                    "unknown scope".takeIf { row.scope !in SCOPES },
                    "PROFILE needs a class".takeIf { row.scope == "PROFILE" && row.storeClass !in PROFILE_CLASSES },
                    "only PROFILE has a class".takeIf { row.scope != "PROFILE" && row.storeClass != "-" },
                    "no reason".takeIf { row.reason.isBlank() },
                ).map { "$it: ${row.kind} ${row.name} ${row.source}" }
            } +
                rows
                    .groupBy { Triple(it.kind, it.name, it.source) }
                    .filterValues { it.size > 1 }
                    .keys
                    .map { "duplicate: $it" }
        assertWithMessage("Malformed rows in the store inventories").that(problems).isEmpty()
    }

    @Test
    fun everyPortOnlyRowNamesAFileThePortHas() {
        val missing = parse(read(Path(REPO_ROOT, PORT_INVENTORY))).filter { it.source !in sources }.map { "${it.kind} ${it.name} ${it.source}" }
        assertWithMessage("store_inventory_ios.tsv rows whose file is missing").that(missing).isEmpty()
    }

    @Test
    fun everyStoreInTheCodeHasARow() {
        assertThat(hits).isNotEmpty()
        val unclassified = hits.filter { hit -> rows.none { row -> covers(row, hit) } }
        assertWithMessage("Add a row to store_inventory_ios.tsv (or port upstream's) for each of these, with its profile scope")
            .that(unclassified)
            .isEmpty()
    }

    @Test
    fun everyRowStillPointsAtCode() {
        val stale =
            portedRows
                .filter { it.kind in SCANNED_KINDS }
                .filter { row -> hits.none { hit -> hit.kind == row.kind && covers(row, hit) } }
        assertWithMessage("These rows no longer match any code; remove or correct them").that(stale).isEmpty()
    }

    @Test
    fun everyPreferencesStoreStatesTheScopeItsRowGives() {
        val call = NAMED_CALLS.single { it.kind == "prefs_datastore" }
        val mismatches =
            sources.flatMap { (source, text) ->
                call.start
                    .findAll(text)
                    .mapNotNull { match ->
                        val name = nameOf(call, text, match.range.last)
                        val stated =
                            arguments(text, match.range.last)
                                .firstOrNull { it.startsWith("scope ") || it.startsWith("scope=") }
                                ?.substringAfter('=')
                                ?.trim()
                        val row = rows.firstOrNull { it.kind == call.kind && it.source == source && it.name == name }
                        val expected = row?.let { "StoreScope.${storeScopeOf(it)}" }
                        "$source $name states $stated, its row says $expected".takeIf { stated != expected }
                    }.toList()
            }
        assertWithMessage("safePreferencesDataStore scopes that disagree with the inventory").that(mismatches).isEmpty()
    }

    @Test
    fun everyRoomTableHasARow() {
        val database = sources.getValue(APP_DATABASE)
        val entities =
            ENTITY_LIST
                .find(database)
                .let(::checkNotNull)
                .groupValues[1]
                .let { list -> Regex("""(\w+)::class""").findAll(list).map { it.groupValues[1] }.toList() }
        val tables = entities.map(::tableOf).toSet()
        assertThat(tables).hasSize(entities.size)
        assertWithMessage("Room tables vs room_table rows")
            .that(rows.filter { it.kind == "room_table" }.map { it.name to it.source }.toSet())
            .isEqualTo(tables)
    }

    @Test
    fun everyDaoHasARow() {
        val daos = DAO_ACCESSOR.findAll(sources.getValue(APP_DATABASE)).map { it.groupValues[1] }.toSet()
        assertThat(daos).isNotEmpty()
        assertWithMessage("AppDatabase DAOs vs room_dao rows")
            .that(rows.filter { it.kind == "room_dao" }.map { it.name }.toSet())
            .isEqualTo(daos)
    }

    private fun covers(
        row: Row,
        hit: Hit,
    ): Boolean {
        if (row.source != hit.source) return false
        if (hit.kind == "datastore_file" && row.kind in DATASTORE_KINDS) return true
        if (hit.kind in FACTORY_KINDS && row.kind == "profile_file") return true
        if (hit.scope != null && hit.scope != storeScopeOf(row)) return false
        return row.kind == hit.kind && (hit.name == null || hit.name == row.name)
    }

    private fun storeScopeOf(row: Row): String = if (row.scope == "PROFILE") "PROFILE_${row.storeClass.uppercase()}" else row.scope

    private fun tableOf(entity: String): Pair<String, String> {
        val (source, text) =
            sources.entries.single { (source, text) ->
                source.startsWith("main:data/local/entity/") && Regex("""\bclass\s+$entity\b""").containsMatchIn(text)
            }
        val declaration = Regex("""\bclass\s+$entity\b""").find(text)!!.range.first
        val annotation = text.lastIndexOf("@Entity(", declaration)
        val table =
            TABLE_NAME.find(text.substring(annotation, declaration))?.groupValues?.get(1) ?: entity
        return table to source
    }

    /** A literal or same-file `const val` argument, or null when the name is computed. */
    private fun resolved(
        argument: String,
        text: String,
    ): String? {
        val trimmed = argument.trim()
        STRING_LITERAL.matchEntire(trimmed)?.let { return it.groupValues[1] }
        if (!CONSTANT.matches(trimmed)) return null
        return Regex("""const\s+val\s+$trimmed\s*=\s*"([^"$]*)"""").find(text)?.groupValues?.get(1)
    }

    /** The store name a call passes, with a same-file `const val` resolved; null when it is computed. */
    private fun nameOf(
        call: NamedCall,
        text: String,
        openParen: Int,
    ): String? {
        val arguments = arguments(text, openParen)
        val named = arguments.firstOrNull { it.startsWith("${call.parameter} ") || it.startsWith("${call.parameter}=") }
        val argument = (named?.substringAfter('=') ?: arguments.getOrElse(call.position) { "" }).trim()
        return resolved(argument, text)
    }

    private fun arguments(
        text: String,
        openParen: Int,
    ): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var inString = false
        var index = openParen + 1
        while (index < text.length) {
            val char = text[index]
            when {
                inString -> {
                    if (char == '\\') {
                        current.append(char)
                        index++
                        current.append(text[index])
                    } else {
                        if (char == '"') inString = false
                        current.append(char)
                    }
                }

                char == '"' -> {
                    inString = true
                    current.append(char)
                }

                char in "([{" -> {
                    depth++
                    current.append(char)
                }

                char in ")]}" && depth == 0 -> {
                    break
                }

                char in ")]}" -> {
                    depth--
                    current.append(char)
                }

                char == ',' && depth == 0 -> {
                    result += current.toString().trim()
                    current.clear()
                }

                else -> {
                    current.append(char)
                }
            }
            index++
        }
        if (current.isNotBlank()) result += current.toString().trim()
        return result
    }

    private fun walk(dir: Path): List<Path> =
        SystemFileSystem.list(dir).flatMap { path ->
            if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) walk(path) else listOf(path).filter { it.name.endsWith(".kt") }
        }

    private fun read(file: Path): String = SystemFileSystem.source(file).buffered().use { it.readString() }

    private fun code(file: Path): String =
        read(file)
            .lines()
            .filterNot { line -> line.trimStart().let { it.startsWith("//") || it.startsWith("*") || it.startsWith("/*") } }
            .joinToString("\n") { it.replace(TRAILING_COMMENT, "") }

    private companion object {
        val SOURCE_SETS = listOf("commonMain", "iosMain")
        const val PACKAGE_PATH = "io/github/aedev/flow"
        const val PORT_INVENTORY = "shared/src/commonTest/resources/profiles/store_inventory_ios.tsv"
        const val APP_DATABASE = "main:data/local/AppDatabase.kt"

        /** Upstream rows for stores the port's version of the file does not keep, and why. */
        val NOT_IN_PORT =
            mapOf(
                ("cache_dir" to "main:utils/cipher/CipherWebView.kt") to
                    "player.js is evaluated in JavaScriptCore as a string; nothing is written to the cache folder",
                ("webview" to "main:utils/cipher/CipherWebView.kt") to "JavaScriptCore keeps no web data",
            ) +
                listOf("shared_prefs", "files_dir", "external_files_dir", "public_dir").associate { kind ->
                    (kind to "main:data/video/VideoDownloadManager.kt") to
                        "S10 pulled forward the read half the player needs; S18 ports the half that writes these stores"
                }

        val SCOPES = setOf("PROFILE", "DEVICE", "CACHE", "DEFAULT_ONLY")
        val PROFILE_CLASSES = setOf("settings", "library", "private", "identity")
        val DATASTORE_KINDS = setOf("prefs_datastore", "typed_datastore", "factory_datastore", "datastore_file")

        val NAMED_CALLS =
            listOf(
                NamedCall("prefs_datastore", Regex("""(?<!fun )\bsafePreferencesDataStore\("""), "name", 0),
                NamedCall("typed_datastore", Regex("""\bby\s+dataStore\("""), "fileName", 0),
                NamedCall("shared_prefs", Regex("""(?<!fun )\bsharedPreferences\("""), "name", 0),
                NamedCall("room_database", Regex("""\bdatabaseBuilder(?:<[^>]*>)?\("""), "name", 0),
            )

        val UNNAMED_PATTERNS =
            mapOf(
                "datastore_wrapper" to Regex("""\bpreferencesDataStore\("""),
                "factory_datastore" to Regex("""\b(?:DataStoreFactory|PreferenceDataStoreFactory)\.create(?:WithPath)?\b"""),
                "datastore_file" to Regex("""\b(?:dataStoreFile|preferencesDataStoreFile)\("""),
                "external_files_dir" to Regex("""\bgetExternalFilesDir\("""),
                "public_dir" to Regex("""\bgetExternalStoragePublicDirectory\("""),
                "sqlite_file" to Regex("""\bSQLiteDatabase\.open\w*\("""),
                "webview" to Regex("""\b(?:WK)?WebView\(|\bWKWebsiteDataStore\.dataStoreForIdentifier\("""),
                "glance_state" to Regex("""\bPreferencesGlanceStateDefinition\b"""),
            )

        val DIRECTORY_ROOTS =
            mapOf(
                "files_dir" to Regex("""(?<!fun )\bappFilesDir\("""),
                "no_backup_dir" to Regex("""(?<!fun )\bappNoBackupDir\("""),
                "cache_dir" to Regex("""(?<!fun )\bappCacheDir\("""),
            )

        val PROFILE_FILE_CALL = Regex("""\bProfileFiles\s*\.(preferences|dataStore|file|noBackupRoot|database|sharedPreferencesName)\(""")
        val IMPLICIT_SCOPES = mapOf("noBackupRoot" to "PROFILE_PRIVATE", "database" to "PROFILE_LIBRARY")

        /** Per-profile names whose rows give the scope themselves: an identity file and a cache. */
        val UNSCOPED_CALLS = setOf("sharedPreferencesName")
        val FACTORY_KINDS = setOf("factory_datastore", "datastore_file")

        val SCANNED_KINDS = NAMED_CALLS.map { it.kind }.toSet() + UNNAMED_PATTERNS.keys + DIRECTORY_ROOTS.keys + "profile_file"
        val KINDS = SCANNED_KINDS + setOf("room_table", "room_dao")

        val TRAILING_COMMENT = Regex("""\s//\s.*$""")
        val STRING_LITERAL = Regex(""""([^"$]*)"""")
        val CONSTANT = Regex("""[A-Z][A-Z0-9_]*""")
        val ENTITY_LIST = Regex("""entities\s*=\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
        val TABLE_NAME = Regex("""tableName\s*=\s*"([^"]+)"""")
        val DAO_ACCESSOR = Regex("""abstract\s+fun\s+\w+\(\)\s*:\s*(\w+)""")
    }
}
