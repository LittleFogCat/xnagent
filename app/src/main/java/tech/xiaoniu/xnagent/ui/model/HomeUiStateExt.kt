package tech.xiaoniu.xnagent.ui.model

/**
 * @author littlefogcat
 * @email littlefogcat@foxmail.com
 */

/**
 * 将会话列表按日期分组。
 * 分组包括：
 * - 今天
 * - 昨天
 * - 7天内
 * - 30天内
 * - 更早的，按照月份分组，格式：yyyy年MM月
 */
fun List<SessionUiModel>.groupByDate(): List<SessionGroup> {
    val now = System.currentTimeMillis()
    val todayStart = now - (now % (24 * 60 * 60 * 1000))
    val yesterdayStart = todayStart - 24 * 60 * 60 * 1000
    val sevenDaysAgo = now - 7 * 24 * 60 * 60 * 1000
    val thirtyDaysAgo = now - 30 * 24 * 60 * 60 * 1000

    val groups = mutableMapOf<String, MutableList<SessionUiModel>>()

    for (session in this) {
        val groupKey = when {
            session.lastMessageTime >= todayStart -> "今天"
            session.lastMessageTime >= yesterdayStart -> "昨天"
            session.lastMessageTime >= sevenDaysAgo -> "7天内"
            session.lastMessageTime >= thirtyDaysAgo -> "30天内"
            else -> {
                // 更早的，按照月份分组
                val date = java.util.Date(session.lastMessageTime)
                val calendar = java.util.Calendar.getInstance().apply { time = date }
                "${calendar.get(java.util.Calendar.YEAR)}年${calendar.get(java.util.Calendar.MONTH) + 1}月"
            }
        }
        groups.getOrPut(groupKey) { mutableListOf() }.add(session)
    }

    return groups.map { SessionGroup(it.key, it.value) }
}