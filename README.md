# Evanlink CN Serve

个人博客后端服务，基于 Spring Boot 构建。

## 技术栈

- **Spring Boot 3.2.0**
- **Spring Data JPA**
- **MySQL**
- **Java 17+**

## 项目结构

```
src/main/java/com/evanlink/
├── EvanlinkCnServeApplication.java    # 应用入口
├── config/                             # 配置类
│   ├── CorsConfig.java                # 跨域配置
│   └── DataInitializer.java           # 数据初始化
├── controller/                         # 控制器
│   └── UserInfoController.java        # 用户信息接口
├── model/                             # 实体类
│   ├── UserInfo.java                  # 用户信息
│   └── Skill.java                     # 技能
├── repository/                         # 数据访问层
│   ├── UserInfoRepository.java
│   └── SkillRepository.java
└── service/                           # 业务逻辑层
    └── UserInfoService.java
```

## API 接口

| 方法 | 路径 | 描述 |
|------|------|------|
| GET | `/api/about` | 获取用户信息 |
| POST | `/api/about` | 保存用户信息 |

## 数据库配置

在 `src/main/resources/application.yml` 中配置 MySQL 连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/evanlink_cn
    username: root
    password: 
```

## 快速开始

1. 创建数据库：
```sql
CREATE DATABASE evanlink_cn CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

2. 启动应用：
```bash
./mvnw spring-boot:run
# 或
mvn spring-boot:run
```

3. 访问 API：`http://localhost:8080/api/about`

## 数据库表

- **user_info** - 用户个人信息表
- **skills** - 技能表（独立于 user_info）
