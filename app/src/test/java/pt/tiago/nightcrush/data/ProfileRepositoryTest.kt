package pt.tiago.nightcrush.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** In-memory double for [ProfileStore]: the repository never touches a real file in tests. */
class FakeProfileStore(
    var main: String? = null,
    var backup: String? = null,
) : ProfileStore {
    val mainWrites = ArrayList<String>()
    val backupWrites = ArrayList<String>()
    val kept = ArrayList<Pair<String, String>>()

    override fun readMain(): String? = main

    override fun readBackup(): String? = backup

    override fun writeMain(text: String) {
        mainWrites.add(text)
        main = text
    }

    override fun writeBackup(text: String) {
        backupWrites.add(text)
        backup = text
    }

    override fun setAside(text: String, name: String) {
        kept.add(name to text)
    }

    fun mainProfile(): Profile? = main?.let { Migrations.migrate(it) }

    fun backupProfile(): Profile? = backup?.let { Migrations.migrate(it) }

    fun keptTexts(): List<String> = kept.map { (_, text) -> text }
}

class ProfileRepositoryTest {
    private val now = 1_700_000_000_000L
    private val id = "profile-1"

    @Test fun emptyStoreStartsAFreshProfile() {
        val store = FakeProfileStore()
        val repository = repository(store, now)

        assertEquals(Profile.fresh(now, id), repository.profile.value)
        assertNull(repository.recoveryNote())
        assertTrue(store.kept.isEmpty())
        // Nothing is written before the first change: a read-only session touches no storage.
        assertTrue(store.mainWrites.isEmpty())
    }

    @Test fun storedProfileIsLoaded() {
        val stored = Profile.fresh(now, id).copy(pearls = 42, lives = 1)
        val store = FakeProfileStore(main = ProfileSerializer.encode(stored))
        val repository = repository(store, now)

        assertEquals(stored, repository.profile.value)
        assertNull(repository.recoveryNote())
    }

    @Test fun corruptMainRecoversFromTheBackupAndKeepsTheBadFile() {
        val good = Profile.fresh(now, id).copy(pearls = 7)
        val store = FakeProfileStore(main = "{ truncated", backup = ProfileSerializer.encode(good))
        val repository = repository(store, now)

        assertEquals(good, repository.profile.value)
        val note = repository.recoveryNote()
        assertNotNull(note)
        assertTrue(note!!, note.contains("unreadable"))
        assertEquals(listOf("{ truncated"), store.keptTexts())
        assertEquals(FileProfileStore.unreadableName(now, fromBackup = false), store.kept.single().first)
    }

    @Test fun bothFilesCorruptSetsBothAsideAndStartsFresh() {
        val store = FakeProfileStore(main = "{ truncated", backup = "not even json")
        val repository = repository(store, now)

        assertEquals(Profile.fresh(now, id), repository.profile.value)
        val note = repository.recoveryNote()
        assertNotNull(note)
        assertTrue(note!!, note.contains("new profile"))
        assertEquals(2, store.kept.size)
        assertEquals(listOf("{ truncated", "not even json"), store.keptTexts())
        assertEquals(FileProfileStore.unreadableName(now, fromBackup = false), store.kept[0].first)
        assertEquals(FileProfileStore.unreadableName(now, fromBackup = true), store.kept[1].first)
    }

    @Test fun anInvalidButWellFormedMainIsAlsoUnreadable() {
        val store = FakeProfileStore(main = ProfileSerializer.encode(Profile.fresh(now, id).copy(pearls = -5)))
        val repository = repository(store, now)

        assertEquals(Profile.fresh(now, id), repository.profile.value)
        assertNotNull(repository.recoveryNote())
        assertEquals(1, store.kept.size)
    }

    @Test fun updatePersistsBothFiles() {
        val store = FakeProfileStore(main = ProfileSerializer.encode(Profile.fresh(now, id)))
        val repository = repository(store, now)

        repository.update { current -> current.copy(pearls = current.pearls + 10) }

        assertEquals(10, repository.profile.value.pearls)
        assertEquals(10, store.mainProfile()!!.pearls)
        assertEquals(1, store.mainWrites.size)
        // The backup is the state before the change, so a bad write is always recoverable.
        assertEquals(0, store.backupProfile()!!.pearls)
        assertEquals(1, store.backupWrites.size)
    }

    @Test fun everyUpdateHitsTheDisk() {
        val store = FakeProfileStore()
        val repository = repository(store, now)

        repeat(5) { step -> repository.update { current -> current.copy(streak = step + 1) } }

        assertEquals(5, store.mainWrites.size)
        assertEquals(5, store.backupWrites.size)
        assertEquals(5, repository.profile.value.streak)
        assertEquals(5, store.mainProfile()!!.streak)
    }

    @Test fun anUpdateThatChangesNothingDoesNotWrite() {
        val store = FakeProfileStore()
        val repository = repository(store, now)

        repository.update { current -> current }

        assertTrue(store.mainWrites.isEmpty())
        assertTrue(store.backupWrites.isEmpty())
    }

    @Test fun flushRepairsAFileThatWasUnreadable() {
        val store = FakeProfileStore(main = "{ truncated")
        val repository = repository(store, now)

        repository.flush()

        assertEquals(Profile.fresh(now, id), store.mainProfile())
        assertEquals(Profile.fresh(now, id), store.backupProfile())
    }

    @Test fun clockRollbackClampsTheLastSeenTimestamp() {
        val ahead = Profile.fresh(now + 60_000, id).copy(lastSeenEpochMs = now + 60_000)
        val store = FakeProfileStore(main = ProfileSerializer.encode(ahead))

        val repository = repository(store, now)

        assertEquals(now, repository.profile.value.lastSeenEpochMs)
    }

    @Test fun anExpiredInfiniteLivesBonusIsClearedAndAFutureOneIsKept() {
        val expired = Profile.fresh(now, id).copy(infiniteLivesUntilEpochMs = now - 1)
        val alive = Profile.fresh(now, id).copy(infiniteLivesUntilEpochMs = now + 1_800_000)

        assertEquals(0L, repository(FakeProfileStore(main = ProfileSerializer.encode(expired)), now).profile.value.infiniteLivesUntilEpochMs)
        assertEquals(now + 1_800_000, repository(FakeProfileStore(main = ProfileSerializer.encode(alive)), now).profile.value.infiniteLivesUntilEpochMs)
    }

    @Test fun aGoodProfileIsNotRewrittenByTheClamps() {
        val stored = Profile.fresh(now - 1_000, id).copy(lastSeenEpochMs = now - 1_000)
        val repository = repository(FakeProfileStore(main = ProfileSerializer.encode(stored)), now)

        assertEquals(stored, repository.profile.value)
    }

    @Test fun importReplacesTheProfileAndKeepsTheOldOneAsBackup() {
        val current = Profile.fresh(now, id).copy(pearls = 3)
        val store = FakeProfileStore(main = ProfileSerializer.encode(current))
        val repository = repository(store, now)
        val imported = Profile.fresh(now, "profile-2").copy(pearls = 900, streak = 12)

        val result = repository.importPayload(ProfileSerializer.encodeForExport(imported))

        assertTrue(result.toString(), result.isSuccess)
        assertEquals(imported, repository.profile.value)
        assertEquals(900, store.mainProfile()!!.pearls)
        assertEquals(3, store.backupProfile()!!.pearls)
    }

    @Test fun importRoundTripsTheExportPayload() {
        val repository = repository(FakeProfileStore(), now)
        repository.update { current -> current.copy(pearls = 77, lives = 2) }

        val fresh = repository(FakeProfileStore(), now)
        val result = fresh.importPayload(repository.exportPayload())

        assertTrue(result.toString(), result.isSuccess)
        assertEquals(77, fresh.profile.value.pearls)
        assertEquals(2, fresh.profile.value.lives)
    }

    @Test fun importRejectsInvalidTextAndKeepsTheProfile() {
        val current = Profile.fresh(now, id).copy(pearls = 3)
        val store = FakeProfileStore(main = ProfileSerializer.encode(current))
        val repository = repository(store, now)

        assertTrue(repository.importPayload("garbage").isFailure)
        assertTrue(repository.importPayload(ProfileSerializer.encode(Profile.fresh(now, "profile-2").copy(pearls = -1))).isFailure)
        assertTrue(repository.importPayload(ProfileSerializer.encodeForExport(Profile.fresh(now, "p").copy(pearls = -1))).isFailure)

        assertEquals(current, repository.profile.value)
        assertTrue(store.mainWrites.isEmpty())
        assertTrue(store.backupWrites.isEmpty())
    }

    @Test fun aTamperedExportIsRejected() {
        val source = repository(FakeProfileStore(), now)
        source.update { current -> current.copy(pearls = 5) }
        val tampered = source.exportPayload().replace("\"pearls\": 5", "\"pearls\": 5000")

        val target = repository(FakeProfileStore(), now)

        assertTrue(target.importPayload(tampered).isFailure)
        assertEquals(0, target.profile.value.pearls)
    }

    @Test fun theStateFlowValueFollowsEveryUpdate() {
        val store = FakeProfileStore()
        val repository = repository(store, now)

        assertEquals(0, repository.profile.value.pearls)
        repository.update { current -> current.copy(pearls = 5) }
        assertEquals(5, repository.profile.value.pearls)
        repository.update { current -> current.copy(pearls = 9) }
        assertEquals(9, repository.profile.value.pearls)
        assertEquals(9, store.mainProfile()!!.pearls)
    }

    private fun repository(store: ProfileStore, startEpochMs: Long): ProfileRepository =
        ProfileRepository(store = store, clock = { startEpochMs }, newProfileId = { id })
}
