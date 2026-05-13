# CVA 商城项目 — 环境配置手册

## 一、前置依赖

| 组件 | 版本 | 用途 |
|------|------|------|
| JDK | 17+ | 编译运行 |
| MySQL | 8.0+ | 数据持久化 |
| Redis | 7.0+ | Token 黑名单、限流、验证码存储 |
| Maven | 3.8+ | 构建（或使用项目自带 mvnw） |

---

## 二、数据库配置

### 1. 创建数据库

启动 MySQL 后，执行项目中的初始化脚本：

```bash
mysql -u root -p < src/main/resources/sql/schema.sql
```

脚本会自动创建 `mall_db` 库（如不存在）及全部表结构和测试数据。

### 2. 测试数据

| 用户名 | 密码 | 角色 | 状态 |
|--------|------|------|------|
| admin | 123456 | SUPER_ADMIN | 已启用，邮箱已验证 |
| merchant | 123456 | MERCHANT | 已启用，邮箱已验证 |
| user | 123456 | USER | 已启用，邮箱已验证 |

### 3. 修改连接信息

若非本地默认安装（端口 3306、用户 root、密码 root），修改 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://your_host:3306/mall_db?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
    username: your_username
    password: your_password
```

---

## 三、Redis 配置

### 1. 启动 Redis

```bash
# Linux/Mac
redis-server

# Windows
redis-server.exe
```

### 2. 修改连接信息（如需）

```yaml
spring:
  data:
    redis:
      host: your_redis_host
      port: 6379
      password: your_redis_password   # 无密码则留空
      database: 0
```

Redis 用于三项功能，**不启动 Redis 将导致：**

- 登出（Token 黑名单）不可用
- 认证接口限流失效 — 不阻止请求，但无保护
- 邮箱验证码不可用

---

## 四、邮箱 SMTP 配置

邮箱验证码功能依赖 SMTP 发送邮件。以下以 QQ 邮箱为例。

### 1. 获取 QQ 邮箱授权码

1. 登录 [QQ 邮箱](https://mail.qq.com)
2. 进入 **设置 → 账户**
3. 找到 **POP3/IMAP/SMTP 服务**
4. 点击 **开启服务**（SMTP 一行）
5. 按提示发送短信验证，获得 16 位授权码

### 2. 配置环境变量

```bash
# Linux / Mac
export MAIL_USERNAME=your_email@qq.com
export MAIL_PASSWORD=你的16位授权码

# Windows (CMD)
set MAIL_USERNAME=your_email@qq.com
set MAIL_PASSWORD=你的16位授权码

# Windows (PowerShell)
$env:MAIL_USERNAME="your_email@qq.com"
$env:MAIL_PASSWORD="你的16位授权码"
```

### 3. 修改 application.yml

将邮件配置段改为：

```yaml
spring:
  mail:
    host: smtp.qq.com
    port: 587
    username: ${MAIL_USERNAME}
    password: ${MAIL_PASSWORD}
    properties:
      mail:
        smtp:
          auth: true
          starttls:
            enable: true
          connectiontimeout: 5000
          timeout: 5000
          writetimeout: 5000
```

> **其他邮箱 SMTP 参数：**
>
> | 邮箱 | host | port |
> |------|------|------|
> | QQ | smtp.qq.com | 587 |
> | 163 | smtp.163.com | 465 |
> | Gmail | smtp.gmail.com | 587 |
> | Outlook | smtp-mail.outlook.com | 587 |

---

## 五、JWT 密钥配置

生产环境**务必更换密钥**，切勿使用默认值。

```yaml
jwt:
  secret: 你的256位随机密钥（至少256位）
  expiration: 86400000    # Token 有效期，毫秒，默认 24h
```

生成随机密钥：

```bash
# Linux/Mac
openssl rand -hex 32

# Windows PowerShell
[System.Convert]::ToHexString((New-Object System.Security.Cryptography.HMACSHA256).Key)
```

---

## 六、启动项目

```bash
# 编译 + 测试
./mvnw clean test

# 启动
./mvnw spring-boot:run

# 或打包运行
./mvnw clean package -DskipTests
java -jar target/cva-0.0.1-SNAPSHOT.jar
```

默认端口 **8080**，启动后访问：`http://localhost:8080`

---

## 七、API 快速测试

### 注册

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456","email":"2421840631@qq.com"}'
```

### 发送注册验证码

```bash
curl -X POST http://localhost:8080/api/auth/send-code \
  -H "Content-Type: application/json" \
  -d '{"email":"2421840631@qq.com","type":"register"}'
```

### 验证邮箱（激活账户）

```bash
curl -X POST http://localhost:8080/api/auth/verify-email \
  -H "Content-Type: application/json" \
  -d '{"email":"2421840631@qq.com","code":"收到的6位验证码"}'
```

### 密码登录

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}'
```

返回示例：

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

### 携带 Token 访问

```bash
curl http://localhost:8080/api/user/profile \
  -H "Authorization: Bearer eyJhbGciOiJIUz..."
```

---

## 八、常见问题

**Q: 启动报错 "Failed to determine a suitable driver class"**
→ MySQL 未启动或连接信息配置错误

**Q: 登录返回 "账户已被禁用"**
→ 注册后未验证邮箱，调用 `/api/auth/verify-email` 激活

**Q: 邮件发送失败**
→ 检查 SMTP 授权码是否正确，QQ 邮箱需用授权码而非登录密码

**Q: 验证码收不到**
→ 检查垃圾邮件箱；确认 Redis 已启动（验证码存在 Redis 中）
