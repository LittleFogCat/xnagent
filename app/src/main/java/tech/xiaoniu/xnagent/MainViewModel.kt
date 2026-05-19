package tech.xiaoniu.xnagent

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import tech.xiaoniu.xnagent.data.repository.AuthRepository
import javax.inject.Inject

/**
 * 应用根状态。
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    authRepository: AuthRepository,
) : ViewModel() {
    val session = authRepository.session
}