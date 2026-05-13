# CVA 商城 API 文档

## 概述

CVA 商城后端基于 Spring Boot 3.2.8 构建，采用 JWT 无状态认证 + RBAC 权限模型。所有接口返回统一的 JSON 响应体。

### 技术栈

| 组件 | 版本/方案 |
|------|-----------|
| 框架 | Spring Boot 3.2.8 |
| 安全 | Spring Security + JWT (jjwt 0.12.6) |
| ORM | MyBatis 3.0.4 |
| 数据库 | MySQL 8.0+ |
| 缓存 | Redis 7.0+ (Lettuce) |
| 密码加密 | BCrypt |
| 邮件 | Spring Mail (SMTP) |

### 基础信息

- **Base URL**: `http://localhost:8080`
- **Content-Type**: `application/json`
- **字符编码**: UTF-8

---

## 一、通用规范

### 1.1 统一响应格式

所有接口返回的 JSON 结构如下：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | HTTP 语义状态码 |
| message | string | 提示信息 |
| data | any | 业务数据载荷，失败时为 null |

### 1.2 通用状态码

| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未认证或 Token 无效 |
| 403 | 权限不足 / 账户被禁用 |
| 404 | 资源不存在 |
| 409 | 数据冲突（如重复注册） |
| 422 | 请求体验证失败 |
| 423 | 账户被锁定 |
| 429 | 请求过于频繁 |
| 500 | 服务器内部错误 |

### 1.3 认证方式

除 `/api/auth/**` 外的所有接口需在请求头携带 JWT：

```
Authorization: Bearer <token>
```

Token 有效期默认 24 小时（可通过 `jwt.expiration` 配置调整）。

### 1.4 限流策略

`/api/auth/**` 路径每 IP 每 60 秒最多 10 次请求，超出返回 429。

### 1.5 角色体系

| 角色代码 | 说明 | 权限 |
|----------|------|------|
| SUPER_ADMIN | 超级管理员 | 全部权限 |
| MERCHANT | 商家 | 商品和订单管理 |
| USER | 普通用户 | 浏览和购买 |

---

## 二、认证接口 `/api/auth/**`

> 所有接口公开访问，无需 Token。

---

### 2.1 密码登录

```
POST /api/auth/login
```

**请求体**

```json
{
  "username": "admin",
  "password": "123456"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 是 | 登录用户名 |
| password | string | 是 | 登录密码 |

**成功响应** (200)

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUz...",
    "userId": 1,
    "username": "admin",
    "email": "admin@mall.com",
    "roles": ["SUPER_ADMIN"]
  }
}
```

**错误响应**

| code | message | 触发条件 |
|------|---------|----------|
| 400 | 用户名不能为空 / 密码不能为空 | 参数校验失败 |
| 401 | 用户名或密码错误 | BadCredentialsException |
| 403 | 账户已被禁用 | status=0 |
| 423 | 账户已被锁定，请稍后再试 | 连续 5 次登录失败后锁定 30 分钟 |

**安全机制**

- 连续 5 次密码错误自动锁定账户 30 分钟
- 登录成功自动解除锁定并重置失败计数

---

### 2.2 用户注册

```
POST /api/auth/register
```

**请求体**

```json
{
  "username": "newuser",
  "password": "123456",
  "email": "user@example.com",
  "phone": "13800001111"
}
```

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|----------|
| username | string | 是 | 2-50 个字符 |
| password | string | 是 | 6-100 个字符 |
| email | string | 否 | 邮箱格式 |
| phone | string | 否 | 中国大陆手机号 (1[3-9]\\d{9}) |

**成功响应** (200)

```json
{
  "code": 200,
  "message": "success",
  "data": "注册成功"
}
```

**错误响应**

| code | message | 触发条件 |
|------|---------|----------|
| 409 | 用户名已被占用 | 用户名重复 |
| 409 | 邮箱已被注册 | 邮箱重复 |
| 409 | 手机号已被注册 | 手机号重复 |

> **注意**: 注册后用户 status=0，需调用验证邮箱接口激活账户后才能登录。

---

### 2.3 发送验证码

```
POST /api/auth/send-code
```

**请求体**

```json
{
  "email": "user@example.com",
  "type": "register"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | string | 是 | 接收验证码的邮箱 |
| type | string | 是 | `register`（注册验证码）或 `login`（登录验证码） |

**成功响应** (200)

```json
{
  "code": 200,
  "message": "success",
  "data": "验证码已发送"
}
```

**错误响应**

| code | message | 触发条件 |
|------|---------|----------|
| 400 | type 只能为 login 或 register | type 取值非法 |
| 404 | 该邮箱未注册 | type=login 但邮箱未注册 |
| 409 | 该邮箱已被注册 | type=register 但邮箱已注册 |
| 429 | 验证码发送过于频繁，请 60 秒后再试 | 同一邮箱同一类型 60 秒内重复发送 |

> **注意**: 验证码为 6 位数字，有效期 5 分钟，存储在 Redis 中。

---

### 2.4 验证码登录

```
POST /api/auth/login-by-code
```

**请求体**

```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | string | 是 | 已注册的邮箱 |
| code | string | 是 | 邮箱收到的 6 位验证码 |

**成功响应** (200)

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUz...",
    "userId": 3,
    "username": "user",
    "email": "user@mall.com",
    "roles": ["USER"]
  }
}
```

**错误响应**

| code | message | 触发条件 |
|------|---------|----------|
| 401 | 验证码错误或已过期 | 验证码不匹配或超过 5 分钟 |
| 403 | 账户未激活，请先完成邮箱验证 | 邮箱未验证 |
| 404 | 该邮箱未注册 | 邮箱不存在 |

---

### 2.5 验证邮箱（激活账户）

```
POST /api/auth/verify-email
```

注册后调用此接口激活账户。

**请求体**

```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| email | string | 是 | 注册时使用的邮箱 |
| code | string | 是 | 注册验证码（type=register 时收到的） |

**成功响应** (200)

```json
{
  "code": 200,
  "message": "success",
  "data": "邮箱验证成功，账户已激活"
}
```

**错误响应**

| code | message | 触发条件 |
|------|---------|----------|
| 400 | 验证码错误或已过期 | 验证码不匹配或超过 5 分钟 |

---

### 2.6 登出

```
POST /api/auth/logout
```

**请求头**

```
Authorization: Bearer <当前有效的Token>
```

**成功响应** (200)

```json
{
  "code": 200,
  "message": "success",
  "data": "已登出"
}
```

> **机制**: Token 被加入 Redis 黑名单，TTL 等于 Token 剩余有效时间，到期自动清理。

---

### 2.7 刷新 Token

```
POST /api/auth/refresh
```

**请求头**

```
Authorization: Bearer <当前有效的Token>
```

**成功响应** (200)

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUz...",
    "userId": 1,
    "username": "admin",
    "email": "admin@mall.com",
    "roles": null
  }
}
```

> **注意**:
> - 旧 Token 立即加入黑名单失效
> - 刷新接口返回的 `roles` 为 null（前端应在登录时缓存角色信息）
> - 需旧 Token 有效且未被黑名单

**错误响应**

| code | message | 触发条件 |
|------|---------|----------|
| 401 | 未提供有效的 Token | Authorization 头缺失 |
| 401 | Token 无效或已过期 | Token 已过期或已被黑名单 |

---

## 三、用户接口 `/api/user/**`

> 需要认证，在请求头携带 `Authorization: Bearer <token>`。

---

### 3.1 获取个人信息

```
GET /api/user/profile
```

**成功响应** (200)

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "admin",
    "email": "admin@mall.com",
    "phone": "13800000001",
    "status": 1,
    "createdAt": "2026-05-13T12:00:00",
    "updatedAt": "2026-05-13T12:00:00"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 用户 ID |
| username | string | 用户名 |
| email | string | 邮箱 |
| phone | string | 手机号 |
| status | int | 账户状态：0-禁用, 1-启用 |
| createdAt | string | 注册时间 (ISO 8601) |
| updatedAt | string | 最后修改时间 (ISO 8601) |

**错误响应**

| code | message | 触发条件 |
|------|---------|----------|
| 401 | 认证失败 | Token 无效或缺失 |
| 404 | 用户不存在 | 用户已被管理员删除 |

---

### 3.2 修改个人信息

```
PUT /api/user/profile
```

**请求体** (所有字段可选，仅更新传入的非空字段)

```json
{
  "email": "newemail@example.com",
  "phone": "13900001111"
}
```

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|----------|
| email | string | 否 | 邮箱格式 |
| phone | string | 否 | 中国大陆手机号 |

**成功响应** (200)

```json
{
  "code": 200,
  "message": "success",
  "data": "修改成功"
}
```

**错误响应**

| code | message | 触发条件 |
|------|---------|----------|
| 409 | 邮箱已被其他用户使用 | 邮箱与已有用户冲突 |
| 409 | 手机号已被其他用户使用 | 手机号与已有用户冲突 |

---

### 3.3 修改密码

```
PUT /api/user/password
```

**请求体**

```json
{
  "oldPassword": "123456",
  "newPassword": "654321"
}
```

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|----------|
| oldPassword | string | 是 | 当前密码，用于身份验证 |
| newPassword | string | 是 | 新密码，6-100 个字符 |

**成功响应** (200)

```json
{
  "code": 200,
  "message": "success",
  "data": "密码修改成功"
}
```

**错误响应**

| code | message | 触发条件 |
|------|---------|----------|
| 400 | 原密码不正确 | 旧密码验证失败 |
| 404 | 用户不存在 | 用户已被删除 |

---

## 四、管理接口 `/api/admin/**`

> 需要 SUPER_ADMIN 角色，在请求头携带 `Authorization: Bearer <token>`。

---

### 4.1 获取用户列表

```
GET /api/admin/users
```

**权限**: SUPER_ADMIN

**成功响应** (200)

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "username": "admin",
      "email": "admin@mall.com",
      "phone": "13800000001",
      "status": 1,
      "createdAt": "2026-05-13T12:00:00",
      "updatedAt": "2026-05-13T12:00:00"
    },
    {
      "id": 2,
      "username": "merchant",
      "email": "shop@mall.com",
      "phone": "13800000002",
      "status": 1,
      "createdAt": "2026-05-13T12:00:00",
      "updatedAt": "2026-05-13T12:00:00"
    }
  ]
}
```

> 返回的用户列表不含密码字段。

---

### 4.2 启用/禁用用户

```
PUT /api/admin/user/{id}/status
```

**权限**: SUPER_ADMIN

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | long | 目标用户 ID |

**请求体**

```json
{
  "status": 0
}
```

| 字段 | 类型 | 必填 | 校验规则 |
|------|------|------|----------|
| status | int | 是 | 0-禁用, 1-启用 |

**成功响应** (200)

```json
{
  "code": 200,
  "message": "success",
  "data": "状态更新成功"
}
```

**错误响应**

| code | message | 触发条件 |
|------|---------|----------|
| 403 | 权限不足 | 非 SUPER_ADMIN 角色 |
| 404 | 用户不存在 | 目标用户 ID 不存在 |

---

### 4.3 删除用户

```
DELETE /api/admin/user/{id}
```

**权限**: SUPER_ADMIN

**路径参数**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | long | 目标用户 ID |

**成功响应** (200)

```json
{
  "code": 200,
  "message": "success",
  "data": "删除成功"
}
```

**错误响应**

| code | message | 触发条件 |
|------|---------|----------|
| 400 | 不能删除自己的账户 | 管理员尝试删除自己 |
| 403 | 权限不足 | 非 SUPER_ADMIN 角色 |
| 404 | 用户不存在 | 目标用户不存在 |

---

## 五、认证流程

### 5.1 密码登录流程

```
Client                          Server
  |                                |
  |-- POST /api/auth/login ------->|
  |   {username, password}         |
  |                                |-- AuthenticationManager.authenticate()
  |                                |-- UserDetailsService.loadUserByUsername()
  |                                |-- BCrypt 密码校验
  |                                |-- 生成 JWT Token
  |                                |-- recordLoginSuccess() 重置失败计数
  |<------- 200 {token, ...} ------|
  |                                |
  |-- 后续请求携带 Authorization -->|
  |   Bearer <token>               |
  |                                |-- JwtAuthenticationFilter 校验 Token
  |                                |-- 从 Token 恢复 SecurityContext
  |<------- 200 / 403 / 401 -------|
```

### 5.2 注册验证码流程

```
  Client                          Server
    |                                |
    |-- POST /api/auth/register ---->|
    |   {username, password, email}   |
    |                                |-- 创建用户 (status=0, email_verified=0)
    |<------- 200 "注册成功" --------|
    |                                |
    |-- POST /api/auth/send-code --->|
    |   {email, type:"register"}     |
    |                                |-- 生成 6 位验证码存入 Redis (5min TTL)
    |                                |-- 发送验证码邮件
    |<------- 200 "验证码已发送" ----|
    |                                |
    |-- POST /api/auth/verify-email >|
    |   {email, code}                |
    |                                |-- 校验验证码
    |                                |-- 更新 email_verified=1, status=1
    |<-- 200 "邮箱验证成功" ---------|
    |                                |
    |-- 用户现在可以正常登录 --------->|
```

### 5.3 验证码登录流程

```
  Client                         Server
    |                               |
    |-- POST /api/auth/send-code -->|
    |   {email, type:"login"}       |
    |                               |-- 校验邮箱已注册
    |                               |-- 生成验证码存入 Redis (5min TTL)
    |                               |-- 发送验证码邮件
    |<------ 200 "验证码已发送" ----|
    |                               |
    |-- POST /api/auth/login-by-code>|
    |   {email, code}                |
    |                               |-- 校验验证码
    |                               |-- 签发 JWT Token
    |<------ 200 {token, ...} ------|
```

---

## 六、安全机制

### 6.1 账户锁定

- 连续 **5 次** 登录失败后自动锁定
- 锁定持续 **30 分钟**，到期自动解除
- 锁定期间返回 HTTP 423

### 6.2 Token 黑名单

- 登出和刷新时将旧 Token 加入 Redis 黑名单
- 黑名单记录 TTL = Token 剩余有效期，到期自动清理
- Redis 键格式: `jwt:blacklist:<token>`

### 6.3 接口限流

- 仅对 `/api/auth/**` 路径限流
- 每 IP **60 秒**内最多 **10 次**请求
- Redis 键格式: `ratelimit:<client_ip>`

### 6.4 密码安全

- 使用 BCrypt 加密存储，不可逆
- 密码字段不出现于任何 API 响应中
- 修改密码需提供旧密码验证

---

## 七、预设测试账户

| 用户名 | 密码 | 角色 | 邮箱 | 状态 |
|--------|------|------|------|------|
| admin | 123456 | SUPER_ADMIN | admin@mall.com | 已启用，邮箱已验证 |
| merchant | 123456 | MERCHANT | shop@mall.com | 已启用，邮箱已验证 |
| user | 123456 | USER | user@mall.com | 已启用，邮箱已验证 |

---

## 八、cURL 测试示例

### 注册

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456","email":"your@email.com"}'
```

### 发送注册验证码

```bash
curl -X POST http://localhost:8080/api/auth/send-code \
  -H "Content-Type: application/json" \
  -d '{"email":"your@email.com","type":"register"}'
```

### 验证邮箱激活

```bash
curl -X POST http://localhost:8080/api/auth/verify-email \
  -H "Content-Type: application/json" \
  -d '{"email":"your@email.com","code":"123456"}'
```

### 密码登录

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
```

### 发送登录验证码

```bash
curl -X POST http://localhost:8080/api/auth/send-code \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@mall.com","type":"login"}'
```

### 验证码登录

```bash
curl -X POST http://localhost:8080/api/auth/login-by-code \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@mall.com","code":"123456"}'
```

### 获取个人信息

```bash
curl http://localhost:8080/api/user/profile \
  -H "Authorization: Bearer eyJhbGciOiJIUz..."
```

### 修改密码

```bash
curl -X PUT http://localhost:8080/api/user/password \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUz..." \
  -d '{"oldPassword":"123456","newPassword":"654321"}'
```

### 刷新 Token

```bash
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Authorization: Bearer eyJhbGciOiJIUz..."
```

### 登出

```bash
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer eyJhbGciOiJIUz..."
```

### 获取用户列表（管理员）

```bash
curl http://localhost:8080/api/admin/users \
  -H "Authorization: Bearer eyJhbGciOiJIUz..."
```

### 启用/禁用用户（管理员）

```bash
curl -X PUT http://localhost:8080/api/admin/user/3/status \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer eyJhbGciOiJIUz..." \
  -d '{"status":0}'
```

### 删除用户（管理员）

```bash
curl -X DELETE http://localhost:8080/api/admin/user/3 \
  -H "Authorization: Bearer eyJhbGciOiJIUz..."
```
