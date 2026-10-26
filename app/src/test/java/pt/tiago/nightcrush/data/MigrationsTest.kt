package pt.tiago.nightcrush.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationsTest {
    private val now = 1_700_000_000_000L

    @Test fun theCurrentVersionIsAcceptedUnchanged() {
        val profile = Profile.fresh(now, "profile-1").copy(pearls = 55, lives = 2)
        val migrated = Migrations.migrate(ProfileSerializer.encode(profile))
        assertEquals(profile, migrated)
    }

    @Test fun versionOneDropsImpossibleRecordsAndKeepsTheRest() {
        val old = Profile.fresh(now, "profile-1").copy(
            schemaVersion = 1,
            pearls = 597,
            stats = GameStats(maxCascade = 802, bestScore = mapOf("easy" to 23_236_240, "medium" to 120_000)),
        )
        val migrated = Migrations.migrate(ProfileSerializer.encode(old))!!
        assertEquals(Profile.SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals(597, migrated.pearls)
        assertEquals(0, migrated.stats.maxCascade)
        assertEquals(mapOf("medium" to 120_000), migrated.stats.bestScore)
    }

    @Test fun garbageIsRejected() {
        assertNull(Migrations.migrate(""))
        assertNull(Migrations.migrate("not json"))
        assertNull(Migrations.migrate("{}"))
        assertNull(Migrations.migrate("[1, 2, 3]"))
        assertNull(Migrations.migrate("{\"profileId\": \"a\"}"))
    }

    @Test fun aFutureSchemaVersionNeverBecomesAFreshProfile() {
        val future = ProfileSerializer.encode(Profile.fresh(now, "profile-1").copy(schemaVersion = Profile.SCHEMA_VERSION + 1))
        assertNull(Migrations.migrate(future))
    }

    @Test fun anInvalidButWellFormedProfileIsRejected() {
        val invalid = ProfileSerializer.encode(Profile.fresh(now, "profile-1").copy(lives = 42))
        assertNull(Migrations.migrate(invalid))
    }

    @Test fun supportIsBoundedByTheEarliestKnownSchema() {
        assertTrue(Migrations.isSupported(Profile.fresh(now, "profile-1")))
        assertFalse(Migrations.isSupported(Profile.fresh(now, "profile-1").copy(schemaVersion = Migrations.EARLIEST_SUPPORTED_VERSION - 1)))
        assertFalse(Migrations.isSupported(Profile.fresh(now, "profile-1").copy(schemaVersion = Profile.SCHEMA_VERSION + 1)))
    }
}
