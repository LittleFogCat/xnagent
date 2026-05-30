package tech.xiaoniu.xnagent.ui.model

/**
 * 模型 UI 结构。
 *
 * 由远端模型 DTO 转换而来，主要用于下拉展示和会话模型匹配。
 */
data class ModelUiModel(
    val id: String,
    val name: String,
    val provider: String,
) {
    val fullId : String get() = "$provider/$id"
    val label: String get() = "$provider/$name"
}