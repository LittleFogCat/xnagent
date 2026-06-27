# 认证与资料接口

所有接口路径前缀均为 `/api`。

## v2 与 v1 鉴权体系对照

仓库同时维护两套登录接口，**v1 保持完全向后兼容**，新业务建议直接使用 v2：

| 维度 | v1（`POST /api/login`） | v2（`POST /api/login-v2`） |
| --- | --- | --- |
| 鉴权 token | 单一长 token（默认 `30` 天，`CHAT_AUTH_TOKEN_TTL_MS` 可覆盖） | 双 token：accessToken（默认 `2` 小时，`CHAT_AUTH_V2_ACCESS_TTL_MS` 可覆盖）+ refreshToken（默认 `30` 天，`CHAT_AUTH_V2_REFRESH_TTL_MS` 可覆盖） |
| Token 格式 | `base64url({ username, exp }).signature` | accessToken：`base64url({ sub: userId, exp }).signature`；refreshToken：`crypto.randomBytes(40).hex()`，服务端存 SHA-256 哈希 |
| 续期 | 过期后必须重新登录 | refresh 接口 + Token Rotation，refresh token 滑动续期 |
| 设备隔离 | 不支持 | 必须传 `deviceId`，refresh token 与设备绑定 |
| 撤销 | 无服务端机制 | `/api/logout-v2` 主动吊销 refresh token |
| 中间件 | `middleware/auth.js` | `middleware/authV2.js`（独立文件，与 v1 不互通） |

### 获取注册人机验证

**接口描述**

获取一次性的人机验证题目，用于后续注册验证码申请。

**请求路径**

`GET /api/register/captcha`

**路径参数**

无。

**请求参数**

无。

**返回示例**

```json
{
  "challengeId": "0b49fb26-28d8-4472-a65b-7d6c85d9784c",
  "question": "3 + 5 = ?",
  "expiresInMs": 300000
}
```

**返回参数**

| 参数名 | 参数类型 | 是否必需 | 参数说明及示例值 |
| --- | --- | --- | --- |
| challengeId | string | 是 | 验证题 ID，例如 `0b49fb26-28d8-4472-a65b-7d6c85d9784c`。 |
| question | string | 是 | 数学题文本，例如 `3 + 5 = ?`。 |
| expiresInMs | number | 是 | 验证题剩余有效期，单位毫秒，固定为 `300000`。 |

**所需权限**

无需登录。

**其他说明**

`challengeId` 需要在“发送注册验证码”接口中以 `captchaId` 字段提交，且每个 challenge 只能成功消费一次。

### 发送注册验证码

**接口描述**

校验邮箱、密码、人机验证和频率限制后，创建待注册记录，并异步发送邮件验证码。

**请求路径**

`POST /api/register/request`

**路径参数**

无。

**请求参数**

| 参数名 | 参数类型 | 是否必需 | 参数说明及示例值 |
| --- | --- | --- | --- |
| email | string | 是 | 注册邮箱，例如 `user@example.com`。服务端会转为小写并去除前后空格。 |
| password | string | 是 | 登录密码，长度必须在 `8` 到 `128` 个字符之间。 |
| captchaId | string | 是 | 上一步获取到的 `challengeId`。 |
| captchaAnswer | string | 是 | 人机验证答案，例如 `8`。 |

**请求示例**

```json
{
  "email": "user@example.com",
  "password": "12345678",
  "captchaId": "0b49fb26-28d8-4472-a65b-7d6c85d9784c",
  "captchaAnswer": "8"
}
```

**返回示例**

```json
{
  "success": true,
  "email": "user@example.com",
  "expiresInMs": 600000,
  "retryAfterSeconds": 60,
  "remainingThisHour": 4
}
```

**返回参数**

| 参数名 | 参数类型 | 是否必需 | 参数说明及示例值 |
| --- | --- | --- | --- |
| success | boolean | 是 | 是否请求成功，成功时固定为 `true`。 |
| email | string | 是 | 实际保存的邮箱，例如 `user@example.com`。 |
| expiresInMs | number | 是 | 邮箱验证码有效期，单位毫秒，固定为 `600000`。 |
| retryAfterSeconds | number | 是 | 下一次允许向同一邮箱再次发送验证码前需要等待的秒数，例如 `60`。 |
| remainingThisHour | number | 是 | 当前 IP 在本小时剩余的可发送次数，例如 `4`。 |

**所需权限**

无需登录。

**其他说明**

- 邮件发送是后台异步任务，接口会在邮件真正发出前先返回成功。
- 可能返回的错误状态码：`400`（邮箱、密码或人机验证不合法）、`409`（邮箱已注册）、`429`（发送过于频繁）。
- 频率限制为同一 IP 每小时最多 `5` 次，同一 IP + 邮箱组合每 `60` 秒最多 `1` 次。

### 完成注册验证

**接口描述**

使用邮箱验证码完成注册，创建用户并直接返回登录令牌。

**请求路径**

`POST /api/register/verify`

**路径参数**

无。

**请求参数**

| 参数名 | 参数类型 | 是否必需 | 参数说明及示例值 |
| --- | --- | --- | --- |
| email | string | 是 | 注册邮箱，例如 `user@example.com`。 |
| code | string | 是 | 6 位数字验证码，例如 `123456`。 |

**请求示例**

```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

**返回示例**

```json
{
  "success": true,
  "token": "<token>",
  "user": {
    "username": "user@example.com",
    "email": "user@example.com"
  }
}
```

**返回参数**

| 参数名 | 参数类型 | 是否必需 | 参数说明及示例值 |
| --- | --- | --- | --- |
| success | boolean | 是 | 是否注册成功，成功时固定为 `true`。 |
| token | string | 是 | 鉴权令牌，格式为 `base64url(payload).base64url(signature)`。 |
| user | object | 是 | 新注册用户的基础身份对象。 |
| user.username | string | 是 | 邮箱身份，例如 `user@example.com`。 |
| user.email | string | 是 | 用户邮箱，例如 `user@example.com`。 |

**所需权限**

无需登录。

**其他说明**

- 新用户的 `nickname` 初始值会被设置为邮箱地址。
- 可能返回的错误状态码：`400`（邮箱格式错误、验证码错误、验证码过期或不存在）、`409`（邮箱已注册）。

### 登录

**接口描述**

使用邮箱和密码登录，成功后返回登录令牌。

**请求路径**

`POST /api/login`

**路径参数**

无。

**请求参数**

| 参数名 | 参数类型 | 是否必需 | 参数说明及示例值 |
| --- | --- | --- | --- |
| email | string | 是 | 登录邮箱，例如 `user@example.com`。前端也可传 `username`，服务端会优先取 `email`。 |
| password | string | 是 | 登录密码，例如 `12345678`。 |

**请求示例**

```json
{
  "email": "user@example.com",
  "password": "12345678"
}
```

**返回示例**

```json
{
  "success": true,
  "token": "<token>",
  "user": {
    "username": "user@example.com",
    "email": "user@example.com"
  }
}
```

**返回参数**

| 参数名 | 参数类型 | 是否必需 | 参数说明及示例值 |
| --- | --- | --- | --- |
| success | boolean | 是 | 是否登录成功，成功时固定为 `true`。 |
| token | string | 是 | 登录令牌。默认有效期为 `7` 天，可通过环境变量 `CHAT_AUTH_TOKEN_TTL_MS` 覆盖。 |
| user | object | 是 | 当前登录身份对象。 |
| user.username | string | 是 | 邮箱身份，例如 `user@example.com`。 |
| user.email | string | 是 | 用户邮箱，例如 `user@example.com`。 |

**所需权限**

无需登录。

**其他说明**

可能返回的错误状态码：`400`（邮箱或密码为空）、`401`（邮箱或密码错误）。

### v2 登录（双 token + 设备绑定）

**接口描述**

`POST /api/login` 的 v2 版本，引入 access token + refresh token 的双 token 机制。access token 有效期短（默认 `2` 小时）用于日常请求鉴权；refresh token 有效期长（默认 `30` 天）仅用于刷新 access token。

客户端必须在每次调用中携带 `deviceId`，服务端会用 `deviceId` 绑定该设备的 refresh token，实现按设备登出与防止跨设备重放。

**请求路径**

`POST /api/login-v2`

**路径参数**

无。

**请求参数**

| 参数名 | 参数类型 | 是否必需 | 参数说明及示例值 |
| --- | --- | --- | --- |
| email | string | 是 | 登录邮箱，例如 `user@example.com`。服务端会转为小写并去除前后空格。 |
| password | string | 是 | 登录密码。 |
| deviceId | string | 是 | 设备唯一标识，长度必须在 `8` 到 `128` 个字符之间。建议使用客户端持久化生成的 UUID。 |
| deviceName | string | 否 | 设备名称，便于用户在多设备列表中识别，如 `iPhone 15`、`Chrome on macOS`。最长 `100` 个字符。 |

**请求示例**

```json
{
  "email": "user@example.com",
  "password": "12345678",
  "deviceId": "550e8400-e29b-41d4-a716-446655440000",
  "deviceName": "iPhone 15"
}
```

**返回示例**

```json
{
  "success": true,
  "accessToken": "<jwt>",
  "refreshToken": "<random>",
  "expiresIn": 7200,
  "user": {
    "username": "XiaoNiu",
    "email": "user@example.com"
  }
}
```

**返回参数**

| 参数名 | 参数类型 | 是否必需 | 参数说明及示例值 |
| --- | --- | --- | --- |
| success | boolean | 是 | 是否登录成功，成功时固定为 `true`。 |
| accessToken | string | 是 | 访问令牌。默认有效期 `7200` 秒（`2` 小时），可通过环境变量 `CHAT_AUTH_V2_ACCESS_TTL_MS` 覆盖。格式为 `base64url(payload).base64url(signature)`，payload 中 `sub` 字段为用户 ID。 |
| refreshToken | string | 是 | 刷新令牌。默认有效期 `30` 天，可通过环境变量 `CHAT_AUTH_V2_REFRESH_TTL_MS` 覆盖。客户端应安全持久化（如 Keychain / EncryptedSharedPreferences），禁止写入 localStorage。 |
| expiresIn | number | 是 | accessToken 的剩余有效期，单位秒，例如 `7200`。 |
| user | object | 是 | 当前登录身份对象。 |
| user.username | string | 是 | 用户昵称。若用户未设置昵称则回退为邮箱，例如 `XiaoNiu` / `user@example.com`。 |
| user.email | string | 是 | 用户邮箱，例如 `user@example.com`。 |

**所需权限**

无需登录。

**其他说明**

- accessToken 与 refreshToken 的签名密钥来自环境变量 `CHAT_AUTH_SECRET`。
- 数据库中 refreshToken 以 SHA-256 哈希存储；返回给客户端的明文仅在签发/刷新时出现一次。
- 服务端会清理同一 `deviceId` 下已过期的 refreshToken 记录（通过 `expiresAt` 的 TTL 索引自动过期）。
- 可能返回的错误状态码：`400`（邮箱格式错误 / 密码为空 / `deviceId` 长度不合规）、`401`（邮箱或密码错误）、`500`（服务异常）。

### 刷新 access token

**接口描述**

使用 refresh token 换取新的 access token。为防止 refresh token 泄漏后的重放攻击，每次刷新都会**轮换 refresh token**：旧的 refresh token 立即失效，返回新的 refresh token。

**请求路径**

`POST /api/refresh`

**路径参数**

无。

**请求参数**

| 参数名 | 参数类型 | 是否必需 | 参数说明及示例值 |
| --- | --- | --- | --- |
| refreshToken | string | 是 | 当前有效的 refresh token。 |
| deviceId | string | 是 | 与登录时相同的设备 ID，服务端会校验两者一致。 |

**请求示例**

```json
{
  "refreshToken": "<current-refresh-token>",
  "deviceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**返回示例**

```json
{
  "success": true,
  "accessToken": "<new-jwt>",
  "refreshToken": "<new-refresh-token>",
  "expiresIn": 7200,
  "user": {
    "username": "XiaoNiu",
    "email": "user@example.com"
  }
}
```

**返回参数**

| 参数名 | 参数类型 | 是否必需 | 参数说明及示例值 |
| --- | --- | --- | --- |
| success | boolean | 是 | 是否刷新成功。 |
| accessToken | string | 是 | 新的 access token。 |
| refreshToken | string | 是 | 新的 refresh token。**旧的 refresh token 立即失效**，客户端必须替换本地存储的值。 |
| expiresIn | number | 是 | 新 access token 的剩余有效期，单位秒。 |
| user | object | 是 | 刷新时刻的用户身份对象（滑动续期时返回，便于前端检测昵称变更）。 |

**所需权限**

无需 access token（使用 refresh token 鉴权）。

**其他说明**

- **Token Rotation**：每次刷新都会销毁旧 refresh token、签发新 refresh token。客户端必须使用最新返回的 refresh token。
- **滑动续期**：refresh token 的 `expiresAt` 会被重置为当前时间 + `30` 天（只要持续刷新就长期有效）。
- **过期处理**：若 refresh token 已过期（超过 `30` 天未刷新），用户必须重新走 `/api/login-v2`。
- 可能返回的错误状态码：
  - `400`（`refreshToken` 或 `deviceId` 缺失/不合规）
  - `401` + `code: invalid_token`（refresh token 不存在 / 已过期 / 已被轮换 / 设备不匹配）
  - `403` + `code: user_blacklisted`（用户已被加入黑名单，refresh token 记录会同步删除）

### v2 登出

**接口描述**

吊销当前 refresh token，使其立即失效。常用于用户主动登出或客户端检测到 refresh token 泄漏时。

**请求路径**

`POST /api/logout-v2`

**请求头**

| Header | 是否必需 | 说明 |
| --- | --- | --- |
| `Authorization: Bearer <accessToken>` | 是 | 当前有效的 access token。 |

**请求参数**

| 参数名 | 参数类型 | 是否必需 | 参数说明及示例值 |
| --- | --- | --- | --- |
| refreshToken | string | 是 | 要吊销的 refresh token。 |

**请求示例**

```http
POST /api/logout-v2 HTTP/1.1
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "refreshToken": "<current-refresh-token>"
}
```

**返回示例**

```json
{
  "success": true,
  "message": "已登出"
}
```

**返回参数**

| 参数名 | 参数类型 | 是否必需 | 参数说明及示例值 |
| --- | --- | --- | --- |
| success | boolean | 是 | 是否登出成功。 |
| message | string | 是 | 固定为 `已登出`。 |

**所需权限**

需要有效的 v2 access token。

**其他说明**

- access token 本身不支持服务端吊销表（无状态 JWT），登出仅清除 refresh token。客户端必须同时**删除本地存储的 accessToken 和 refreshToken**，否则 accessToken 在剩余 `2` 小时内仍然有效。
- 若用户在另一设备再次调用 `/api/refresh`，会因为 refresh token 不存在而失败，触发重新登录。
- 可能返回的错误状态码：
  - `400`（`refreshToken` 缺失）
  - `401` + `code: token_missing`（未提供 access token）
  - `401` + `code: token_expired`（access token 已过期）
  - `401` + `code: invalid_token`（access token 无效）
  - `403`（用户已被加入黑名单）

### 获取当前用户资料

**接口描述**

获取当前登录用户的资料、所属用户组和权限列表。

**请求路径**

`GET /api/user/profile`

**路径参数**

无。

**请求参数**

无。

**返回示例**

```json
{
  "user": {
    "email": "user@example.com",
    "nickname": "User",
    "avatarUrl": "/api/files/68207a7105d9d5a1bc3aa7a5",
    "avatarFileId": "68207a7105d9d5a1bc3aa7a5",
    "bio": "个人简介",
    "createdAt": "2026-01-01T00:00:00.000Z",
    "hasApiKey": true,
    "apiKeyPreview": "xntk_r4Ld...Z8pQ",
    "apiKeyCreatedAt": "2026-06-16T08:00:00.000Z",
    "groups": [
      {
        "id": "681f7f0ab2a2e8d7d4f69301",
        "key": "user",
        "name": "普通用户",
        "isSystem": true
      }
    ],
    "permissions": [
      "blog:view",
      "blog:read",
      "blog:write"
    ]
  }
}
```

**返回参数**

| 参数名 | 参数类型 | 是否必需 | 参数说明及示例值 |
| --- | --- | --- | --- |
| user | object | 是 | 当前用户资料对象。 |
| user.email | string | 是 | 用户邮箱，例如 `user@example.com`。 |
| user.nickname | string | 是 | 当前昵称，例如 `User`。 |
| user.avatarUrl | string | 是 | 头像访问地址，无头像时为空字符串。 |
| user.avatarFileId | string\|null | 是 | 头像文件 ID，没有头像时为 `null`。 |
| user.bio | string | 是 | 个人简介，最多 200 字。 |
| user.createdAt | string | 是 | 账号创建时间，ISO 字符串。 |
| user.hasApiKey | boolean | 是 | 当前是否已生成个人 API Key。 |
| user.apiKeyPreview | string | 是 | API Key 脱敏预览，仅用于识别当前生效的 Key。 |
| user.apiKeyCreatedAt | string\|null | 是 | API Key 生成时间，未生成时为 `null`。 |
| user.groups | array<object> | 是 | 当前所属用户组数组，每项包含 `id`、`key`、`name`、`isSystem`。 |
| user.permissions | array<string> | 是 | 当前账号拥有的权限列表。 |

**所需权限**

需要登录。

**其他说明**

可能返回的错误状态码：`401`（未登录或令牌失效）、`403`（账号在黑名单中）、`404`（用户不存在）。

### 更新当前用户资料

**接口描述**

更新昵称、头像、简介，或修改密码。所有字段均为可选字段。

**请求路径**

`PUT /api/user/profile`

**路径参数**

无。

**请求参数**

| 参数名 | 参数类型 | 是否必需 | 参数说明及示例值 |
| --- | --- | --- | --- |
| nickname | string | 否 | 新昵称，例如 `XiaoNiu`。长度需在 `1` 到 `32` 个字符之间，只允许中文、英文和数字。 |
| avatarFileId | string\|null | 否 | 新头像文件 ID；传 `null` 或空字符串可清空头像。 |
| bio | string | 否 | 新个人简介，服务端会裁剪到最多 `200` 个字符。 |
| currentPassword | string | 否 | 当前密码；仅在修改密码时需要。 |
| newPassword | string | 否 | 新密码，长度需在 `8` 到 `128` 个字符之间。 |

**请求示例**

```json
{
  "nickname": "XiaoNiu",
  "avatarFileId": "68207a7105d9d5a1bc3aa7a5",
  "bio": "全栈开发者",
  "currentPassword": "12345678",
  "newPassword": "better-password-123"
}
```

**返回示例**

```json
{
  "user": {
    "email": "user@example.com",
    "nickname": "XiaoNiu",
    "avatarUrl": "/api/files/68207a7105d9d5a1bc3aa7a5",
    "avatarFileId": "68207a7105d9d5a1bc3aa7a5",
    "bio": "全栈开发者",
    "createdAt": "2026-01-01T00:00:00.000Z",
    "hasApiKey": true,
    "apiKeyPreview": "xntk_r4Ld...Z8pQ",
    "apiKeyCreatedAt": "2026-06-16T08:00:00.000Z",
    "groups": [
      {
        "id": "681f7f0ab2a2e8d7d4f69301",
        "key": "user",
        "name": "普通用户",
        "isSystem": true
      }
    ],
    "permissions": [
      "blog:view",
      "blog:read",
      "blog:write"
    ]
  }
}
```

**返回参数**

| 参数名 | 参数类型 | 是否必需 | 参数说明及示例值 |
| --- | --- | --- | --- |
| user | object | 是 | 更新后的用户资料对象，字段与“获取当前用户资料”接口一致。 |

**所需权限**

需要登录。

**其他说明**

- 修改密码时必须同时提供 `currentPassword` 和 `newPassword`，否则会校验失败。
- 系统保留昵称为 `post`、`new`、`edit`、`manage`，不能使用。
- 可能返回的错误状态码：`400`（昵称不合法、头像文件无效、当前密码错误、新密码不合法）、`401`、`403`、`404`、`409`（昵称已被使用或写入冲突）。

### 获取当前用户 API Key 状态

**接口描述**

获取当前登录用户的 API Key 状态。出于安全考虑，该接口不会返回完整 Key，只返回是否存在、脱敏预览和生成时间。

**请求路径**

`GET /api/user/api-key`

**返回示例**

```json
{
  "apiKey": {
    "hasApiKey": true,
    "apiKeyPreview": "xntk_r4Ld...Z8pQ",
    "apiKeyCreatedAt": "2026-06-16T08:00:00.000Z",
    "expiresAt": null
  }
}
```

**返回参数**

| 参数名 | 参数类型 | 是否必需 | 参数说明及示例值 |
| --- | --- | --- | --- |
| apiKey | object | 是 | 当前用户 API Key 状态对象。 |
| apiKey.hasApiKey | boolean | 是 | 是否已生成 API Key。 |
| apiKey.apiKeyPreview | string | 是 | API Key 的脱敏预览。 |
| apiKey.apiKeyCreatedAt | string\|null | 是 | API Key 生成时间，未生成时为 `null`。 |
| apiKey.expiresAt | null | 是 | 到期时间。当前固定为 `null`，表示不过期。 |

**所需权限**

需要登录。

### 生成或重置当前用户 API Key

**接口描述**

生成新的个人 API Key。若用户已有 API Key，则本接口会直接重置，旧 Key 立即失效。完整 Key 只会在本接口响应中返回一次。

**请求路径**

`POST /api/user/api-key`

**返回示例**

```json
{
  "apiKey": {
    "hasApiKey": true,
    "apiKeyPreview": "xntk_r4Ld...Z8pQ",
    "apiKeyCreatedAt": "2026-06-16T08:00:00.000Z",
    "expiresAt": null,
    "value": "xntk_r4LdQwY6DqQwIYj9mTjE3pS2e8dFZ8pQ"
  }
}
```

**返回参数**

除“获取当前用户 API Key 状态”中的字段外，还额外返回：

| 参数名 | 参数类型 | 是否必需 | 参数说明及示例值 |
| --- | --- | --- | --- |
| apiKey.value | string | 是 | 本次新生成的完整 API Key，仅在本次响应中返回。 |

**所需权限**

需要登录。

### 废弃当前用户 API Key

**接口描述**

废弃当前用户的 API Key。废弃后旧 Key 立即失效。

**请求路径**

`DELETE /api/user/api-key`

**返回示例**

```json
{
  "apiKey": {
    "hasApiKey": false,
    "apiKeyPreview": "",
    "apiKeyCreatedAt": null,
    "expiresAt": null
  }
}
```

**所需权限**

需要登录。
