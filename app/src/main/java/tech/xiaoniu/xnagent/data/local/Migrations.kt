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

/**
 * v2 → v3：新增 agent 表，用于存放本地自定义智能体与服务端智能体的本地覆盖 / 墓碑。
 *
 * 非破坏性迁移：老会话与消息全部保留，新表初始为空。
 *
 * 建表 SQL 逐字取自 Room 生成的 `app/schemas/…/3.json#createSql`（含 `IF NOT EXISTS`
 * 与反引号），保证运行期 schema 校验不因列定义差异而失败。
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `agent` (" +
                "`id` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`role` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, " +
                "`prompt` TEXT NOT NULL, " +
                "`avatarUrl` TEXT, " +
                "`isCustom` INTEGER NOT NULL, " +
                "`isHidden` INTEGER NOT NULL, " +
                "`isDeleted` INTEGER NOT NULL, " +
                "`boundSessionId` TEXT, " +
                "`updateTime` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
    }
}
