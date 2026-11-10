package com.akay.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AxMigrations {
    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `http_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `session_id` TEXT NOT NULL, `method` TEXT NOT NULL, `url` TEXT NOT NULL, `http_version` TEXT NOT NULL, `headers_json` TEXT NOT NULL, `body_text` TEXT NOT NULL, `response_status` INTEGER, `response_headers_json` TEXT NOT NULL, `response_body_text` TEXT NOT NULL, `response_time_ms` INTEGER NOT NULL, `source` TEXT NOT NULL, `created_at` INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_http_transactions_session_id` ON `http_transactions` (`session_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_http_transactions_created_at` ON `http_transactions` (`created_at`)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `fuzz_jobs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `engine` TEXT NOT NULL, `request_template` TEXT NOT NULL, `payload_sets_json` TEXT NOT NULL, `threads` INTEGER NOT NULL, `request_cap` INTEGER NOT NULL, `delay_ms` INTEGER NOT NULL, `status` TEXT NOT NULL, `created_at` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `fuzz_results` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `job_id` INTEGER NOT NULL, `payload` TEXT NOT NULL, `status` INTEGER NOT NULL, `length` INTEGER NOT NULL, `time_ms` INTEGER NOT NULL, `hit` INTEGER NOT NULL, `created_at` INTEGER NOT NULL)"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_fuzz_results_job_id` ON `fuzz_results` (`job_id`)")
        }
    }

    /** Optional per-proxy locale/zone and SOCKS remote-DNS flag. Existing rows keep working. */
    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE proxies ADD COLUMN timezone TEXT")
            db.execSQL("ALTER TABLE proxies ADD COLUMN locale TEXT")
            db.execSQL("ALTER TABLE proxies ADD COLUMN remote_dns INTEGER NOT NULL DEFAULT 0")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_6_7, MIGRATION_7_8)
}
