package tech.xiaoniu.xnagent.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

/**
 * @author littlefogcat
 * @email littlefogcat@foxmail.com
 */

fun <T> Flow<T>.toStateFlow(scope: CoroutineScope, initialValue: T): StateFlow<T> {
    return stateIn(scope, SharingStarted.WhileSubscribed(5000), initialValue)
}

fun <T, R> Flow<T>.toStateFlowMapped(
    scope: CoroutineScope,
    initialValue: R,
    mapping: suspend (T) -> R
): StateFlow<R> =
    map(mapping)
        .stateIn(
            scope,
            SharingStarted.WhileSubscribed(5000),
            initialValue
        )

fun <T> Flow<T>.toSharedFlow(scope: CoroutineScope): Flow<T> =
    shareIn(
        scope,
        SharingStarted.WhileSubscribed(5000),
        1
    )

fun <T, R> Flow<T>.toSharedFlowMapped(
    scope: CoroutineScope,
    mapping: suspend (T) -> R
): Flow<R> =
    map(mapping)
        .shareIn(
            scope,
            SharingStarted.WhileSubscribed(5000),
            1
        )