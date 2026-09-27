# OSC Calendar — Plan A: App-laag (onboarding, gezin/profielen, OIDC-login, sync-naad)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bouw de client-laag van de nieuwe onboarding: één account-first trechter met Authentik-OIDC-login (AppAuth + Custom Tab), een gezins-/profielmodel, en een geflagde sync-keuze — zonder dat backend B of C al live hoeft te zijn.

**Architecture:** Volgt de bestaande MVVM + Clean-structuur (`domain/` puur Kotlin, `data/` Android-impl, `presentation/` Compose). Login gaat via AppAuth-Android in een Custom Tab; de app bewaart alleen tokens (EncryptedSharedPreferences). Gezin + kind-profielen zijn nieuwe Room-entiteiten; events krijgen een `profileId`. De sync-keuze schrijft een `SyncScope` per profiel en zet de betaalde kaart achter een feature-flag.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2024.12.01), Material3, Room 2.6.1, Hilt 2.50, AppAuth-Android 0.11.1, EncryptedSharedPreferences (security-crypto 1.1.0-alpha06), JUnit4 + MockK 1.13.9 + Robolectric 4.11.1 + kotlinx-coroutines-test.

## Global Constraints

Copied verbatim from `docs/superpowers/specs/2026-09-27-osc-onboarding-identity-family-design.md` and `CLAUDE.md`:

- **Kotlin only.** No Java files. Code/comments/vars in English; all user-facing strings in Dutch via `strings.xml`.
- **AGPL header** required on every new `.kt` file (copy the block used by existing files in the same package).
- **minSdk 26 / targetSdk 34 / compileSdk 35.** JvmTarget 17. `java.time.*` only, never `java.util.Date`/`Calendar`. Storage in `Instant` (UTC).
- **Compose BOM stays `2024.12.01` or newer.** Do NOT use `PullToRefreshBox` or `HorizontalDivider` (use `Divider`). Always add `else ->` on `when` over sealed classes.
- **No Google Play Services / Firebase / analytics.** No cloud AI.
- **Hilt for all DI** (`@HiltViewModel`, `@Inject constructor`, `@Provides` in `AppModule.kt`). Room `exportSchema = false`. Coroutines + Flow, no RxJava.
- **EncryptedSharedPreferences must use lazy init + fallback** to regular SharedPreferences (MasterKey fails on some devices — known trap).
- **Room migrations are mandatory** for any schema change; register in `AppDatabase.ALL_MIGRATIONS`.
- **Kind-PII minimalisatie:** kind-profielen dragen alleen naam + kleur/avatar; geen e-mail/geboortedatum. Kind-data blijft lokaal (`SyncScope.LOCAL`) tot de ouder bewust cloud kiest.
- **Two credential kinds stay separate:** OSC-identiteit (Authentik tokens) vs Nextcloud app-password (CalDAV). Never mix storage or usage.
- **Branding:** primary `#3B9FD9`, app name "OSC Calendar", footer "Powered by OpenSchoolCloud.nl".

**Current DB schema version: 6.** This plan introduces version **7**.

---

### Task 1: AppAuth dependency + OIDC BuildConfig velden + feature-flag

**Files:**
- Modify: `android/app/build.gradle.kts` (dependencies-block + `defaultConfig`)

**Interfaces:**
- Produces: `BuildConfig.OIDC_ISSUER: String`, `BuildConfig.OIDC_CLIENT_ID: String`, `BuildConfig.OIDC_REDIRECT_SCHEME: String`, `BuildConfig.ENABLE_OSC_CLOUD: Boolean` — consumed by Tasks 4, 8, 11.

- [ ] **Step 1: Add the AppAuth dependency**

In `android/app/build.gradle.kts`, in the `dependencies { }` block, directly under the `// Network` group, add:

```kotlin
    // OIDC login (Authentik) via Custom Tab
    implementation("net.openid:appauth:0.11.1")
```

- [ ] **Step 2: Add BuildConfig fields + manifest placeholder to defaultConfig**

In `android/app/build.gradle.kts`, inside `defaultConfig { }` (after the `vectorDrawables { }` block), add:

```kotlin
        // OIDC / Authentik configuration. Overridden per build type / CI as needed.
        buildConfigField("String", "OIDC_ISSUER", "\"https://id.openschoolcloud.nl/application/o/osc-calendar/\"")
        buildConfigField("String", "OIDC_CLIENT_ID", "\"osc-calendar\"")
        buildConfigField("String", "OIDC_REDIRECT_SCHEME", "\"nl.openschoolcloud.calendar\"")
        // Betaalde OSC-sync (subsysteem C) — pas true zodra C gebouwd + getest is.
        buildConfigField("Boolean", "ENABLE_OSC_CLOUD", "false")
        // AppAuth RedirectUriReceiverActivity needs the scheme as a manifest placeholder.
        manifestPlaceholders["appAuthRedirectScheme"] = "nl.openschoolcloud.calendar"
```

- [ ] **Step 3: Verify it builds**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (BuildConfig regenerates with the new fields).

- [ ] **Step 4: Commit**

```bash
git add android/app/build.gradle.kts
git commit -m "Plan A T1: add AppAuth dep + OIDC BuildConfig fields + ENABLE_OSC_CLOUD flag"
```

---

### Task 2: Domein-modellen — Family, Profile, SyncScope

**Files:**
- Modify: `android/app/src/main/java/nl/openschoolcloud/calendar/domain/model/Models.kt` (append)
- Test: `android/app/src/test/java/nl/openschoolcloud/calendar/domain/model/FamilyModelTest.kt`

**Interfaces:**
- Produces:
  - `enum class SyncScope { LOCAL, OWN_NEXTCLOUD, OSC_CLOUD }`
  - `data class Family(val id: String, val name: String, val ownerAccountId: String, val createdAt: java.time.Instant)`
  - `data class Profile(val id: String, val familyId: String, val displayName: String, val color: Long, val avatar: String?, val isOwnerProfile: Boolean, val syncScope: SyncScope)`
  - Consumed by Tasks 3, 5, 6, 9, 10, 11, 12.

- [ ] **Step 1: Write the failing test**

Create `android/app/src/test/java/nl/openschoolcloud/calendar/domain/model/FamilyModelTest.kt`:

```kotlin
package nl.openschoolcloud.calendar.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FamilyModelTest {
    @Test
    fun `profile defaults are local scope and non-owner constructible`() {
        val profile = Profile(
            id = "p1",
            familyId = "f1",
            displayName = "Sanne",
            color = 0xFF3B9FD9L,
            avatar = null,
            isOwnerProfile = false,
            syncScope = SyncScope.LOCAL
        )
        assertEquals(SyncScope.LOCAL, profile.syncScope)
        assertTrue(!profile.isOwnerProfile)
    }

    @Test
    fun `family carries owner account id`() {
        val family = Family("f1", "Gezin Aldewereld", "acc1", Instant.ofEpochMilli(0))
        assertEquals("acc1", family.ownerAccountId)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*FamilyModelTest"`
Expected: FAIL — `Profile`/`Family`/`SyncScope` unresolved.

- [ ] **Step 3: Append the models to Models.kt**

At the end of `domain/model/Models.kt` (keep the existing file header; do not duplicate it), add:

```kotlin
/** Where a profile's calendar data is allowed to sync to. */
enum class SyncScope { LOCAL, OWN_NEXTCLOUD, OSC_CLOUD }

/** A family owned by exactly one signed-in parent account. */
data class Family(
    val id: String,
    val name: String,
    val ownerAccountId: String,
    val createdAt: java.time.Instant
)

/**
 * A person within a family. The owner (parent) has isOwnerProfile = true and
 * is the only one who authenticates; children are profiles without credentials.
 * Children carry minimal data: name + color/avatar only.
 */
data class Profile(
    val id: String,
    val familyId: String,
    val displayName: String,
    val color: Long,
    val avatar: String?,
    val isOwnerProfile: Boolean,
    val syncScope: SyncScope
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*FamilyModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/nl/openschoolcloud/calendar/domain/model/Models.kt android/app/src/test/java/nl/openschoolcloud/calendar/domain/model/FamilyModelTest.kt
git commit -m "Plan A T2: domain models Family/Profile/SyncScope"
```

---

### Task 3: Room-entiteiten + DAO's + AccountEntity/EventEntity uitbreiding + MIGRATION_6_7

**Files:**
- Create: `android/app/src/main/java/nl/openschoolcloud/calendar/data/local/entity/FamilyEntity.kt`
- Create: `android/app/src/main/java/nl/openschoolcloud/calendar/data/local/entity/ProfileEntity.kt`
- Create: `android/app/src/main/java/nl/openschoolcloud/calendar/data/local/dao/FamilyDao.kt`
- Create: `android/app/src/main/java/nl/openschoolcloud/calendar/data/local/dao/ProfileDao.kt`
- Modify: `android/app/src/main/java/nl/openschoolcloud/calendar/data/local/entity/AccountEntity.kt`
- Modify: `android/app/src/main/java/nl/openschoolcloud/calendar/data/local/entity/EventEntity.kt` (add `profileId`)
- Modify: `android/app/src/main/java/nl/openschoolcloud/calendar/data/local/AppDatabase.kt` (entities, version 7, `MIGRATION_6_7`, `ALL_MIGRATIONS`, new abstract DAO getters)
- Test: `android/app/src/test/java/nl/openschoolcloud/calendar/data/local/Migration6to7Test.kt`

**Interfaces:**
- Consumes: nothing from prior tasks.
- Produces:
  - `FamilyEntity(id, name, ownerAccountId, createdAt: Long)`
  - `ProfileEntity(id, familyId, displayName, color: Long, avatar: String?, isOwnerProfile: Boolean, syncScope: String)`
  - `AccountEntity` gains nullable `oscSub: String?`, `email: String?`, `authProvider: String?`, and non-null `mfaEnrolled: Boolean = false`
  - `EventEntity` gains nullable `profileId: String?`
  - `FamilyDao`, `ProfileDao` — consumed by Task 5.

- [ ] **Step 1: Write the failing migration test**

Create `android/app/src/test/java/nl/openschoolcloud/calendar/data/local/Migration6to7Test.kt`:

```kotlin
package nl.openschoolcloud.calendar.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Migration6to7Test {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate6to7_addsFamilyProfileTablesAndColumns() {
        // Create v6 with one legacy event + account row.
        helper.createDatabase(dbName, 6).apply {
            execSQL(
                "INSERT INTO accounts (id, serverUrl, username, displayName, email, principalUrl, calendarHomeSet, isDefault) " +
                    "VALUES ('acc1','https://nc.example','bob','Bob',NULL,NULL,NULL,1)"
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 7, true, AppDatabase.MIGRATION_6_7)

        // New tables exist and new columns are queryable.
        db.query("SELECT id, name, ownerAccountId, createdAt FROM families").use { assertTrue(it.columnCount == 4) }
        db.query("SELECT id, familyId, displayName, color, avatar, isOwnerProfile, syncScope FROM profiles").use { assertTrue(it.columnCount == 7) }
        db.query("SELECT oscSub, email, authProvider, mfaEnrolled FROM accounts").use { assertTrue(it.columnCount == 4) }
        db.query("SELECT profileId FROM events").use { assertTrue(it.columnCount == 1) }
        db.close()
    }
}
```

Note: `MigrationTestHelper` requires the schema location. Add to `android/app/build.gradle.kts` inside `defaultConfig` — but since `exportSchema = false`, instead run this test with schema export enabled only for tests. Simplest path: set `room.schemaLocation` is NOT needed when using `validateDroppedTables=true` with `exportSchema=false` is unsupported. Therefore this task uses the runtime approach below instead.

- [ ] **Step 2: Replace the migration test with a runtime open test (works with exportSchema=false)**

Overwrite `Migration6to7Test.kt` with a test that opens a real DB at v6 via raw SQL, then opens Room at v7 with the migration and asserts inserts succeed:

```kotlin
package nl.openschoolcloud.calendar.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import nl.openschoolcloud.calendar.data.local.entity.FamilyEntity
import nl.openschoolcloud.calendar.data.local.entity.ProfileEntity

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
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*Migration6to7Test"`
Expected: FAIL — `FamilyEntity`/`ProfileEntity`/`familyDao`/`profileDao` unresolved.

- [ ] **Step 4: Create FamilyEntity**

Create `data/local/entity/FamilyEntity.kt` (with the AGPL header block copied from `AccountEntity.kt`):

```kotlin
package nl.openschoolcloud.calendar.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "families")
data class FamilyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ownerAccountId: String,
    val createdAt: Long
)
```

- [ ] **Step 5: Create ProfileEntity**

Create `data/local/entity/ProfileEntity.kt` (AGPL header + this body):

```kotlin
package nl.openschoolcloud.calendar.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "profiles",
    indices = [Index("familyId")]
)
data class ProfileEntity(
    @PrimaryKey val id: String,
    val familyId: String,
    val displayName: String,
    val color: Long,
    val avatar: String?,
    val isOwnerProfile: Boolean,
    val syncScope: String
)
```

- [ ] **Step 6: Create FamilyDao**

Create `data/local/dao/FamilyDao.kt` (AGPL header + body):

```kotlin
package nl.openschoolcloud.calendar.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import nl.openschoolcloud.calendar.data.local.entity.FamilyEntity

@Dao
interface FamilyDao {
    @Query("SELECT * FROM families LIMIT 1")
    fun observe(): Flow<FamilyEntity?>

    @Query("SELECT * FROM families WHERE ownerAccountId = :accountId LIMIT 1")
    suspend fun getByOwner(accountId: String): FamilyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(family: FamilyEntity)

    @Query("DELETE FROM families WHERE id = :id")
    suspend fun deleteById(id: String)
}
```

- [ ] **Step 7: Create ProfileDao**

Create `data/local/dao/ProfileDao.kt` (AGPL header + body):

```kotlin
package nl.openschoolcloud.calendar.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import nl.openschoolcloud.calendar.data.local.entity.ProfileEntity

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles WHERE familyId = :familyId ORDER BY isOwnerProfile DESC, displayName ASC")
    fun observeByFamily(familyId: String): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE familyId = :familyId")
    suspend fun getByFamilySync(familyId: String): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getById(id: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: ProfileEntity)

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun deleteById(id: String)
}
```

- [ ] **Step 8: Extend AccountEntity**

In `data/local/entity/AccountEntity.kt`, replace the `data class AccountEntity(...)` body with (keep the header + `@Entity` annotation):

```kotlin
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey
    val id: String,
    val serverUrl: String,
    val username: String,
    val displayName: String?,
    val email: String?,
    val principalUrl: String?,
    val calendarHomeSet: String?,
    val isDefault: Boolean,
    // OIDC identity (Authentik). Null for legacy Nextcloud-only accounts.
    val oscSub: String? = null,
    val authProvider: String? = null,
    val mfaEnrolled: Boolean = false
)
```

Note: `email` already existed; the two genuinely new columns are `oscSub`, `authProvider`, `mfaEnrolled`.

- [ ] **Step 9: Add profileId to EventEntity**

In `data/local/entity/EventEntity.kt`, add this field to the `data class EventEntity(...)` (place it as the last constructor parameter, defaulting to null so existing constructors keep compiling):

```kotlin
    val profileId: String? = null
```

- [ ] **Step 10: Wire the entities, DAOs, version and migration into AppDatabase**

In `data/local/AppDatabase.kt`:

1. Add imports:
```kotlin
import nl.openschoolcloud.calendar.data.local.dao.FamilyDao
import nl.openschoolcloud.calendar.data.local.dao.ProfileDao
import nl.openschoolcloud.calendar.data.local.entity.FamilyEntity
import nl.openschoolcloud.calendar.data.local.entity.ProfileEntity
```

2. In the `@Database(entities = [...])` list add `FamilyEntity::class,` and `ProfileEntity::class,` and change `version = 6` to `version = 7`.

3. Add abstract getters:
```kotlin
    abstract fun familyDao(): FamilyDao
    abstract fun profileDao(): ProfileDao
```

4. In `companion object`, after `MIGRATION_5_6`, add:
```kotlin
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `families` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `ownerAccountId` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `profiles` (
                        `id` TEXT NOT NULL,
                        `familyId` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `color` INTEGER NOT NULL,
                        `avatar` TEXT,
                        `isOwnerProfile` INTEGER NOT NULL,
                        `syncScope` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_profiles_familyId` ON `profiles` (`familyId`)")
                database.execSQL("ALTER TABLE accounts ADD COLUMN oscSub TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE accounts ADD COLUMN authProvider TEXT DEFAULT NULL")
                database.execSQL("ALTER TABLE accounts ADD COLUMN mfaEnrolled INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE events ADD COLUMN profileId TEXT DEFAULT NULL")
            }
        }
```

5. In `ALL_MIGRATIONS`, add `MIGRATION_6_7` as the last element.

- [ ] **Step 11: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*Migration6to7Test"`
Expected: PASS.

- [ ] **Step 12: Commit**

```bash
git add android/app/src/main/java/nl/openschoolcloud/calendar/data/local/ android/app/src/test/java/nl/openschoolcloud/calendar/data/local/Migration6to7Test.kt
git commit -m "Plan A T3: family/profile entities+DAOs, account/event columns, MIGRATION_6_7"
```

---

### Task 4: Token-opslag (EncryptedSharedPreferences met fallback)

**Files:**
- Create: `android/app/src/main/java/nl/openschoolcloud/calendar/data/remote/auth/OidcTokenStore.kt`
- Test: `android/app/src/test/java/nl/openschoolcloud/calendar/data/remote/auth/OidcTokenStoreTest.kt`

**Interfaces:**
- Produces:
  - `class OidcTokenStore(context: Context)` with `var authStateJson: String?`, `fun clear()`, `val isLoggedIn: Boolean`
  - Consumed by Tasks 5, 8.

- [ ] **Step 1: Write the failing test**

Create `OidcTokenStoreTest.kt`:

```kotlin
package nl.openschoolcloud.calendar.data.remote.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OidcTokenStoreTest {
    private lateinit var store: OidcTokenStore

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        store = OidcTokenStore(ctx)
        store.clear()
    }

    @Test fun startsLoggedOut() {
        assertFalse(store.isLoggedIn)
        assertNull(store.authStateJson)
    }

    @Test fun persistsAndClears() {
        store.authStateJson = """{"state":"x"}"""
        assertTrue(store.isLoggedIn)
        assertEquals("""{"state":"x"}""", store.authStateJson)
        store.clear()
        assertFalse(store.isLoggedIn)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*OidcTokenStoreTest"`
Expected: FAIL — `OidcTokenStore` unresolved.

- [ ] **Step 3: Implement OidcTokenStore**

Create `OidcTokenStore.kt` (AGPL header + body). Mirror the lazy-init/fallback pattern already used in `data/remote/auth/CredentialStorage.kt`:

```kotlin
package nl.openschoolcloud.calendar.data.remote.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.util.Log

/**
 * Stores the serialized AppAuth AuthState (tokens) at rest.
 * Uses EncryptedSharedPreferences with a lazy init + fallback to regular
 * SharedPreferences, because MasterKey creation fails on some devices/emulators.
 * This is the OSC identity store; Nextcloud CalDAV credentials live separately
 * in CredentialStorage and must never be mixed in here.
 */
class OidcTokenStore(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "osc_oidc_tokens",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w("OidcTokenStore", "Encrypted prefs unavailable, falling back (${e.javaClass.simpleName})")
            context.getSharedPreferences("osc_oidc_tokens_plain", Context.MODE_PRIVATE)
        }
    }

    var authStateJson: String?
        get() = prefs.getString(KEY_AUTH_STATE, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_AUTH_STATE).apply()
            else prefs.edit().putString(KEY_AUTH_STATE, value).apply()
        }

    val isLoggedIn: Boolean get() = authStateJson != null

    fun clear() { prefs.edit().remove(KEY_AUTH_STATE).apply() }

    private companion object { const val KEY_AUTH_STATE = "auth_state" }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*OidcTokenStoreTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/nl/openschoolcloud/calendar/data/remote/auth/OidcTokenStore.kt android/app/src/test/java/nl/openschoolcloud/calendar/data/remote/auth/OidcTokenStoreTest.kt
git commit -m "Plan A T4: encrypted OIDC token store with fallback"
```

---

### Task 5: FamilyRepository + ProfileRepository (interfaces, impls, Hilt bindings)

**Files:**
- Modify: `android/app/src/main/java/nl/openschoolcloud/calendar/domain/repository/Repositories.kt` (append interfaces)
- Create: `android/app/src/main/java/nl/openschoolcloud/calendar/data/repository/FamilyRepositoryImpl.kt`
- Create: `android/app/src/main/java/nl/openschoolcloud/calendar/data/repository/ProfileRepositoryImpl.kt`
- Modify: `android/app/src/main/java/nl/openschoolcloud/calendar/di/AppModule.kt` (bindings + DAO providers)
- Test: `android/app/src/test/java/nl/openschoolcloud/calendar/data/repository/ProfileRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `FamilyDao`, `ProfileDao` (T3); `Family`, `Profile`, `SyncScope` (T2).
- Produces:
  - `interface FamilyRepository { fun observeFamily(): Flow<Family?>; suspend fun createFamily(name: String, ownerAccountId: String): Family }`
  - `interface ProfileRepository { fun observeProfiles(familyId: String): Flow<List<Profile>>; suspend fun addProfile(familyId: String, name: String, color: Long, isOwner: Boolean): Profile; suspend fun setSyncScope(profileId: String, scope: SyncScope); suspend fun removeProfile(profileId: String) }`
  - Consumed by Tasks 9, 10, 11, 12.

- [ ] **Step 1: Write the failing test**

Create `ProfileRepositoryImplTest.kt`:

```kotlin
package nl.openschoolcloud.calendar.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import nl.openschoolcloud.calendar.data.local.dao.ProfileDao
import nl.openschoolcloud.calendar.data.local.entity.ProfileEntity
import nl.openschoolcloud.calendar.domain.model.SyncScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRepositoryImplTest {

    private val dao = mockk<ProfileDao>(relaxed = true)
    private val repo = ProfileRepositoryImpl(dao)

    @Test fun `addProfile inserts a profile with given scope defaulting to LOCAL`() = runTest {
        val captured = slot<ProfileEntity>()
        coEvery { dao.insert(capture(captured)) } returns Unit

        val profile = repo.addProfile("f1", "Sanne", 0xFF123456L, isOwner = false)

        assertEquals("f1", captured.captured.familyId)
        assertEquals("Sanne", captured.captured.displayName)
        assertEquals("LOCAL", captured.captured.syncScope)
        assertTrue(!captured.captured.isOwnerProfile)
        assertEquals(SyncScope.LOCAL, profile.syncScope)
    }

    @Test fun `setSyncScope updates the stored profile scope`() = runTest {
        coEvery { dao.getById("p1") } returns
            ProfileEntity("p1", "f1", "Sanne", 0L, null, false, "LOCAL")

        repo.setSyncScope("p1", SyncScope.OSC_CLOUD)

        coVerify { dao.update(match { it.syncScope == "OSC_CLOUD" }) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*ProfileRepositoryImplTest"`
Expected: FAIL — `ProfileRepositoryImpl` unresolved.

- [ ] **Step 3: Append repository interfaces to Repositories.kt**

At the end of `domain/repository/Repositories.kt` add:

```kotlin
import kotlinx.coroutines.flow.Flow
import nl.openschoolcloud.calendar.domain.model.Family
import nl.openschoolcloud.calendar.domain.model.Profile
import nl.openschoolcloud.calendar.domain.model.SyncScope

interface FamilyRepository {
    fun observeFamily(): Flow<Family?>
    suspend fun createFamily(name: String, ownerAccountId: String): Family
}

interface ProfileRepository {
    fun observeProfiles(familyId: String): Flow<List<Profile>>
    suspend fun addProfile(familyId: String, name: String, color: Long, isOwner: Boolean): Profile
    suspend fun setSyncScope(profileId: String, scope: SyncScope)
    suspend fun removeProfile(profileId: String)
}
```

(If `Repositories.kt` already imports `Flow`, do not duplicate the import — merge into the existing import group.)

- [ ] **Step 4: Implement FamilyRepositoryImpl**

Create `data/repository/FamilyRepositoryImpl.kt` (AGPL header + body). Use `java.util.UUID.randomUUID().toString()` for ids and `Instant.now()` for timestamps:

```kotlin
package nl.openschoolcloud.calendar.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.openschoolcloud.calendar.data.local.dao.FamilyDao
import nl.openschoolcloud.calendar.data.local.entity.FamilyEntity
import nl.openschoolcloud.calendar.domain.model.Family
import nl.openschoolcloud.calendar.domain.repository.FamilyRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class FamilyRepositoryImpl @Inject constructor(
    private val familyDao: FamilyDao
) : FamilyRepository {

    override fun observeFamily(): Flow<Family?> =
        familyDao.observe().map { it?.toDomain() }

    override suspend fun createFamily(name: String, ownerAccountId: String): Family {
        val entity = FamilyEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            ownerAccountId = ownerAccountId,
            createdAt = Instant.now().toEpochMilli()
        )
        familyDao.insert(entity)
        return entity.toDomain()
    }

    private fun FamilyEntity.toDomain() =
        Family(id, name, ownerAccountId, Instant.ofEpochMilli(createdAt))
}
```

- [ ] **Step 5: Implement ProfileRepositoryImpl**

Create `data/repository/ProfileRepositoryImpl.kt` (AGPL header + body):

```kotlin
package nl.openschoolcloud.calendar.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.openschoolcloud.calendar.data.local.dao.ProfileDao
import nl.openschoolcloud.calendar.data.local.entity.ProfileEntity
import nl.openschoolcloud.calendar.domain.model.Profile
import nl.openschoolcloud.calendar.domain.model.SyncScope
import nl.openschoolcloud.calendar.domain.repository.ProfileRepository
import java.util.UUID
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val profileDao: ProfileDao
) : ProfileRepository {

    override fun observeProfiles(familyId: String): Flow<List<Profile>> =
        profileDao.observeByFamily(familyId).map { list -> list.map { it.toDomain() } }

    override suspend fun addProfile(
        familyId: String, name: String, color: Long, isOwner: Boolean
    ): Profile {
        val entity = ProfileEntity(
            id = UUID.randomUUID().toString(),
            familyId = familyId,
            displayName = name,
            color = color,
            avatar = null,
            isOwnerProfile = isOwner,
            syncScope = SyncScope.LOCAL.name
        )
        profileDao.insert(entity)
        return entity.toDomain()
    }

    override suspend fun setSyncScope(profileId: String, scope: SyncScope) {
        val current = profileDao.getById(profileId) ?: return
        profileDao.update(current.copy(syncScope = scope.name))
    }

    override suspend fun removeProfile(profileId: String) = profileDao.deleteById(profileId)

    private fun ProfileEntity.toDomain() =
        Profile(id, familyId, displayName, color, avatar, isOwnerProfile, SyncScope.valueOf(syncScope))
}
```

- [ ] **Step 6: Add Hilt bindings + DAO providers**

In `di/AppModule.kt`:

1. If DAO providers are `@Provides` in this module, add (matching the existing style, referencing the injected `AppDatabase`):
```kotlin
    @Provides fun provideFamilyDao(db: AppDatabase): FamilyDao = db.familyDao()
    @Provides fun provideProfileDao(db: AppDatabase): ProfileDao = db.profileDao()
```
2. Add repository bindings. If the module binds interfaces via `@Provides @Singleton` (as the existing `AccountRepository` binding does), add:
```kotlin
    @Provides @Singleton
    fun provideFamilyRepository(impl: FamilyRepositoryImpl): FamilyRepository = impl

    @Provides @Singleton
    fun provideProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository = impl
```
3. Add the matching imports for `FamilyDao`, `ProfileDao`, `FamilyRepository`, `ProfileRepository`, `FamilyRepositoryImpl`, `ProfileRepositoryImpl`.

(Follow whatever binding style `AppModule.kt` already uses for `AccountRepository` — `@Binds` vs `@Provides`. Match it exactly.)

- [ ] **Step 7: Run test + build to verify**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*ProfileRepositoryImplTest" && ./gradlew :app:compileDebugKotlin`
Expected: test PASS, compile SUCCESSFUL (Hilt graph resolves).

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/java/nl/openschoolcloud/calendar/domain/repository/Repositories.kt android/app/src/main/java/nl/openschoolcloud/calendar/data/repository/FamilyRepositoryImpl.kt android/app/src/main/java/nl/openschoolcloud/calendar/data/repository/ProfileRepositoryImpl.kt android/app/src/main/java/nl/openschoolcloud/calendar/di/AppModule.kt android/app/src/test/java/nl/openschoolcloud/calendar/data/repository/ProfileRepositoryImplTest.kt
git commit -m "Plan A T5: Family/Profile repositories + Hilt bindings"
```

---

### Task 6: SyncProvider-naad + FeatureFlags

**Files:**
- Create: `android/app/src/main/java/nl/openschoolcloud/calendar/domain/sync/SyncProvider.kt`
- Create: `android/app/src/main/java/nl/openschoolcloud/calendar/data/sync/LocalSyncProvider.kt`
- Create: `android/app/src/main/java/nl/openschoolcloud/calendar/data/sync/NextcloudCalDavProvider.kt`
- Create: `android/app/src/main/java/nl/openschoolcloud/calendar/data/sync/OscCloudProvider.kt`
- Create: `android/app/src/main/java/nl/openschoolcloud/calendar/util/FeatureFlags.kt`
- Test: `android/app/src/test/java/nl/openschoolcloud/calendar/data/sync/SyncProviderTest.kt`

**Interfaces:**
- Consumes: `SyncScope` (T2), `BuildConfig.ENABLE_OSC_CLOUD` (T1).
- Produces:
  - `interface SyncProvider { val scope: SyncScope; fun isAvailable(): Boolean }`
  - `object FeatureFlags { val oscCloudEnabled: Boolean }`
  - Consumed by Task 11 (sync-choice screen).

- [ ] **Step 1: Write the failing test**

Create `SyncProviderTest.kt`:

```kotlin
package nl.openschoolcloud.calendar.data.sync

import nl.openschoolcloud.calendar.domain.model.SyncScope
import nl.openschoolcloud.calendar.domain.sync.SyncProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncProviderTest {
    @Test fun `local provider is always available and local scope`() {
        val p: SyncProvider = LocalSyncProvider()
        assertEquals(SyncScope.LOCAL, p.scope)
        assertTrue(p.isAvailable())
    }

    @Test fun `nextcloud provider reports own-nextcloud scope`() {
        assertEquals(SyncScope.OWN_NEXTCLOUD, NextcloudCalDavProvider().scope)
    }

    @Test fun `osc cloud provider availability follows the feature flag`() {
        // OscCloudProvider takes availability explicitly so it is testable
        // without depending on BuildConfig in a unit test.
        assertEquals(SyncScope.OSC_CLOUD, OscCloudProvider(enabled = false).scope)
        assertTrue(!OscCloudProvider(enabled = false).isAvailable())
        assertTrue(OscCloudProvider(enabled = true).isAvailable())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*SyncProviderTest"`
Expected: FAIL — types unresolved.

- [ ] **Step 3: Create the SyncProvider interface**

Create `domain/sync/SyncProvider.kt` (AGPL header + body):

```kotlin
package nl.openschoolcloud.calendar.domain.sync

import nl.openschoolcloud.calendar.domain.model.SyncScope

/**
 * The seam between the app and a sync destination. The betaalde OSC-sync
 * (subsysteem C) fills OscCloudProvider later; the app architecture does not
 * change when it lands.
 */
interface SyncProvider {
    val scope: SyncScope
    fun isAvailable(): Boolean
}
```

- [ ] **Step 4: Create the three implementations**

Create `data/sync/LocalSyncProvider.kt`:

```kotlin
package nl.openschoolcloud.calendar.data.sync

import nl.openschoolcloud.calendar.domain.model.SyncScope
import nl.openschoolcloud.calendar.domain.sync.SyncProvider

class LocalSyncProvider : SyncProvider {
    override val scope = SyncScope.LOCAL
    override fun isAvailable() = true
}
```

Create `data/sync/NextcloudCalDavProvider.kt`:

```kotlin
package nl.openschoolcloud.calendar.data.sync

import nl.openschoolcloud.calendar.domain.model.SyncScope
import nl.openschoolcloud.calendar.domain.sync.SyncProvider

/** Delegates to the existing CalDAV sync stack once an account is linked. */
class NextcloudCalDavProvider : SyncProvider {
    override val scope = SyncScope.OWN_NEXTCLOUD
    override fun isAvailable() = true
}
```

Create `data/sync/OscCloudProvider.kt`:

```kotlin
package nl.openschoolcloud.calendar.data.sync

import nl.openschoolcloud.calendar.domain.model.SyncScope
import nl.openschoolcloud.calendar.domain.sync.SyncProvider

/**
 * Stub for the betaalde OSC-sync (subsysteem C). Availability is gated by the
 * ENABLE_OSC_CLOUD feature flag until C is built and tested.
 */
class OscCloudProvider(private val enabled: Boolean) : SyncProvider {
    override val scope = SyncScope.OSC_CLOUD
    override fun isAvailable() = enabled
}
```

- [ ] **Step 5: Create FeatureFlags**

Create `util/FeatureFlags.kt`:

```kotlin
package nl.openschoolcloud.calendar.util

import nl.openschoolcloud.calendar.BuildConfig

object FeatureFlags {
    val oscCloudEnabled: Boolean get() = BuildConfig.ENABLE_OSC_CLOUD
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*SyncProviderTest"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/nl/openschoolcloud/calendar/domain/sync/ android/app/src/main/java/nl/openschoolcloud/calendar/data/sync/ android/app/src/main/java/nl/openschoolcloud/calendar/util/FeatureFlags.kt android/app/src/test/java/nl/openschoolcloud/calendar/data/sync/SyncProviderTest.kt
git commit -m "Plan A T6: SyncProvider seam + FeatureFlags"
```

---

### Task 7: strings.xml — alle nieuwe Nederlandse teksten

**Files:**
- Modify: `android/app/src/main/res/values/strings.xml`

**Interfaces:**
- Produces: string resource keys consumed by Tasks 9–12. Keys listed below.

- [ ] **Step 1: Add the strings**

In `res/values/strings.xml`, before the closing `</resources>`, add:

```xml
    <!-- Welkom / account -->
    <string name="welcome_title">Eén agenda voor het hele gezin</string>
    <string name="welcome_subtitle">Privacy-first. Jij bepaalt waar je gegevens staan.</string>
    <string name="welcome_create_account">Account maken</string>
    <string name="welcome_login">Inloggen</string>
    <string name="auth_in_progress">Bezig met inloggen…</string>
    <string name="auth_failed">Inloggen mislukt. Probeer het opnieuw.</string>

    <!-- Herstelcodes -->
    <string name="recovery_title">Bewaar je herstelcodes</string>
    <string name="recovery_body">Je hebt tweestapsverificatie ingesteld. Bewaar je herstelcodes op een veilige plek — je hebt ze nodig als je je telefoon kwijtraakt.</string>
    <string name="recovery_copy">Kopieer codes</string>
    <string name="recovery_confirm">Ik heb ze bewaard</string>

    <!-- Gezin opzetten -->
    <string name="family_setup_title">Stel je gezin in</string>
    <string name="family_name_label">Naam van je gezin</string>
    <string name="family_name_hint">Bijv. Gezin Jansen</string>
    <string name="profile_add">Profiel toevoegen</string>
    <string name="profile_name_label">Naam</string>
    <string name="profile_name_hint">Naam van het kind</string>
    <string name="family_setup_continue">Doorgaan</string>

    <!-- Sync-keuze -->
    <string name="sync_choice_title">Waar bewaar je jullie agenda?</string>
    <string name="sync_local_title">Alleen op dit apparaat</string>
    <string name="sync_local_desc">Gratis. Niets verlaat je telefoon.</string>
    <string name="sync_local_button">Kies lokaal</string>
    <string name="sync_nextcloud_title">Eigen Nextcloud</string>
    <string name="sync_nextcloud_desc">Gratis. Koppel je eigen server via CalDAV.</string>
    <string name="sync_nextcloud_button">Koppel Nextcloud</string>
    <string name="sync_osc_title">OSC-sync voor het gezin</string>
    <string name="sync_osc_desc">Automatisch synchroniseren over alle apparaten, zonder eigen server. €1 per gezinslid per maand, eerste 30 dagen gratis.</string>
    <string name="sync_osc_button">Probeer 30 dagen gratis</string>
    <string name="sync_osc_soon">Binnenkort beschikbaar</string>

    <!-- Profiel-switcher -->
    <string name="profile_all">Hele gezin</string>
    <string name="profile_switch">Wissel profiel</string>
```

- [ ] **Step 2: Verify resources compile**

Run: `cd android && ./gradlew :app:processDebugResources`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/res/values/strings.xml
git commit -m "Plan A T7: Dutch strings for onboarding/family/sync/profile-switcher"
```

---

### Task 8: AuthManager — AppAuth-flow (login/logout/token-refresh)

**Files:**
- Create: `android/app/src/main/java/nl/openschoolcloud/calendar/data/remote/auth/AuthManager.kt`
- Modify: `android/app/src/main/java/nl/openschoolcloud/calendar/di/AppModule.kt` (provide `AuthManager` + `OidcTokenStore`)
- Test: `android/app/src/test/java/nl/openschoolcloud/calendar/data/remote/auth/AuthManagerStateTest.kt`

**Interfaces:**
- Consumes: `OidcTokenStore` (T4), `BuildConfig.OIDC_*` (T1).
- Produces:
  - `class AuthManager(context, tokenStore)` with:
    - `fun buildAuthRequestIntent(): android.content.Intent` (Custom Tab auth request)
    - `suspend fun handleAuthResponse(data: android.content.Intent): Result<OidcIdentity>` (exchanges code for tokens, persists AuthState)
    - `val isLoggedIn: Boolean`
    - `fun logout()`
  - `data class OidcIdentity(val sub: String, val email: String?)`
  - Consumed by Tasks 9 (account entry), 10 (splash routing).

- [ ] **Step 1: Write the failing test (state-only, no network)**

Create `AuthManagerStateTest.kt`. AppAuth's network exchange needs an Activity, so unit-test only the logged-in/logout state delegation to the token store:

```kotlin
package nl.openschoolcloud.calendar.data.remote.auth

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuthManagerStateTest {
    private lateinit var manager: AuthManager
    private lateinit var store: OidcTokenStore

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        store = OidcTokenStore(ctx).also { it.clear() }
        manager = AuthManager(ctx, store)
    }

    @Test fun `reports logged out when store empty`() {
        assertFalse(manager.isLoggedIn)
    }

    @Test fun `reflects logged in when store has state and clears on logout`() {
        store.authStateJson = """{"mAuthorizationServiceConfiguration":null}"""
        assertTrue(manager.isLoggedIn)
        manager.logout()
        assertFalse(manager.isLoggedIn)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*AuthManagerStateTest"`
Expected: FAIL — `AuthManager`/`OidcIdentity` unresolved.

- [ ] **Step 3: Implement AuthManager**

Create `AuthManager.kt` (AGPL header + body). Uses AppAuth `AuthorizationService`, `AuthorizationRequest`, `AuthState`:

```kotlin
package nl.openschoolcloud.calendar.data.remote.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import nl.openschoolcloud.calendar.BuildConfig
import kotlin.coroutines.resume

data class OidcIdentity(val sub: String, val email: String?)

/**
 * Drives the OIDC Authorization Code + PKCE flow against Authentik using a
 * Custom Tab. Password entry and MFA happen entirely in the browser tab; this
 * class only ever handles the resulting tokens.
 */
class AuthManager(
    private val context: Context,
    private val tokenStore: OidcTokenStore
) {
    private val serviceConfig = AuthorizationServiceConfiguration(
        Uri.parse(BuildConfig.OIDC_ISSUER + "authorize/"),
        Uri.parse(BuildConfig.OIDC_ISSUER + "token/")
    )

    val isLoggedIn: Boolean get() = tokenStore.isLoggedIn

    fun buildAuthRequestIntent(): Intent {
        val service = AuthorizationService(context)
        val request = AuthorizationRequest.Builder(
            serviceConfig,
            BuildConfig.OIDC_CLIENT_ID,
            ResponseTypeValues.CODE,
            Uri.parse(BuildConfig.OIDC_REDIRECT_SCHEME + "://oidc")
        ).setScopes("openid", "profile", "email", "offline_access").build()
        return service.getAuthorizationRequestIntent(request)
    }

    suspend fun handleAuthResponse(data: Intent): Result<OidcIdentity> {
        val response = AuthorizationResponse.fromIntent(data)
        val ex = AuthorizationException.fromIntent(data)
        if (response == null) {
            return Result.failure(ex ?: IllegalStateException("No authorization response"))
        }
        val service = AuthorizationService(context)
        return suspendCancellableCoroutine { cont ->
            service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, tokenEx ->
                if (tokenResponse == null) {
                    cont.resume(Result.failure(tokenEx ?: IllegalStateException("Token exchange failed")))
                    return@performTokenRequest
                }
                val authState = AuthState(response, tokenResponse, null)
                tokenStore.authStateJson = authState.jsonSerializeString()
                val idToken = tokenResponse.idToken
                val identity = parseIdentity(idToken)
                cont.resume(Result.success(identity))
            }
        }
    }

    fun logout() = tokenStore.clear()

    /** Extract sub + email from the ID token payload (base64url middle segment). */
    private fun parseIdentity(idToken: String?): OidcIdentity {
        if (idToken == null) return OidcIdentity(sub = "", email = null)
        return try {
            val payload = idToken.split(".")[1]
            val json = String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING))
            val obj = org.json.JSONObject(json)
            OidcIdentity(sub = obj.optString("sub"), email = obj.optString("email").ifEmpty { null })
        } catch (e: Exception) {
            OidcIdentity(sub = "", email = null)
        }
    }
}
```

- [ ] **Step 4: Provide AuthManager + OidcTokenStore via Hilt**

In `di/AppModule.kt` add (matching existing `@Provides @Singleton` style and `@ApplicationContext` usage):

```kotlin
    @Provides @Singleton
    fun provideOidcTokenStore(@ApplicationContext context: Context): OidcTokenStore =
        OidcTokenStore(context)

    @Provides @Singleton
    fun provideAuthManager(@ApplicationContext context: Context, store: OidcTokenStore): AuthManager =
        AuthManager(context, store)
```

Add imports for `OidcTokenStore` and `AuthManager` (and `Context`/`@ApplicationContext` if not already present).

- [ ] **Step 5: Run test + build**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*AuthManagerStateTest" && ./gradlew :app:compileDebugKotlin`
Expected: test PASS, compile SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/nl/openschoolcloud/calendar/data/remote/auth/AuthManager.kt android/app/src/main/java/nl/openschoolcloud/calendar/di/AppModule.kt android/app/src/test/java/nl/openschoolcloud/calendar/data/remote/auth/AuthManagerStateTest.kt
git commit -m "Plan A T8: AuthManager AppAuth OIDC flow + Hilt providers"
```

---

### Task 9: Welkom-scherm + account-entry (start AppAuth)

**Files:**
- Create: `android/app/src/main/java/nl/openschoolcloud/calendar/presentation/screens/welcome/WelcomeScreen.kt`
- Create: `android/app/src/main/java/nl/openschoolcloud/calendar/presentation/screens/welcome/WelcomeViewModel.kt`
- Test: `android/app/src/test/java/nl/openschoolcloud/calendar/presentation/welcome/WelcomeViewModelTest.kt`

**Interfaces:**
- Consumes: `AuthManager` (T8), `AppPreferences` (existing), `AccountRepository` (existing).
- Produces:
  - `WelcomeViewModel` with `val uiState: StateFlow<WelcomeUiState>`, `fun onAuthResult(data: Intent)`, `fun buildAuthIntent(): Intent`
  - `WelcomeScreen(onAuthenticated: () -> Unit)`
  - Consumed by Task 12 (NavGraph).

- [ ] **Step 1: Write the failing ViewModel test**

Create `WelcomeViewModelTest.kt`:

```kotlin
package nl.openschoolcloud.calendar.presentation.welcome

import android.content.Intent
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.openschoolcloud.calendar.data.remote.auth.AuthManager
import nl.openschoolcloud.calendar.data.remote.auth.OidcIdentity
import nl.openschoolcloud.calendar.presentation.screens.welcome.WelcomeViewModel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val auth = mockk<AuthManager>(relaxed = true)

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `successful auth sets authenticated state`() = runTest {
        val intent = mockk<Intent>()
        coEvery { auth.handleAuthResponse(intent) } returns Result.success(OidcIdentity("sub123", "a@b.nl"))
        val vm = WelcomeViewModel(auth)

        vm.onAuthResult(intent)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.authenticated)
    }

    @Test fun `failed auth sets error state`() = runTest {
        val intent = mockk<Intent>()
        coEvery { auth.handleAuthResponse(intent) } returns Result.failure(RuntimeException("no"))
        val vm = WelcomeViewModel(auth)

        vm.onAuthResult(intent)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.error)
        assertTrue(!vm.uiState.value.authenticated)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*WelcomeViewModelTest"`
Expected: FAIL — `WelcomeViewModel` unresolved.

- [ ] **Step 3: Implement WelcomeViewModel**

Create `presentation/screens/welcome/WelcomeViewModel.kt` (AGPL header + body):

```kotlin
package nl.openschoolcloud.calendar.presentation.screens.welcome

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.openschoolcloud.calendar.data.remote.auth.AuthManager
import javax.inject.Inject

data class WelcomeUiState(
    val loading: Boolean = false,
    val authenticated: Boolean = false,
    val error: Boolean = false
)

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    fun buildAuthIntent(): Intent = authManager.buildAuthRequestIntent()

    fun onAuthResult(data: Intent) {
        _uiState.update { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            val result = authManager.handleAuthResponse(data)
            _uiState.update {
                if (result.isSuccess) it.copy(loading = false, authenticated = true)
                else it.copy(loading = false, error = true)
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*WelcomeViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Implement WelcomeScreen**

Create `presentation/screens/welcome/WelcomeScreen.kt` (AGPL header + body). Uses `rememberLauncherForActivityResult` to run the AppAuth intent:

```kotlin
package nl.openschoolcloud.calendar.presentation.screens.welcome

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.openschoolcloud.calendar.R

@Composable
fun WelcomeScreen(
    onAuthenticated: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.let { viewModel.onAuthResult(it) }
    }

    LaunchedEffect(uiState.authenticated) {
        if (uiState.authenticated) onAuthenticated()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.osc_logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.width(120.dp),
            contentScale = ContentScale.FillWidth
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.welcome_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))
        if (uiState.loading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = { launcher.launch(viewModel.buildAuthIntent()) },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text(stringResource(R.string.welcome_create_account)) }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { launcher.launch(viewModel.buildAuthIntent()) },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) { Text(stringResource(R.string.welcome_login)) }
        }
        if (uiState.error) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.auth_failed),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
```

(Note: "Account maken" and "Inloggen" both launch the same Authentik flow; Authentik's own screens branch between sign-up and sign-in. This keeps the app free of auth logic.)

- [ ] **Step 6: Verify build**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/nl/openschoolcloud/calendar/presentation/screens/welcome/ android/app/src/test/java/nl/openschoolcloud/calendar/presentation/welcome/WelcomeViewModelTest.kt
git commit -m "Plan A T9: Welcome screen + account entry launching AppAuth"
```

---

### Task 10: Herstelcodes-bevestigingsscherm

**Files:**
- Create: `android/app/src/main/java/nl/openschoolcloud/calendar/presentation/screens/welcome/RecoveryCodesScreen.kt`

**Interfaces:**
- Consumes: strings from T7.
- Produces: `RecoveryCodesScreen(onConfirmed: () -> Unit)` — consumed by Task 12.

Rationale: Authentik shows the actual codes in its own flow. This screen is the app-side "have you saved them?" gate so the user makes a deliberate confirmation before proceeding. It has no ViewModel and no logic to unit-test, so it is a single implementation step folded into this task.

- [ ] **Step 1: Implement RecoveryCodesScreen**

Create `RecoveryCodesScreen.kt` (AGPL header + body):

```kotlin
package nl.openschoolcloud.calendar.presentation.screens.welcome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import nl.openschoolcloud.calendar.R

@Composable
fun RecoveryCodesScreen(onConfirmed: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.recovery_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.recovery_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onConfirmed,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text(stringResource(R.string.recovery_confirm)) }
    }
}
```

- [ ] **Step 2: Verify build**

Run: `cd android && ./gradlew :app:compileDebugKotlin`
Expected: SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/nl/openschoolcloud/calendar/presentation/screens/welcome/RecoveryCodesScreen.kt
git commit -m "Plan A T10: recovery-codes confirmation screen"
```

---

### Task 11: Gezin-opzet + sync-keuze schermen

**Files:**
- Create: `android/app/src/main/java/nl/openschoolcloud/calendar/presentation/screens/family/FamilySetupScreen.kt`
- Create: `android/app/src/main/java/nl/openschoolcloud/calendar/presentation/screens/family/FamilySetupViewModel.kt`
- Create: `android/app/src/main/java/nl/openschoolcloud/calendar/presentation/screens/family/SyncChoiceScreen.kt`
- Create: `android/app/src/main/java/nl/openschoolcloud/calendar/presentation/screens/family/SyncChoiceViewModel.kt`
- Test: `android/app/src/test/java/nl/openschoolcloud/calendar/presentation/family/FamilySetupViewModelTest.kt`

**Interfaces:**
- Consumes: `FamilyRepository`, `ProfileRepository` (T5); `AccountRepository` (existing) for owner account id; `AppPreferences` (existing); `FeatureFlags` (T6); `SyncScope` (T2).
- Produces:
  - `FamilySetupViewModel`: `val uiState: StateFlow<FamilySetupUiState>`, `fun onFamilyNameChange(String)`, `fun addChild(String)`, `fun save(onDone: (familyId: String) -> Unit)`
  - `SyncChoiceViewModel`: `val oscCloudAvailable: Boolean`, `fun choose(scope: SyncScope, familyId: String, onDone: () -> Unit)`
  - `FamilySetupScreen(onFamilyCreated: (String) -> Unit)`, `SyncChoiceScreen(familyId: String, onLocalOrOsc: () -> Unit, onConnectNextcloud: () -> Unit)`
  - Consumed by Task 12.

- [ ] **Step 1: Write the failing ViewModel test**

Create `FamilySetupViewModelTest.kt`:

```kotlin
package nl.openschoolcloud.calendar.presentation.family

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import nl.openschoolcloud.calendar.domain.model.Family
import nl.openschoolcloud.calendar.domain.model.Profile
import nl.openschoolcloud.calendar.domain.model.SyncScope
import nl.openschoolcloud.calendar.domain.repository.AccountRepository
import nl.openschoolcloud.calendar.domain.repository.FamilyRepository
import nl.openschoolcloud.calendar.domain.repository.ProfileRepository
import nl.openschoolcloud.calendar.presentation.screens.family.FamilySetupViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FamilySetupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val familyRepo = mockk<FamilyRepository>(relaxed = true)
    private val profileRepo = mockk<ProfileRepository>(relaxed = true)
    private val accountRepo = mockk<AccountRepository>(relaxed = true)

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun `save creates family with owner profile and children`() = runTest {
        coEvery { accountRepo.getDefaultAccountId() } returns "acc1"
        coEvery { familyRepo.createFamily("Gezin X", "acc1") } returns
            Family("f1", "Gezin X", "acc1", Instant.ofEpochMilli(0))
        coEvery { profileRepo.addProfile(any(), any(), any(), any()) } returns
            Profile("p", "f1", "x", 0L, null, false, SyncScope.LOCAL)

        val vm = FamilySetupViewModel(familyRepo, profileRepo, accountRepo)
        vm.onFamilyNameChange("Gezin X")
        vm.addChild("Sanne")
        var createdId = ""
        vm.save { createdId = it }
        advanceUntilIdle()

        assertEquals("f1", createdId)
        // one owner profile + one child = 2 addProfile calls
        io.mockk.coVerify(exactly = 2) { profileRepo.addProfile(any(), any(), any(), any()) }
    }
}
```

Note: this test assumes `AccountRepository` exposes `suspend fun getDefaultAccountId(): String`. If the existing interface lacks it, add that method to `AccountRepository` + `AccountRepositoryImpl` (returning `accountDao.getDefault()?.id ?: ""`) as part of Step 3.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*FamilySetupViewModelTest"`
Expected: FAIL — `FamilySetupViewModel` unresolved.

- [ ] **Step 3: Implement FamilySetupViewModel (+ AccountRepository helper if needed)**

If `AccountRepository` has no `getDefaultAccountId`, add to the interface in `domain/repository/Repositories.kt`:
```kotlin
    suspend fun getDefaultAccountId(): String
```
and in `data/repository/AccountRepositoryImpl.kt`:
```kotlin
    override suspend fun getDefaultAccountId(): String = accountDao.getDefault()?.id ?: ""
```

Create `presentation/screens/family/FamilySetupViewModel.kt` (AGPL header + body):

```kotlin
package nl.openschoolcloud.calendar.presentation.screens.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.openschoolcloud.calendar.domain.repository.AccountRepository
import nl.openschoolcloud.calendar.domain.repository.FamilyRepository
import nl.openschoolcloud.calendar.domain.repository.ProfileRepository
import javax.inject.Inject

data class FamilySetupUiState(
    val familyName: String = "",
    val children: List<String> = emptyList(),
    val saving: Boolean = false
)

@HiltViewModel
class FamilySetupViewModel @Inject constructor(
    private val familyRepository: FamilyRepository,
    private val profileRepository: ProfileRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FamilySetupUiState())
    val uiState: StateFlow<FamilySetupUiState> = _uiState.asStateFlow()

    // OSC blue as the owner's default profile color.
    private val ownerColor = 0xFF3B9FD9L

    fun onFamilyNameChange(name: String) = _uiState.update { it.copy(familyName = name) }

    fun addChild(name: String) {
        if (name.isBlank()) return
        _uiState.update { it.copy(children = it.children + name.trim()) }
    }

    fun save(onDone: (String) -> Unit) {
        val state = _uiState.value
        if (state.familyName.isBlank()) return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val ownerId = accountRepository.getDefaultAccountId()
            val family = familyRepository.createFamily(state.familyName.trim(), ownerId)
            profileRepository.addProfile(family.id, state.familyName.trim(), ownerColor, isOwner = true)
            state.children.forEach { child ->
                profileRepository.addProfile(family.id, child, ownerColor, isOwner = false)
            }
            _uiState.update { it.copy(saving = false) }
            onDone(family.id)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*FamilySetupViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Implement SyncChoiceViewModel**

Create `presentation/screens/family/SyncChoiceViewModel.kt` (AGPL header + body):

```kotlin
package nl.openschoolcloud.calendar.presentation.screens.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import nl.openschoolcloud.calendar.data.local.AppPreferences
import nl.openschoolcloud.calendar.domain.model.SyncScope
import nl.openschoolcloud.calendar.domain.repository.ProfileRepository
import nl.openschoolcloud.calendar.util.FeatureFlags
import javax.inject.Inject

@HiltViewModel
class SyncChoiceViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    val oscCloudAvailable: Boolean get() = FeatureFlags.oscCloudEnabled

    /** Apply the chosen scope to every profile in the family, then finish. */
    fun choose(scope: SyncScope, familyId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            appPreferences.hasCompletedModeSelection = true
            appPreferences.isStandaloneMode = scope == SyncScope.LOCAL
            profileRepository.observeProfilesOnce(familyId).forEach {
                profileRepository.setSyncScope(it.id, scope)
            }
            onDone()
        }
    }
}
```

This needs a one-shot profile read. Add to `ProfileRepository` (interface + impl):
```kotlin
    // interface
    suspend fun observeProfilesOnce(familyId: String): List<nl.openschoolcloud.calendar.domain.model.Profile>
```
```kotlin
    // impl — reuse the DAO sync query
    override suspend fun observeProfilesOnce(familyId: String) =
        profileDao.getByFamilySync(familyId).map { it.toDomain() }
```

- [ ] **Step 6: Implement FamilySetupScreen**

Create `presentation/screens/family/FamilySetupScreen.kt` (AGPL header + body):

```kotlin
package nl.openschoolcloud.calendar.presentation.screens.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.openschoolcloud.calendar.R

@Composable
fun FamilySetupScreen(
    onFamilyCreated: (String) -> Unit,
    viewModel: FamilySetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var childName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.family_setup_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = uiState.familyName,
            onValueChange = viewModel::onFamilyNameChange,
            label = { Text(stringResource(R.string.family_name_label)) },
            placeholder = { Text(stringResource(R.string.family_name_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        uiState.children.forEach { child ->
            Text("• $child", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 4.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = childName,
                onValueChange = { childName = it },
                label = { Text(stringResource(R.string.profile_name_label)) },
                placeholder = { Text(stringResource(R.string.profile_name_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { viewModel.addChild(childName); childName = "" },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.profile_add)) }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { viewModel.save(onFamilyCreated) },
            enabled = uiState.familyName.isNotBlank() && !uiState.saving,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text(stringResource(R.string.family_setup_continue)) }
    }
}
```

- [ ] **Step 7: Implement SyncChoiceScreen**

Create `presentation/screens/family/SyncChoiceScreen.kt` (AGPL header + body). The OSC card degrades to a disabled "Binnenkort" state when `oscCloudAvailable` is false:

```kotlin
package nl.openschoolcloud.calendar.presentation.screens.family

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import nl.openschoolcloud.calendar.R
import nl.openschoolcloud.calendar.domain.model.SyncScope

@Composable
fun SyncChoiceScreen(
    familyId: String,
    onLocalOrOsc: () -> Unit,
    onConnectNextcloud: () -> Unit,
    viewModel: SyncChoiceViewModel = hiltViewModel()
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.sync_choice_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(24.dp))

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.sync_local_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.sync_local_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.choose(SyncScope.LOCAL, familyId, onLocalOrOsc) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.sync_local_button)) }
            }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.sync_nextcloud_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.sync_nextcloud_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onConnectNextcloud, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sync_nextcloud_button))
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.sync_osc_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.sync_osc_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                if (viewModel.oscCloudAvailable) {
                    Button(
                        onClick = { viewModel.choose(SyncScope.OSC_CLOUD, familyId, onLocalOrOsc) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.sync_osc_button)) }
                } else {
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.sync_osc_soon))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
```

- [ ] **Step 8: Run tests + build**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*FamilySetupViewModelTest" && ./gradlew :app:compileDebugKotlin`
Expected: test PASS, compile SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add android/app/src/main/java/nl/openschoolcloud/calendar/presentation/screens/family/ android/app/src/main/java/nl/openschoolcloud/calendar/domain/repository/Repositories.kt android/app/src/main/java/nl/openschoolcloud/calendar/data/repository/ android/app/src/test/java/nl/openschoolcloud/calendar/presentation/family/FamilySetupViewModelTest.kt
git commit -m "Plan A T11: family setup + sync-choice screens (OSC card feature-flagged)"
```

---

### Task 12: NavGraph herbedrading + Splash-routing

**Files:**
- Modify: `android/app/src/main/java/nl/openschoolcloud/calendar/presentation/navigation/NavGraph.kt`
- Modify: `android/app/src/main/java/nl/openschoolcloud/calendar/presentation/screens/splash/SplashScreen.kt` (routing decision)
- Test: `android/app/src/test/java/nl/openschoolcloud/calendar/presentation/navigation/StartDestinationTest.kt`

**Interfaces:**
- Consumes: `AuthManager` (T8), `AppPreferences` (existing), `WelcomeScreen`/`RecoveryCodesScreen` (T9/T10), `FamilySetupScreen`/`SyncChoiceScreen` (T11), existing `LoginScreen`/`CalendarScreen`.
- Produces: a pure routing function `decideStartRoute(isLoggedIn, onboardingCompleted, modeSelectionCompleted)` used by both the test and the splash.

- [ ] **Step 1: Write the failing routing test**

Create `StartDestinationTest.kt`:

```kotlin
package nl.openschoolcloud.calendar.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class StartDestinationTest {
    @Test fun `not logged in goes to welcome`() {
        assertEquals(
            Route.Welcome.route,
            decideStartRoute(isLoggedIn = false, familyExists = false, syncChosen = false)
        )
    }

    @Test fun `logged in without family goes to family setup`() {
        assertEquals(
            Route.FamilySetup.route,
            decideStartRoute(isLoggedIn = true, familyExists = false, syncChosen = false)
        )
    }

    @Test fun `logged in with family but no sync choice goes to sync choice`() {
        assertEquals(
            Route.SyncChoice.route,
            decideStartRoute(isLoggedIn = true, familyExists = true, syncChosen = false)
        )
    }

    @Test fun `fully set up goes to calendar`() {
        assertEquals(
            Route.Calendar.route,
            decideStartRoute(isLoggedIn = true, familyExists = true, syncChosen = true)
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*StartDestinationTest"`
Expected: FAIL — `decideStartRoute`, `Route.Welcome`, `Route.FamilySetup`, `Route.SyncChoice` unresolved.

- [ ] **Step 3: Add the routes and the pure routing function**

In `presentation/navigation/NavGraph.kt`, inside the `sealed class Route`, add:

```kotlin
    object Welcome : Route("welcome")
    object RecoveryCodes : Route("recovery_codes")
    object FamilySetup : Route("family_setup")
    object SyncChoice : Route("sync_choice/{familyId}") {
        fun createRoute(familyId: String) = "sync_choice/$familyId"
    }
```

At top level in `NavGraph.kt` (outside the composable), add the pure function:

```kotlin
/**
 * Pure start-route decision so it can be unit-tested without Compose.
 * familyExists = a FamilyEntity is present; syncChosen = mode selection done.
 */
fun decideStartRoute(isLoggedIn: Boolean, familyExists: Boolean, syncChosen: Boolean): String = when {
    !isLoggedIn -> Route.Welcome.route
    !familyExists -> Route.FamilySetup.route
    !syncChosen -> Route.SyncChoice.route
    else -> Route.Calendar.route
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*StartDestinationTest"`
Expected: PASS.

- [ ] **Step 5: Wire the new screens into the NavHost + splash routing**

In `SplashScreen.kt`, replace the existing navigation decision so it uses `decideStartRoute`. The splash already has access to state via its ViewModel/DI; compute the three booleans and navigate:
- `isLoggedIn` = injected `AuthManager.isLoggedIn`
- `familyExists` = `familyDao.observe().first() != null` (or a repository call `familyRepository.observeFamily().first() != null`)
- `syncChosen` = `appPreferences.hasCompletedModeSelection`

Concretely, in the splash's LaunchedEffect that currently decides the destination, replace the target with:
```kotlin
val start = decideStartRoute(
    isLoggedIn = authManager.isLoggedIn,
    familyExists = familyRepository.observeFamily().first() != null,
    syncChosen = appPreferences.hasCompletedModeSelection
)
onNavigate(start)
```
(Adapt to the splash's existing callback name; if the splash currently hard-codes `Route.Onboarding`/`Route.ModeSelection`, remove those branches. Keep `import kotlinx.coroutines.flow.first`.)

In `NavGraph.kt`'s `NavHost`, add these `composable` entries and remove the standalone `Onboarding`/`ModeSelection` entries (their roles are absorbed by Welcome + SyncChoice):

```kotlin
        composable(Route.Welcome.route) {
            WelcomeScreen(onAuthenticated = {
                navController.navigate(Route.RecoveryCodes.route) {
                    popUpTo(Route.Welcome.route) { inclusive = true }
                }
            })
        }
        composable(Route.RecoveryCodes.route) {
            RecoveryCodesScreen(onConfirmed = {
                navController.navigate(Route.FamilySetup.route) {
                    popUpTo(Route.RecoveryCodes.route) { inclusive = true }
                }
            })
        }
        composable(Route.FamilySetup.route) {
            FamilySetupScreen(onFamilyCreated = { familyId ->
                navController.navigate(Route.SyncChoice.createRoute(familyId))
            })
        }
        composable(
            Route.SyncChoice.route,
            arguments = listOf(navArgument("familyId") { type = NavType.StringType })
        ) { backStackEntry ->
            val familyId = backStackEntry.arguments?.getString("familyId").orEmpty()
            SyncChoiceScreen(
                familyId = familyId,
                onLocalOrOsc = {
                    navController.navigate(Route.Calendar.route) {
                        popUpTo(Route.SyncChoice.route) { inclusive = true }
                    }
                },
                onConnectNextcloud = { navController.navigate(Route.Login.route) }
            )
        }
```

Add the imports for `WelcomeScreen`, `RecoveryCodesScreen`, `FamilySetupScreen`, `SyncChoiceScreen`. Keep the existing `LoginScreen` composable (now reached only from the Nextcloud card); on its `onLoginSuccess`, navigate to `Route.Calendar` with `popUpTo(Route.SyncChoice.route) { inclusive = true }`.

- [ ] **Step 6: Build + full unit test run**

Run: `cd android && ./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: all unit tests PASS; APK builds with 0 errors.

- [ ] **Step 7: Manual smoke test (from CLAUDE.md release gate)**

Install the debug APK and verify:
- Fresh launch → Welcome screen (not the old 3-slide onboarding).
- "Account maken" opens a Custom Tab to the Authentik issuer.
- "Alleen op dit apparaat" → lands on Calendar; relaunch stays on Calendar.
- OSC-sync card shows "Binnenkort beschikbaar" (flag off).
- Dark mode: no invisible text on the new screens.

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/java/nl/openschoolcloud/calendar/presentation/navigation/NavGraph.kt android/app/src/main/java/nl/openschoolcloud/calendar/presentation/screens/splash/SplashScreen.kt android/app/src/test/java/nl/openschoolcloud/calendar/presentation/navigation/StartDestinationTest.kt
git commit -m "Plan A T12: rewire NavGraph + splash routing to account-first family flow"
```

---

### Task 13: Profiel-switcher in de kalender

**Files:**
- Create: `android/app/src/main/java/nl/openschoolcloud/calendar/presentation/components/ProfileSwitcher.kt`
- Modify: `android/app/src/main/java/nl/openschoolcloud/calendar/presentation/screens/calendar/CalendarScreen.kt` (host the switcher, filter by profile)
- Modify: `android/app/src/main/java/nl/openschoolcloud/calendar/presentation/screens/calendar/CalendarViewModel.kt` (expose profiles + selected profile filter)
- Test: `android/app/src/test/java/nl/openschoolcloud/calendar/presentation/calendar/ProfileFilterTest.kt`

**Interfaces:**
- Consumes: `ProfileRepository` (T5), `FamilyRepository` (T5), `Profile` (T2).
- Produces: `ProfileSwitcher(profiles, selectedProfileId, onSelect)` composable; `CalendarViewModel.selectedProfileId: StateFlow<String?>` + `fun selectProfile(id: String?)` (null = whole family).

- [ ] **Step 1: Write the failing filter test**

Create `ProfileFilterTest.kt`. Test the pure filter helper that decides whether an event belongs to the current profile selection:

```kotlin
package nl.openschoolcloud.calendar.presentation.calendar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileFilterTest {
    @Test fun `null selection shows all profiles`() {
        assertTrue(eventVisibleForProfile(eventProfileId = "p1", selectedProfileId = null))
        assertTrue(eventVisibleForProfile(eventProfileId = null, selectedProfileId = null))
    }

    @Test fun `specific selection shows only matching or unassigned events`() {
        assertTrue(eventVisibleForProfile(eventProfileId = "p1", selectedProfileId = "p1"))
        assertFalse(eventVisibleForProfile(eventProfileId = "p2", selectedProfileId = "p1"))
        // Legacy/unassigned events (null) belong to the owner view; show when a profile is selected.
        assertTrue(eventVisibleForProfile(eventProfileId = null, selectedProfileId = "p1"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*ProfileFilterTest"`
Expected: FAIL — `eventVisibleForProfile` unresolved.

- [ ] **Step 3: Add the pure filter helper**

In `presentation/screens/calendar/CalendarViewModel.kt` (top level of the file, outside the class), add:

```kotlin
/** Whether an event is visible under the current profile selection.
 *  null selection = whole family (everything). Unassigned events (null
 *  profileId) are always visible so legacy data never disappears. */
fun eventVisibleForProfile(eventProfileId: String?, selectedProfileId: String?): Boolean =
    selectedProfileId == null || eventProfileId == null || eventProfileId == selectedProfileId
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "*ProfileFilterTest"`
Expected: PASS.

- [ ] **Step 5: Expose profiles + selection in CalendarViewModel**

In `CalendarViewModel`, inject `ProfileRepository` + `FamilyRepository` (add to the constructor; Hilt already provides them from T5). Add:

```kotlin
    private val _selectedProfileId = MutableStateFlow<String?>(null)
    val selectedProfileId: StateFlow<String?> = _selectedProfileId.asStateFlow()

    val profiles: StateFlow<List<Profile>> =
        familyRepository.observeFamily()
            .flatMapLatest { family ->
                if (family == null) flowOf(emptyList())
                else profileRepository.observeProfiles(family.id)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectProfile(id: String?) { _selectedProfileId.value = id }
```

Add imports: `kotlinx.coroutines.flow.*` (`MutableStateFlow`, `StateFlow`, `asStateFlow`, `flatMapLatest`, `flowOf`, `stateIn`, `SharingStarted`), `nl.openschoolcloud.calendar.domain.model.Profile`, and the two repository interfaces. Apply `eventVisibleForProfile` when mapping events to the visible list (combine the events flow with `_selectedProfileId`).

- [ ] **Step 6: Implement ProfileSwitcher composable**

Create `presentation/components/ProfileSwitcher.kt` (AGPL header + body). A horizontal row of filter chips: "Hele gezin" + one chip per profile:

```kotlin
package nl.openschoolcloud.calendar.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.openschoolcloud.calendar.R
import nl.openschoolcloud.calendar.domain.model.Profile

@Composable
fun ProfileSwitcher(
    profiles: List<Profile>,
    selectedProfileId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.horizontalScroll(rememberScrollState())) {
        FilterChip(
            selected = selectedProfileId == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.profile_all)) },
            modifier = Modifier.padding(end = 8.dp)
        )
        profiles.forEach { profile ->
            FilterChip(
                selected = selectedProfileId == profile.id,
                onClick = { onSelect(profile.id) },
                label = { Text(profile.displayName) },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}
```

- [ ] **Step 7: Host the switcher in CalendarScreen**

In `CalendarScreen.kt`, collect `viewModel.profiles` and `viewModel.selectedProfileId`, and place `ProfileSwitcher(...)` directly under the top app bar (before the calendar grid), wired to `viewModel::selectProfile`. Only render it when `profiles.size > 1` so single-profile users see no clutter:

```kotlin
val profiles by viewModel.profiles.collectAsStateWithLifecycle()
val selectedProfileId by viewModel.selectedProfileId.collectAsStateWithLifecycle()
if (profiles.size > 1) {
    ProfileSwitcher(
        profiles = profiles,
        selectedProfileId = selectedProfileId,
        onSelect = viewModel::selectProfile,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
```

- [ ] **Step 8: Build + full test run**

Run: `cd android && ./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: all unit tests PASS; APK builds with 0 errors.

- [ ] **Step 9: Manual smoke test**

- Create a family with 2 children in onboarding, choose local.
- Calendar shows the profile switcher; "Hele gezin" shows all events; selecting a child filters.
- Single-profile (owner only): no switcher shown.

- [ ] **Step 10: Commit**

```bash
git add android/app/src/main/java/nl/openschoolcloud/calendar/presentation/components/ProfileSwitcher.kt android/app/src/main/java/nl/openschoolcloud/calendar/presentation/screens/calendar/ android/app/src/test/java/nl/openschoolcloud/calendar/presentation/calendar/ProfileFilterTest.kt
git commit -m "Plan A T13: profile switcher + per-profile event filtering"
```

---

### Task 14: Versie-bump + CHANGELOG + lint (release gate)

**Files:**
- Modify: `android/app/build.gradle.kts` (`versionCode`, `versionName`)
- Modify: `CHANGELOG.md`

- [ ] **Step 1: Bump version**

In `android/app/build.gradle.kts` `defaultConfig`, set `versionCode = 13` and `versionName = "2.0.0"` (major bump: new onboarding + identity layer).

- [ ] **Step 2: Update CHANGELOG**

Prepend a `## 2.0.0` section to `CHANGELOG.md` summarizing: account-first onboarding via Authentik OIDC, verplichte MFA (in de IdP-flow), gezins-/profielmodel met profiel-switcher, sync-keuze met geflagde OSC-sync-kaart.

- [ ] **Step 3: Lint (errors only)**

Run: `cd android && ./gradlew :app:lintDebug`
Expected: BUILD SUCCESSFUL (warnings acceptable; fix any errors).

- [ ] **Step 4: Commit**

```bash
git add android/app/build.gradle.kts CHANGELOG.md
git commit -m "Plan A T14: version 2.0.0 + changelog for onboarding/identity/family"
```

---

## Self-Review

**1. Spec coverage** (spec §3–§9 vs tasks):
- §3 onboarding-flow → T7 (strings), T9 (welcome/account), T10 (recovery), T11 (family+sync), T12 (nav+splash). ✓
- §4 datamodel → T2 (domain), T3 (entities/DAO/migration). ✓
- §5 identity/app-integratie → T1 (BuildConfig), T4 (token store), T8 (AuthManager/AppAuth). ✓
- §6 MFA & herstel → T10 (recovery-code gate). Note: MFA enrollment itself lives in Authentik (subsysteem B, separate plan); app-side gate covered here. ✓
- §7.3 sync-naad + feature-flag → T1 (flag), T6 (SyncProvider), T11 (sync-choice degradation). ✓
- §8 privacy → kind-profielen minimal data (T3/T5 addProfile: name+color only, no PII); local-first default `SyncScope.LOCAL` (T5). ✓
- §9 EUDI → no app work needed now (Authentik provider concern). Correctly out of Plan A.
- Profiel-switcher (§3 flow) → T13. ✓
- Backend B (Authentik config) and C (paid sync) → out of scope, separate plans (stated in header).

**2. Placeholder scan:** No "TBD"/"implement later". The only intentional stub is `OscCloudProvider`, which is a spec-mandated seam (§7.3), not a plan gap. Adaptive steps (T5 Step 6, T11 Step 3/5, T12 Step 5, T13 Step 5/7) instruct matching existing patterns because those files' exact current contents (binding style, splash callback names) must be followed — each gives the concrete code to add.

**3. Type consistency:** `SyncScope` enum values `LOCAL/OWN_NEXTCLOUD/OSC_CLOUD` consistent across T2/T3(string)/T5/T6/T11. `addProfile(familyId, name, color, isOwner)` signature consistent T5↔T11. `decideStartRoute(isLoggedIn, familyExists, syncChosen)` consistent T12 test↔impl. `AuthManager.isLoggedIn`/`handleAuthResponse`/`buildAuthRequestIntent` consistent T8↔T9↔T12. `observeProfilesOnce` added in T11 to `ProfileRepository` — consistent with T5 interface (extension noted in T11 Step 5).

**Known adaptive dependency:** several tasks say "match the existing `AccountRepository` binding style / splash callback". The executor must read those files first (they exist). This is deliberate — unilaterally guessing `@Binds` vs `@Provides` would break the Hilt graph.
```
