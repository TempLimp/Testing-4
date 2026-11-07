package io.github.aedev.flow.data.profile

import io.github.aedev.flow.testing.assertThat
import io.github.aedev.flow.testing.deleteRecursively
import io.github.aedev.flow.testing.newTempDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.test.AfterTest
import kotlin.test.Test

class ProfileRegistryTest {
    private val dir = newTempDir("registry")
    private val file = Path(dir, "datastore/${ProfileRegistry.FILE_NAME}")

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun <T> withRegistry(
        newId: () -> String = { error("No profile should be created") },
        block: suspend (ProfileRegistry) -> T,
    ): T =
        runBlocking {
            val job = Job()
            try {
                block(ProfileRegistry(file, CoroutineScope(Dispatchers.IO + job), newId, now = { 1_000L }))
            } finally {
                job.cancelAndJoin()
            }
        }

    @Test
    fun theFirstRunCreatesDefaultAndMakesItActive() {
        val running = withRegistry(newId = { "default-id" }) { it.running() }

        assertThat(running).isEqualTo(RunningProfile(id = "default-id", isDefault = true))
        val state = withRegistry { it.state.first() }
        assertThat(state.profiles).containsExactly(Profile(id = "default-id", createdAt = 1_000L))
        assertThat(state.defaultId).isEqualTo("default-id")
        assertThat(state.activeId).isEqualTo("default-id")
    }

    @Test
    fun aLaterStartReadsTheSameDefaultWithoutWriting() {
        withRegistry(newId = { "default-id" }) { it.running() }
        val written = read()

        val running = withRegistry { it.running() }

        assertThat(running).isEqualTo(RunningProfile(id = "default-id", isDefault = true))
        assertThat(read()).isEqualTo(written)
    }

    @Test
    fun aCorruptFileStartsOverWithANewDefault() {
        write("{ not json")

        val running = withRegistry(newId = { "fresh-id" }) { it.running() }

        assertThat(running).isEqualTo(RunningProfile(id = "fresh-id", isDefault = true))
    }

    @Test
    fun anotherActiveProfileRunsAsThatProfile() {
        write("""{"profiles":[{"id":"d"},{"id":"study"}],"defaultId":"d","activeId":"study"}""")

        assertThat(withRegistry { it.running() }).isEqualTo(RunningProfile(id = "study", isDefault = false))
    }

    @Test
    fun anActiveProfileThatIsGoneFallsBackToDefaultAndKeepsTheOthers() {
        write("""{"profiles":[{"id":"d"},{"id":"study"}],"defaultId":"d","activeId":"deleted"}""")

        val running = withRegistry { it.running() }

        assertThat(running).isEqualTo(RunningProfile(id = "d", isDefault = true))
        assertThat(withRegistry { it.state.first() }.profiles.map { it.id }).containsExactly("d", "study").inOrder()
    }

    @Test
    fun anActiveIdThatCouldLeaveItsFolderRunsAsDefault() {
        write("""{"profiles":[{"id":"d"},{"id":"../d"}],"defaultId":"d","activeId":"../d"}""")

        assertThat(ProfileRegistry.peek(file)).isNull()
        assertThat(withRegistry { it.running() }).isEqualTo(RunningProfile(id = "d", isDefault = true))
    }

    @Test
    fun afterARestoreTheAppReopensInDefaultAndKeepsEveryProfile() {
        write("""{"profiles":[{"id":"d"},{"id":"study"}],"defaultId":"d","activeId":"study"}""")

        val restored = withRegistry { it.reopenInDefault() }

        assertThat(restored.activeId).isEqualTo("d")
        assertThat(restored.profiles.map { it.id }).containsExactly("d", "study").inOrder()
        assertThat(ProfileRegistry.peek(file)).isEqualTo(RunningProfile(id = "d", isDefault = true))
    }

    @Test
    fun aRestoreThatBroughtNoRegistryStartsDefault() {
        val restored = withRegistry(newId = { "fresh" }) { it.reopenInDefault() }

        assertThat(restored.defaultId).isEqualTo("fresh")
        assertThat(restored.activeId).isEqualTo("fresh")
    }

    @Test
    fun profilesCanBeAddedUpToTheLimit() {
        var next = 0
        val added = withRegistry(newId = { "p${next++}" }) { registry -> (1..6).map { registry.add("P$it", "", ProfileAvatar()) } }

        assertThat(added.take(4).map { it?.name }).containsExactly("P1", "P2", "P3", "P4").inOrder()
        assertThat(added.drop(4)).containsExactly(null, null)
        assertThat(withRegistry { it.state.first() }.profiles).hasSize(ProfileRegistry.MAX_PROFILES)
    }

    @Test
    fun onlyDefaultMayBeRenamedToABlankName() {
        write("""{"profiles":[{"id":"d","name":"Me"},{"id":"study","name":"Study"}],"defaultId":"d","activeId":"d"}""")

        withRegistry { registry ->
            assertThat(registry.rename("study", " ")).isFalse()
            assertThat(registry.rename("study", "Books")).isTrue()
            assertThat(registry.rename("d", "")).isTrue()
            assertThat(registry.rename("missing", "X")).isFalse()
        }

        assertThat(withRegistry { it.state.first() }.profiles.map { it.name }).containsExactly("", "Books").inOrder()
    }

    @Test
    fun aProfilesColourAndAvatarChangeTogetherAndOnlyForAProfileThatExists() {
        write("""{"profiles":[{"id":"d"},{"id":"study","name":"Study"}],"defaultId":"d","activeId":"d"}""")
        val photo = ProfileAvatar(kind = ProfileAvatar.PHOTO, photoVersion = 7)

        withRegistry { registry ->
            assertThat(registry.setAppearance("study", "teal", photo)).isTrue()
            assertThat(registry.setAppearance("missing", "teal", photo)).isFalse()
        }

        val study = withRegistry { it.state.first() }.profiles.single { it.id == "study" }
        assertThat(study.colorKey).isEqualTo("teal")
        assertThat(study.avatar).isEqualTo(photo)
        assertThat(study.name).isEqualTo("Study")
    }

    @Test
    fun eachProfileKeepsTheSplashToneItLastShowed() {
        write("""{"profiles":[{"id":"d"},{"id":"study","name":"Study"}],"defaultId":"d","activeId":"d"}""")

        withRegistry { registry ->
            registry.setSplashTone("study", "LIGHT")
            registry.setSplashTone("missing", "DARK")
        }

        val profiles = withRegistry { it.state.first() }.profiles
        assertThat(profiles.map { it.splashTone }).containsExactly("", "LIGHT").inOrder()
    }

    @Test
    fun aTvAsksWhoIsWatchingUntilItIsToldNotTo() {
        write("""{"profiles":[{"id":"d"}],"defaultId":"d","activeId":"d"}""")

        assertThat(withRegistry { it.state.first() }.askWhosWatching).isTrue()
        withRegistry { it.setAskWhosWatching(false) }

        assertThat(withRegistry { it.state.first() }.askWhosWatching).isFalse()
    }

    @Test
    fun aMasterRestoreAddsTheProfilesThisDeviceLacksUnderTheirOwnIdsAndNeverPastTheLimit() {
        write("""{"profiles":[{"id":"d"},{"id":"study","name":"Study here"}],"defaultId":"d","activeId":"d"}""")

        withRegistry { registry ->
            assertThat(registry.addRestored(listOf(Profile("study", name = "Study there"), Profile("gaming", name = "Gaming")))).isTrue()
            assertThat(registry.addRestored(listOf(Profile("a1"), Profile("a2"), Profile("a3")))).isFalse()
        }

        val profiles = withRegistry { it.state.first() }.profiles
        assertThat(profiles.map { it.id }).containsExactly("d", "study", "gaming").inOrder()
        assertThat(profiles.single { it.id == "study" }.name).isEqualTo("Study here")
    }

    @Test
    fun defaultAndTheActiveProfileAreNeverRemoved() {
        write("""{"profiles":[{"id":"d"},{"id":"study"},{"id":"music"}],"defaultId":"d","activeId":"study"}""")

        val results = withRegistry { registry -> listOf("d", "study", "music", "missing").map { registry.remove(it) } }

        assertThat(results).containsExactly(false, false, true, false).inOrder()
        assertThat(withRegistry { it.state.first() }.profiles.map { it.id }).containsExactly("d", "study").inOrder()
    }

    @Test
    fun aSwitchLeavesAMarkerTheNextProcessClearsOnce() {
        write("""{"profiles":[{"id":"d"},{"id":"study"}],"defaultId":"d","activeId":"d"}""")

        val results =
            withRegistry { registry ->
                registry.setActive("study")
                listOf(registry.finishPostSwitch(), registry.finishPostSwitch())
            }

        assertThat(results).containsExactly(true, false).inOrder()
        assertThat(ProfileRegistry.peek(file)).isEqualTo(RunningProfile(id = "study", isDefault = false))
    }

    @Test
    fun theFirstProcessAfterASwitchKnowsIt() {
        write("""{"profiles":[{"id":"d"},{"id":"study"}],"defaultId":"d","activeId":"d"}""")

        withRegistry { it.setActive("study") }

        assertThat(ProfileRegistry.peek(file)).isEqualTo(RunningProfile(id = "study", isDefault = false, afterSwitch = true))
    }

    @Test
    fun aStartWithoutASwitchFindsNoMarker() {
        write("""{"profiles":[{"id":"d"}],"defaultId":"d","activeId":"d"}""")

        assertThat(withRegistry { it.finishPostSwitch() }).isFalse()
    }

    @Test
    fun aFileFromANewerVersionStillReads() {
        write(
            """{"profiles":[{"id":"d","avatar":{"kind":"hologram","frames":3},"pin":"x"}],""" +
                """"defaultId":"d","activeId":"d","cloudLinked":true}""",
        )

        val state = withRegistry { it.state.first() }

        assertThat(withRegistry { it.running() }).isEqualTo(RunningProfile(id = "d", isDefault = true))
        assertThat(
            state.profiles
                .single()
                .avatar.kind,
        ).isEqualTo("hologram")
    }

    @Test
    fun aMissingDefaultIsRecreatedAheadOfTheOtherProfiles() {
        val state = ProfileRegistryState(profiles = listOf(Profile(id = "study")), activeId = "study")

        val completed = state.completed(newId = { "d" }, now = { 5L })

        assertThat(completed.profiles.map { it.id }).containsExactly("d", "study").inOrder()
        assertThat(completed.defaultId).isEqualTo("d")
        assertThat(completed.activeId).isEqualTo("study")
    }

    @Test
    fun aStartReadsTheStoredProfileStraightFromTheFile() {
        write("""{"profiles":[{"id":"d"},{"id":"study"}],"defaultId":"d","activeId":"study"}""")

        assertThat(ProfileRegistry.peek(file)).isEqualTo(RunningProfile(id = "study", isDefault = false))
    }

    @Test
    fun aStartLeavesAMissingCorruptOrIncompleteFileToTheStore() {
        assertThat(ProfileRegistry.peek(file)).isNull()
        write("{ not json")
        assertThat(ProfileRegistry.peek(file)).isNull()
        write("""{"profiles":[{"id":"d"}],"defaultId":"d","activeId":"deleted"}""")
        assertThat(ProfileRegistry.peek(file)).isNull()
    }

    @Test
    fun whatTheStoreWritesReadsBackTheSameStraightFromTheFile() {
        val running = withRegistry(newId = { "default-id" }) { it.running() }

        assertThat(ProfileRegistry.peek(file)).isEqualTo(running)
    }

    private fun read(): String = SystemFileSystem.source(file).buffered().use { it.readString() }

    private fun write(json: String) {
        SystemFileSystem.createDirectories(file.parent!!)
        SystemFileSystem.sink(file).buffered().use { it.writeString(json) }
    }
}
