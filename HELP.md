# CVA 商城项目

## 技术栈
- Spring Boot 3.2.8
- Spring Security + JWT 认证
- MyBatis 3.0.4 + MySQL
- Redis (Lettuce)
- Java 17

## 快速启动

1. 创建数据库并导入表结构：
```sql
source src/main/resources/sql/schema.sql
```

2. 配置 `application.yml` 中的数据库和 Redis 连接信息。

3. 启动项目：
```bash
./mvnw spring-boot:run
```

## 接口说明
- `/api/auth/**` — 认证相关（登录、注册），无需 Token
- 其他接口需在请求头携带 `Authorization: Bearer <token>`
