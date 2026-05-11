package tech.xiaoniu.xnagent.ui.model

import tech.xiaoniu.xnagent.data.Model

/**
 * @author littlefogcat
 * @email littlefogcat@foxmail.com
 */
/** Ui model for LLM model ([Model]) **/
data class ModelUiModel(
    val id: String,
    val name: String,
    val provider: String,
) {
    val fullId : String get() = "$provider/$id"
    val label: String get() = "$provider/$name"
}