package tech.xiaoniu.xnagent.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2：会话表新增 isPinned 列。
 *
 * 非破坏性迁移：老用户聊天记录全部保留，新增列默认 0（不置顶）。
 *
 * 后续破坏性变更提醒：
 * - 本迁移仅追加列（`ALTER TABLE ADD COLUMN ... DEFAULT 0`），老数据自动填默认值；
 * - 若未来需要破坏性变更（如删除列、重命名表、调整列类型），必须同时评估
 *   [tech.xiaoniu.xnagent.AppModule.provideDatabase] 中 `fallbackToDestructiveMigration(dropAllTables = BuildConfig.DEBUG)`
 *   的兜底策略——release 包将在缺 Migration 时直接崩溃而不是静默清空；
 * - 若涉及"老数据回填"语义（如新增字段需要从远端拉取），应在迁移结束后再异步触发补齐；
 * - 新增 Migration 直接追加为 `MIGRATION_2_3` / `MIGRATION_3_4`，由 Room 按版本号链式调用。
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE session ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
    }
}
