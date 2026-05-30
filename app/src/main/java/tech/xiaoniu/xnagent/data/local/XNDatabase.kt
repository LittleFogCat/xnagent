package tech.xiaoniu.xnagent.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import tech.xiaoniu.xnagent.data.local.dao.ChatDao
import tech.xiaoniu.xnagent.data.local.entity.ChatMessage
import tech.xiaoniu.xnagent.data.local.entity.Session

/**
 * 本地聊天数据库。
 */
@Database(
    entities = [Session::class, ChatMessage::class],
    version = 1,
    exportSchema = false,
)
abstract class XNDatabase : RoomDatabase() {
    /** 当前应用唯一使用的聊天 DAO。 */
    abstract fun chatDao(): ChatDao
}