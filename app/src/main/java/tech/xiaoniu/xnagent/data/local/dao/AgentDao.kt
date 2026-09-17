package tech.xiaoniu.xnagent.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import tech.xiaoniu.xnagent.data.local.entity.Agent

/**
 * 本地智能体数据访问接口。
 *
 * 只承载「被本地改过」或「纯本地创建」的智能体行；服务端未改动的智能体不进本表。
 */
@Dao
interface AgentDao {
    @Query("SELECT * FROM agent ORDER BY updateTime DESC")
    fun queryAgentList(): Flow<List<Agent>>

    @Query("SELECT * FROM agent ORDER BY updateTime DESC")
    suspend fun getAgentList(): List<Agent>

    @Query("SELECT * FROM agent WHERE id = :agentId LIMIT 1")
    suspend fun getAgent(agentId: String): Agent?

    @Query("SELECT * FROM agent WHERE boundSessionId = :sessionId LIMIT 1")
    suspend fun getAgentBySession(sessionId: String): Agent?

    @Upsert
    suspend fun upsertAgent(agent: Agent)

    @Query("DELETE FROM agent WHERE id = :agentId")
    suspend fun deleteAgent(agentId: String)

    @Query("DELETE FROM agent")
    suspend fun clearAgents()
}
