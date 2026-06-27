# 认证业务

XNAgent 客户端使用服务端 **v2 鉴权体系**（双 token + 设备绑定 + Token Rotation），并提供「游客模式」以无登录方式体验基础聊天。完整接口定义见 [`../../api/auth.md`](../../api/auth.md)。

## 1. 认证态

`AuthSession`（`data/repository/AuthSession.kt`）是应用内唯一的认证态结构：

```kotlin
data class AuthSession(
    val token: String? = null,            // accessToken
    val refreshToken: String? = null,
    val user: AuthUser? = null,
    val isGuest: Boolean = false,
) {
    val isLoggedIn: Boolean       // token 非空 + user 非空
    val canEnterHome: Boolean     // 登录或游客
}
```

- `token` = v2 accessToken，**短期**（默认 2h）；
- `refreshToken` = v2 refreshToken，**长期**（默认 30d），与 `deviceId` 绑定；
- `isGuest` 为 true 时表示用户选择「游客模式」进入首页，无 token。

### 持久化

`AuthStore`（`data/local/AuthStore.kt`）把 `AuthSession` 持久化到 SharedPreferences（文件名 `auth_store`）：

| 键 | 用途 |
| --- | --- |
| `token` | 当前 accessToken |
| `refresh_token` | 当前 refreshToken |
| `device_id` | 设备唯一标识（首次调用时生成 UUID 并持久化） |
| `username` / `email` | 用户身份 |
| `is_guest` | 游客态标记 |

> `updateTokens(accessToken, refreshToken)` 只更新 token 字段，保留 `user` 等其他信息，专供 `TokenRefreshHandler` 在拦截器线程使用。

## 2. 业务流程

### 2.1 注册（邮箱 + 验证）

注册是三步流程：

```
┌────────────────────────────┐
│ 1. GET /api/register/captcha │
│    → challengeId + question  │
└──────────────┬─────────────┘
               ▼
┌──────────────────────────────────────────────┐
│ 2. POST /api/register/request                │
│    { email, password, captchaId, captchaAnswer } │
│    → 异步发送邮箱验证码                       │
└──────────────┬─────────────────────────────┘
               ▼
┌──────────────────────────────────────┐
│ 3. POST /api/register/verify         │
│    { email, code }                    │
│    → { success, token, user } (v1)    │
└──────────────┬───────────────────────┘
               ▼
        若缓存了 password：立即调用 /api/login-v2 升级为 v2 双 token
```

代码位置：

- `AuthRepository.requestRegisterCaptcha / requestRegister / verifyRegister`；
- `LoginViewModel.refreshRegisterCaptcha / requestRegisterCode / completeRegister`；
- `LoginIntent`：`RefreshCaptcha` / `RequestRegisterCode` / `CompleteRegister` / `ToggleMode`。

> 频率限制：同一 IP 每小时最多 5 次，同一 IP + 邮箱组合每 60 秒最多 1 次。

### 2.2 登录（v2 双 token + 设备绑定）

```
POST /api/login-v2
{ email, password, deviceId, deviceName? }
→ { accessToken, refreshToken, expiresIn, user }
```

- `deviceId` 由 `AuthStore.getDeviceId()` 懒生成并持久化，**首装后保持稳定**；
- `deviceName` 可选，便于多设备列表识别；
- 登录成功后 `AuthRepository.login` 把会话通过 `AuthStore.saveSession` 写入本地。

### 2.3 Token Rotation（自动刷新）

```
普通请求 401 触发
  ↓
TokenRefreshHandler.refreshToken()（ReentrantLock 单飞）
  ↓
POST /api/refresh { refreshToken, deviceId }
  ↓
AuthStore.updateTokens(accessToken, refreshToken)
  ↓
原始请求附带新 accessToken 重试
```

实现细节：

- `NetworkModule.provideOkHttpClient` 添加 401 拦截器，跳过 `/api/refresh`、`/api/logout-v2`；
- `TokenRefreshHandler` 用 `ReentrantLock + Condition` 保证同一时刻只发一个刷新请求，其他请求 `await` 10s；
- 刷新失败时 `authStore.clear()`，触发上层 `MainViewModel` 把用户带回 `Login` 页面。

### 2.4 游客模式

`AuthRepository.continueAsGuest` 把 `AuthSession(isGuest = true)` 写入本地，**不发起任何网络请求**。游客态下：

- 可以使用首页基础聊天（模型与公开智能体）；
- 单会话消息数上限 **10 条**（`GUEST_USER_MESSAGE_LIMIT`，`HomeViewModel` 私有常量）；
- 达到上限后输入区上方提示「登录 / 注册」入口，发送按钮自动 disable。

游客登录后的会话在登录时通过 `HomeRepository.syncLocalChatsToRemote()` 补传到远端。

### 2.5 登录保护（图形验证码）

`LoginViewModel.login` 维护 `loginFailedAttempts` 计数器：

- 连续 3 次 401 后开启「图形验证码」（`loginCaptchaRequired = true`）；
- 图形验证码由 `CaptchaQuestionImage` 在客户端生成（5 位 ASCII），校验在 ViewModel 内完成，**不发送到服务端**；
- 校验通过后再发起网络登录。

## 3. 登出

`AuthRepository.logout` 流程：

```
AuthRepository.logout
  ↓
若 refreshToken 非空：POST /api/logout-v2 { refreshToken }（best-effort）
  ↓
TokenRefreshHandler.reset() 唤醒等待中的刷新线程
  ↓
AuthStore.clear() 清空本地 + 重置内存态
  ↓
MainViewModel 监听 session 变化，自动切回 Login 页面
```

`SettingsScreen` 中「退出登录」或「清除本地数据」都会调用该流程。

## 4. 路由与认证态联动

`MainViewModel.init` 中订阅 `authRepository.session`：

- `!session.canEnterHome` → `MainDestination.Login`；
- 在 `Login` 页面且已 `canEnterHome` → `MainDestination.Home`；
- 其他情况下保留当前 `destination`（用于从抽屉进入 `Settings`）。

> 抽屉中「前往登录」按钮在已登录态不会显示（`showGuestLoginButton = !uiState.session.isGuest`）。
