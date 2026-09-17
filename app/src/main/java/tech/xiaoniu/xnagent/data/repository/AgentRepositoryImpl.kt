package tech.xiaoniu.xnagent.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import tech.xiaoniu.xnagent.data.local.dao.AgentDao
import tech.xiaoniu.xnagent.data.local.entity.Agent
import tech.xiaoniu.xnagent.data.remote.api.ChatApi
import tech.xiaoniu.xnagent.data.remote.dto.AgentInfoDto
import tech.xiaoniu.xnagent.ui.model.AgentUiModel
import java.util.UUID
import javax.inject.Inject

/**
 * 智能体仓库实现。
 *
 * 合并策略：
 * - 服务端清单是「有哪些智能体」的权威来源；本地行只覆盖字段，不新增服务端智能体的存在；
 * - 本地 `isDeleted` 行是墓碑，把对应服务端智能体从合并结果里剔除；
 * - 本地 `isCustom` 行是纯本地智能体，直接附加在列表尾部；
 * - 绑定会话：服务端智能体从远端 `chatTarget` 反查，自定义智能体取本地行字段。
 */
class AgentRepositoryImpl @Inject constructor(
    private val agentDao: AgentDao,
    private val chatApi: ChatApi,
) : AgentRepository {
    private val tag = javaClass.simpleName

    private val _agents = MutableStateFlow<List<AgentUiModel>>(emptyList())
    override val agents: StateFlow<List<AgentUiModel>> = _agents.asStateFlow()

    /** 最近一次成功拉取的服务端清单（本地字段尚未覆盖）。 */
    private var remoteAgents: List<AgentUiModel> = emptyList()

    /** 智能体 ID → 已绑定会话 ID，来自远端会话列表的 `chatTarget`。 */
    private var bindings: Map<String, String> = emptyMap()

    override suspend fun refresh() = withContext(Dispatchers.IO) {
        try {
            remoteAgents = chatApi.getAgents().identities.map { it.toUiModel() }
        } finally {
            // 即使远端失败也要重新合并本地行，离线时自定义智能体不能消失。
            recompute()
        }
    }

    override suspend fun refreshBindings(): Unit = withContext(Dispatchers.IO) {
        runCatching {
            chatApi.getChats()
        }.onSuccess { response ->
            bindings = response.chats
                .mapNotNull { chat ->
                    chat.chatTarget?.id
                        ?.takeIf { it.isNotBlank() }
                        ?.let { agentId -> agentId to chat.id }
                }
                .toMap()
            recompute()
        }.onFailure {
            // 游客态、离线、或账号已切换：旧绑定不可复用，直接清空等待下次成功拉取。
            Log.d(tag, "refreshBindings: skipped", it)
            bindings = emptyMap()
            recompute()
        }
        Unit
    }

    override suspend fun getAgent(id: String): AgentUiModel? =
        _agents.value.firstOrNull { it.id == id }

    override suspend fun findBySession(sessionId: String): AgentUiModel? {
        val localRow = agentDao.getAgentBySession(sessionId)
        if (localRow != null) {
            return _agents.value.firstOrNull { it.id == localRow.id } ?: localRow.toUiModel()
        }
        val agentId = bindings.entries.firstOrNull { it.value == sessionId }?.key ?: return null
        return _agents.value.firstOrNull { it.id == agentId }
    }

    override suspend fun saveAgent(draft: AgentUiModel): String = withContext(Dispatchers.IO) {
        val id = draft.id.ifBlank { "local-${UUID.randomUUID()}" }
        val existing = agentDao.getAgent(id)
        // 服务端没有该 identity 时视为纯本地自定义智能体。
        val isCustom = existing?.isCustom ?: remoteAgents.none { it.id == id }
        agentDao.upsertAgent(
            Agent(
                id = id,
                name = draft.name.trim(),
                role = draft.role,
                description = draft.description,
                prompt = draft.prompt,
                avatarUrl = draft.avatarUrl ?: existing?.avatarUrl,
                isCustom = isCustom,
                isHidden = existing?.isHidden ?: false,
                isDeleted = false,
                boundSessionId = existing?.boundSessionId ?: draft.boundSessionId,
                updateTime = System.currentTimeMillis(),
            )
        )
        recompute()
        id
    }

    override suspend fun deleteAgent(id: String) = withContext(Dispatchers.IO) {
        val existing = agentDao.getAgent(id)
        val isCustom = existing?.isCustom ?: remoteAgents.none { it.id == id }
        if (isCustom) {
            // 纯本地智能体没有服务端来源，直接删行即可。
            agentDao.deleteAgent(id)
        } else {
            // 服务端智能体写墓碑，避免下次 refresh 后复活。
            val remote = remoteAgents.firstOrNull { it.id == id }
            agentDao.upsertAgent(
                Agent(
                    id = id,
                    name = existing?.name ?: remote?.name.orEmpty(),
                    role = existing?.role ?: remote?.role.orEmpty(),
                    description = existing?.description ?: remote?.description.orEmpty(),
                    prompt = existing?.prompt.orEmpty(),
                    avatarUrl = existing?.avatarUrl ?: remote?.avatarUrl,
                    isCustom = false,
                    isHidden = true,
                    isDeleted = true,
                    boundSessionId = existing?.boundSessionId,
                    updateTime = System.currentTimeMillis(),
                )
            )
        }
        recompute()
    }

    override suspend fun setAgentHidden(id: String, hidden: Boolean) = withContext(Dispatchers.IO) {
        val existing = agentDao.getAgent(id)
        if (existing != null) {
            agentDao.upsertAgent(existing.copy(isHidden = hidden, updateTime = System.currentTimeMillis()))
        } else {
            // 服务端智能体首次隐藏时才落库，避免本地库沦为服务端清单的镜像。
            val current = _agents.value.firstOrNull { it.id == id } ?: return@withContext
            agentDao.upsertAgent(
                Agent(
                    id = id,
                    name = current.name,
                    role = current.role,
                    description = current.description,
                    prompt = current.prompt,
                    avatarUrl = current.avatarUrl,
                    isCustom = current.isCustom,
                    isHidden = hidden,
                    isDeleted = false,
                    boundSessionId = current.boundSessionId,
                    updateTime = System.currentTimeMillis(),
                )
            )
        }
        recompute()
    }

    override suspend fun bindSession(agentId: String, sessionId: String) = withContext(Dispatchers.IO) {
        // 没有本地行说明是未改动过的服务端智能体，其绑定由 refreshBindings 从远端反查，无需落库。
        val existing = agentDao.getAgent(agentId) ?: return@withContext
        agentDao.upsertAgent(
            existing.copy(boundSessionId = sessionId, updateTime = System.currentTimeMillis())
        )
        recompute()
    }

    override suspend fun clearAgents() = withContext(Dispatchers.IO) {
        agentDao.clearAgents()
        bindings = emptyMap()
        recompute()
    }

    /** 读取本地行并与服务端清单重新合并，写入 [agents]。 */
    private suspend fun recompute() {
        val localById = agentDao.getAgentList().associateBy { it.id }
        val merged = buildList {
            remoteAgents.forEach { remote ->
                val local = localById[remote.id]
                when {
                    local == null -> add(remote.copy(boundSessionId = bindings[remote.id]))
                    // 墓碑：该服务端智能体已被用户删除，不再出现在任何列表里。
                    local.isDeleted -> Unit
                    else -> add(
                        remote.copy(
                            name = local.name,
                            role = local.role,
                            description = local.description,
                            prompt = local.prompt,
                            avatarUrl = local.avatarUrl ?: remote.avatarUrl,
                            isOverridden = true,
                            isHidden = local.isHidden,
                            boundSessionId = bindings[remote.id],
                        )
                    )
                }
            }
            localById.values
                .filter { it.isCustom && !it.isDeleted }
                .forEach { row ->
                    if (none { it.id == row.id }) add(row.toUiModel())
                }
        }
        _agents.value = merged
    }

    private fun AgentInfoDto.toUiModel(): AgentUiModel = AgentUiModel(
        id = id,
        name = name,
        role = role.orEmpty(),
        description = description.orEmpty(),
        avatarUrl = avatarUrl,
    )

    private fun Agent.toUiModel(): AgentUiModel = AgentUiModel(
        id = id,
        name = name,
        role = role,
        description = description,
        prompt = prompt,
        avatarUrl = avatarUrl,
        isCustom = isCustom,
        isOverridden = !isCustom,
        boundSessionId = boundSessionId,
        isHidden = isHidden,
        isDeleted = isDeleted,
    )
}
