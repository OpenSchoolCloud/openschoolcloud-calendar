package nl.openschoolcloud.calendar.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import nl.openschoolcloud.calendar.data.local.entity.FamilyEntity
import nl.openschoolcloud.calendar.data.local.entity.ProfileEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Migration6to7Test {
    private lateinit var context: Context
    private val dbName = "migration67.db"

    @Before fun setup() { context = ApplicationProvider.getApplicationContext() }
    @After fun teardown() { context.deleteDatabase(dbName) }

    @Test
    fun migrationOpensAndNewDaosWork() = runBlocking {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()
        // Insert a family + owner profile through the new DAOs.
        db.familyDao().insert(FamilyEntity("f1", "Gezin Test", "acc1", 0L))
        db.profileDao().insert(
            ProfileEntity("p1", "f1", "Ouder", 0xFF3B9FD9L, null, true, "LOCAL")
        )
        assertEquals(1, db.profileDao().getByFamilySync("f1").size)
        db.close()
    }
}
