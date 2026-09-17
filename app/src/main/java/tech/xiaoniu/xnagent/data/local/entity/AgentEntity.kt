package tech.xiaoniu.xnagent.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 本地智能体表。
 *
 * 一行代表「本地对某个智能体的认知」，承载四种角色：自定义定义、系统智能体覆盖、
 * 隐藏标记、删除墓碑。系统智能体自身不落库（列表仍来自服务端），只有被本地改过
 * 或纯本地创建的智能体才会写入本表，避免本地库沦为服务端的镜像。
 */
@Entity(tableName = "agent")
data class Agent(
    /** 服务端 identity id，或本地生成的 `local-<uuid>`。 */
    @PrimaryKey val id: String,
    val name: String,
    val role: String = "",
    val description: String = "",
    /** 本地提示词。非空时以本地为准，请求体注入 system 消息且不再传 chatTarget。 */
    val prompt: String = "",
    val avatarUrl: String? = null,
    /** true 表示服务端不存在该 identity，是纯本地智能体。 */
    val isCustom: Boolean = false,
    /** 仅列表不展示，可恢复。 */
    val isHidden: Boolean = false,
    /** 服务端智能体的删除墓碑，避免下次刷新后复活。 */
    val isDeleted: Boolean = false,
    /** 自定义智能体绑定的会话；系统智能体的绑定从远端 chatTarget 反查，不落库。 */
    val boundSessionId: String? = null,
    val updateTime: Long,
)
