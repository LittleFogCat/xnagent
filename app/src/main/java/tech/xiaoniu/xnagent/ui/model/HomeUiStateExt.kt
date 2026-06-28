package tech.xiaoniu.xnagent.ui.model

/**
 * 把会话列表按置顶状态划分为两组。
 *
 * - [Pair.first]：置顶会话，按更新时间倒序；
 * - [Pair.second]：普通会话，按更新时间倒序。
 *
 * 调用方负责在置顶组非空时才渲染「置顶」小标题；置顶组为空时 UI 跳过小标题。
 */
fun List<SessionUiModel>.partitionByPin(): Pair<List<SessionUiModel>, List<SessionUiModel>> {
    val pinned = filter { it.isPinned }.sortedByDescending { it.updatedAt }
    val normal = filterNot { it.isPinned }.sortedByDescending { it.updatedAt }
    return pinned to normal
}
