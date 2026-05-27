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
| GET | `/api/user_info` | 获取用户信息 |
| POST | `/api/user_info` | 保存用户信息 |
| GET | `/api/skills` | 获取分组后的技能列表 |
| POST | `/api/skills` | 保存单个技能 |
| DELETE | `/api/skills/{id}` | 删除技能 |
| POST | `/api/skills/reinitialize` | 重置技能默认数据 |
| POST | `/api/resume/verify` | 简历访问验证，并记录申请信息 |

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

3. 访问 API：`http://localhost:8080/api/user_info`

## 数据库表

- **user_info** - 用户个人信息表
- **skills** - 技能表（独立于 user_info）
- **resume_record** - 简历访问申请记录表
- **client_history_list** - 访客 IP 记录表

如果旧库里表名误写为 `resume_recode`，需要改为 `resume_record`：

```sql
RENAME TABLE resume_recode TO resume_record;
```

## 部署到服务器

当前线上后端使用 Docker 运行：

- 后端容器：`evanlink-backend`
- MySQL 容器：`evanlink-mysql`
- 后端镜像：`eclipse-temurin:17-jre`
- 后端 jar 宿主机路径：`/opt/evanlink-backend/app.jar`
- 后端 jar 容器内路径：`/app/app.jar`
- 端口映射：`127.0.0.1:8080 -> 8080`
- Docker 网络：`evanlink-net`

### 1. 本地构建后端 jar

```bash
cd ~/Desktop/project/evanlink-cn-serve
mvn clean package -DskipTests
```

构建产物：

```text
target/evanlink-cn-serve-1.0.0.jar
```

### 2. 上传 jar 到服务器

```bash
scp target/evanlink-cn-serve-1.0.0.jar root@服务器IP:/root/app.jar
```

### 3. 替换宿主机 jar

服务器执行：

```bash
cp /root/app.jar /opt/evanlink-backend/app.jar
```

说明：`/app/app.jar` 是容器内 bind mount，来源是宿主机 `/opt/evanlink-backend/app.jar`。不要用 `docker cp` 覆盖 `/app/app.jar`，可能出现 `device or resource busy`。

### 4. 创建或重建后端容器

如果只是 jar 更新，优先重启容器：

```bash
docker restart evanlink-backend
```

如果旧容器启动失败，或者旧 `--link` 关系异常，可以删除后重建：

```bash
docker rm -f evanlink-backend
docker network create evanlink-net
docker network connect evanlink-net evanlink-mysql
```

如果网络已存在或 MySQL 已经连接到网络，报错可忽略。

重新创建后端容器：

```bash
docker run -d \
  --name evanlink-backend \
  --network evanlink-net \
  --restart unless-stopped \
  -p 127.0.0.1:8080:8080 \
  -v /opt/evanlink-backend/app.jar:/app/app.jar \
  -e SERVER_PORT=8080 \
  -e "SPRING_DATASOURCE_URL=jdbc:mysql://evanlink-mysql:3306/evanlink_cn?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true" \
  -e SPRING_DATASOURCE_USERNAME=evanlink \
  -e SPRING_DATASOURCE_PASSWORD=替换成数据库密码 \
  -e RESUME_VERIFY_PASSWORD=替换成简历访问密码 \
  -e RESUME_URL=https://www.qmjianli.com/cvs/251112EQRYWGY0GB \
  eclipse-temurin:17-jre \
  java -jar /app/app.jar
```

注意：`SPRING_DATASOURCE_URL` 必须保持一行，不能在 `?` 后换行，否则 MySQL 驱动会报 `Driver com.mysql.cj.jdbc.Driver claims to not accept jdbcUrl`。

### 5. 检查运行状态

```bash
docker ps | grep evanlink
docker logs --tail=120 evanlink-backend
curl -i http://127.0.0.1:8080/api/user_info
```

线上域名验证：

```bash
curl -i https://evanlink.cn/api/user_info
```

### 6. 数据库维护

进入 MySQL 容器：

```bash
docker exec -it evanlink-mysql mysql -u root -p
```

常用 SQL：

```sql
USE evanlink_cn;
SHOW TABLES;
SELECT id, name, resume_url FROM user_info;
UPDATE user_info
SET resume_url = 'https://www.qmjianli.com/cvs/251112EQRYWGY0GB'
WHERE id = 1;
```

如果需要确认简历访问记录表：

```sql
SHOW TABLES LIKE 'resume_record';
SELECT * FROM resume_record ORDER BY time_apply DESC LIMIT 20;
```
