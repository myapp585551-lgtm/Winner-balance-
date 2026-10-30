package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.BuildConfig

@Database(
    entities = [
        UserEntity::class,
        DepositRequestEntity::class,
        WithdrawalRequestEntity::class,
        LedgerEntryEntity::class,
        SiteSettingsEntity::class,
        AdminAccountEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class WinnerDatabase : RoomDatabase() {
    abstract fun winnerDao(): WinnerDao

    companion object {
        const val VERIFIED_TEST_USER_ID = "usr-verified-wb01"
        const val VERIFIED_TEST_PUBLIC_ID = "WB7x9Km2Pq4Rt8Vz3N"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN avatarUri TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val now = System.currentTimeMillis()
                val deployedUrl = BuildConfig.DEFAULT_PRODUCTION_BASE_URL

                // Update any existing unconfigured domain (e.g., winnerbalance.com) to the actual deployed URL
                db.execSQL(
                    """
                    UPDATE site_settings
                    SET productionBaseUrl = '$deployedUrl', updatedAtEpochMs = $now
                    WHERE productionBaseUrl LIKE '%winnerbalance.com%'
                       OR productionBaseUrl LIKE '%your-domain.com%'
                       OR productionBaseUrl LIKE '%example.com%'
                       OR productionBaseUrl = ''
                    """.trimIndent()
                )

                // Ensure global site_settings row exists with actual deployed URL
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO site_settings (id, bkashNumber, productionBaseUrl, updatedAtEpochMs)
                    VALUES ('global', '01700000000', '$deployedUrl', $now)
                    """.trimIndent()
                )

                seedVerifiedPublicUser(db, now)
            }
        }

        private fun seedVerifiedPublicUser(db: SupportSQLiteDatabase, now: Long) {
            db.execSQL(
                """
                INSERT OR IGNORE INTO users (
                    id, publicId, displayName, balancePaisa, reservedPaisa, isActive, createdAtEpochMs, updatedAtEpochMs, avatarUri
                ) VALUES (
                    '$VERIFIED_TEST_USER_ID', '$VERIFIED_TEST_PUBLIC_ID', 'Tanvir Rahman', 125000, 0, 1, $now, $now, NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO ledger_entries (
                    entryId, userId, userDisplayName, actionType, deltaPaisa, balanceBeforePaisa, balanceAfterPaisa, referenceId, note, actorEmail, createdAtEpochMs
                ) VALUES (
                    'ldg-verified-wb01', '$VERIFIED_TEST_USER_ID', 'Tanvir Rahman', 'USER_CREATED', 125000, 0, 125000, 'INIT-WB01', 'Initial active user account provisioned', 'system@portal', $now
                )
                """.trimIndent()
            )
        }

        @Volatile
        private var instance: WinnerDatabase? = null

        fun getInstance(context: Context): WinnerDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WinnerDatabase::class.java,
                    "winner_balance_production.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            val now = System.currentTimeMillis()
                            val deployedUrl = BuildConfig.DEFAULT_PRODUCTION_BASE_URL
                            db.execSQL(
                                """
                                INSERT OR IGNORE INTO site_settings (id, bkashNumber, productionBaseUrl, updatedAtEpochMs)
                                VALUES ('global', '01700000000', '$deployedUrl', $now)
                                """.trimIndent()
                            )
                            seedVerifiedPublicUser(db, now)
                        }
                    })
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
