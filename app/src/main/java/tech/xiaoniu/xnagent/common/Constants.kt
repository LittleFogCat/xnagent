package tech.xiaoniu.xnagent.common

/**
 * 应用级共享常量。
 *
 * 跨多个 ViewModel / Repository 复用的字面量集中在此，避免散落各处导致修改时遗漏。
 * 仅放「值不变、跨层共享」的常量；模块内私有的常量请放在对应类的 `companion object`。
 */
object Constants {
    /**
     * 默认会话标题。
     *
     * 用于：
     * - LLM 标题生成失败 / 输出为空时的兜底；
     * - 新建本地会话时未指定 title 的回退；
     * - 远端会话缺 title 字段时的占位。
     */
    const val DEFAULT_TITLE = "新对话"
}