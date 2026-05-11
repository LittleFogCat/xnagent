# 认证与资料接口

所有接口路径前缀均为 `/api`。

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
