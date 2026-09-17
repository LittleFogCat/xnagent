package tech.xiaoniu.xnagent.ui.screen.agent

/**
 * 智能体详情页意图集合。
 */
sealed class AgentDetailIntent {
    /**
     * 载入指定智能体的表单内容。
     *
     * [agentId] 为 null 表示新建：重置为空表单。
     */
    data class Load(val agentId: String?) : AgentDetailIntent()

    data class UpdateName(val name: String) : AgentDetailIntent()

    data class UpdateDescription(val description: String) : AgentDetailIntent()

    data class UpdatePrompt(val prompt: String) : AgentDetailIntent()

    /** 保存表单内容到本地（新建自定义智能体或覆盖既有智能体）。 */
    object Save : AgentDetailIntent()

    /** 打开该智能体已绑定的会话，没有则新建一条。 */
    object OpenChat : AgentDetailIntent()

    /** 切换隐藏状态。 */
    data class SetHidden(val hidden: Boolean) : AgentDetailIntent()

    /** 删除该智能体。 */
    object Delete : AgentDetailIntent()

    /** 消费一次性提示，避免重复展示。 */
    object ConsumeNotice : AgentDetailIntent()
}
