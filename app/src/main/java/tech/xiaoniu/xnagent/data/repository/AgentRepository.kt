package tech.xiaoniu.xnagent.data.repository

import kotlinx.coroutines.flow.StateFlow
import tech.xiaoniu.xnagent.ui.model.AgentUiModel

/**
 * 智能体数据入口。
 *
 * 数据来源是「服务端公开智能体」与「本地 agent 表」的合并结果：本地行可以覆盖服务端字段
 * （名称 / 简介 / 提示词）、标记隐藏、或在删除时作为墓碑；纯本地创建的自定义智能体只存在于本地。
 */
interface AgentRepository {
    /**
     * 合并后的智能体列表，含隐藏与已删除标记，由 UI 自行过滤。
     *
     * 顺序：服务端智能体在前（被本地覆盖过的用本地字段），纯本地自定义智能体在后。
     */
    val agents: StateFlow<List<AgentUiModel>>

    /**
     * 拉取服务端智能体列表并与本地行重新合并。
     *
     * 失败时抛出的异常由调用方处理；本地行仍会照常合并，保证离线时自定义智能体不丢。
     */
    suspend fun refresh()

    /**
     * 拉取远端会话列表，建立「智能体 → 已绑定会话」映射。
     *
     * 游客态或离线时静默失败并清空映射（旧账号的绑定不可跨账号复用）。
     */
    suspend fun refreshBindings()

    /** 按 ID 查询智能体，含隐藏项；已删除的墓碑不返回。 */
    suspend fun getAgent(id: String): AgentUiModel?

    /** 按已绑定会话 ID 反查智能体，用于自定义智能体的会话解析。 */
    suspend fun findBySession(sessionId: String): AgentUiModel?

    /**
     * 保存智能体改动（新建或覆盖）。
     *
     * [AgentUiModel.id] 为空时生成 `local-<uuid>` 作为纯本地自定义智能体；否则按
     * 「服务端是否存在该 identity」判定是覆盖还是本地自定义。
     *
     * @return 最终落库的智能体 ID。
     */
    suspend fun saveAgent(draft: AgentUiModel): String

    /** 删除智能体：自定义智能体直接删行，服务端智能体写删除墓碑避免刷新后复活。 */
    suspend fun deleteAgent(id: String)

    /** 切换隐藏状态。隐藏只在列表不展示，可恢复。 */
    suspend fun setAgentHidden(id: String, hidden: Boolean)

    /** 把智能体绑定到某个会话（仅自定义智能体需要落库）。 */
    suspend fun bindSession(agentId: String, sessionId: String)

    /** 清空本地智能体数据（清除本地数据时调用）。 */
    suspend fun clearAgents()
}
