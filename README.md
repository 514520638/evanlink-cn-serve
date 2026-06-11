# EvanLink Backend

Spring Boot 后端服务，提供个人信息、技能栈、文章、简历访问验证和管理员鉴权接口。

## 技术栈

- Java 17
- Spring Boot 3.2
- Spring Web
- Spring Data JPA
- MySQL 8
- Maven

## 本地运行

```bash
mvn spring-boot:run
```

默认端口：

```text
8080
```

打包：

```bash
mvn clean package
```

构建产物：

```text
target/evanlink-cn-serve-1.0.0.jar
```

## 配置

主要配置文件：

```text
src/main/resources/application.yml
```

关键配置：

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://...
    username: ...
    password: ...
  jpa:
    hibernate:
      ddl-auto: update

app:
  admin:
    username: ...
    password: ...
```

管理员账号密码用于 `/admin` 前端后台登录。后端会签发 12 小时有效的 HMAC token，文章写入和资料维护接口都需要携带：

```http
Authorization: Bearer <admin_token>
```

## API

### 公开接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/user_info` | 获取个人信息，并记录访客 |
| `GET` | `/api/skills` | 获取分组后的技能栈 |
| `GET` | `/api/articles` | 分页查询文章 |
| `GET` | `/api/articles/{slug}` | 获取文章详情 |
| `GET` | `/api/articles/filters` | 获取文章分类和标签筛选项 |
| `POST` | `/api/articles/{slug}/view` | 增加文章浏览量 |
| `POST` | `/api/resume/verify` | 验证简历访问并记录申请 |

### 管理员接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/admin/login` | 管理员登录 |
| `GET` | `/api/admin/profile` | 获取后台资料和技能列表 |
| `PUT` | `/api/admin/profile` | 更新个人信息和技能栈 |
| `POST` | `/api/articles` | 新增文章，需要 token |
| `PUT` | `/api/articles/{id}` | 更新文章，需要 token |
| `DELETE` | `/api/articles/{id}` | 删除文章，需要 token |

## 数据表

当前由 JPA 实体维护表结构，`ddl-auto: update` 会自动补齐字段。

| 表 | 说明 |
| --- | --- |
| `user_info` | 个人信息、头像、微信、邮箱、简历链接、访客数 |
| `skills` | 技能栈 |
| `article` | 文章 |
| `article_category` | 文章分类 |
| `tag` | 标签 |
| `article_tag` | 文章和标签关联 |
| `resume_record` | 简历访问申请记录 |
| `client_history_list` | 访客 IP 记录 |

## 线上部署

当前线上后端运行在 Docker 容器中。

已知线上结构：

```text
容器名: evanlink-backend
镜像: eclipse-temurin:17-jre
宿主机 jar: /opt/evanlink-backend/app.jar
容器内 jar: /app/app.jar
端口映射: 127.0.0.1:8080 -> 8080
MySQL 容器: evanlink-mysql
```

相册上传文件默认保存在后端工作目录的 `uploads/album` 下。线上 Docker 建议把宿主机目录挂载到容器内工作目录，例如：

```text
宿主机: /opt/evanlink-backend/uploads
容器内: /app/uploads
```

如果本地后端直接连接线上 MySQL，并希望本地上传的相册图片/视频同步到服务器，需要配置镜像上传。服务器后端配置接收密钥：

```bash
APP_UPLOAD_MIRROR_SECRET=替换成强随机密钥
```

本地后端配置同一个密钥和服务器镜像接口：

```bash
APP_UPLOAD_MIRROR_URL=https://evanlink.cn/api/album/photos/mirror
APP_UPLOAD_MIRROR_DELETE_URL=https://evanlink.cn/api/album/photos/mirror/delete
APP_UPLOAD_MIRROR_SECRET=替换成同一个强随机密钥
```

镜像接口只写入或删除服务器本地文件，不新增数据库记录，避免本地上传时出现重复相册记录。

### 1. 本地构建

```bash
cd /Users/xdf/Desktop/project/evanlink-cn-serve
mvn clean package
```

### 2. 上传 jar

```bash
scp target/evanlink-cn-serve-1.0.0.jar root@62.234.72.18:/opt/evanlink-backend/app.jar
```

### 3. 重启后端容器

```bash
ssh root@62.234.72.18 "docker restart evanlink-backend"
```

### 4. 查看日志

```bash
ssh root@62.234.72.18 "docker logs --tail=120 evanlink-backend"
```

持续查看：

```bash
ssh root@62.234.72.18 "docker logs -f evanlink-backend"
```

## 服务器排查

查看正在运行的容器：

```bash
docker ps
```

查看后端端口：

```bash
lsof -i :8080
```

查看容器挂载路径：

```bash
docker inspect evanlink-backend | grep -E "Source|Destination|/app|app.jar"
```

本机验证后端：

```bash
curl -i http://127.0.0.1:8080/api/user_info
```

线上域名验证：

```bash
curl -i https://evanlink.cn/api/user_info
```

## 数据库排查

进入 MySQL 容器：

```bash
docker exec -it evanlink-mysql mysql -u root -p
```

常用 SQL：

```sql
USE evanlink_cn;
SHOW TABLES;
SELECT id, name, email, resume_url FROM user_info;
SELECT id, title, slug, status FROM article ORDER BY created_at DESC LIMIT 10;
SELECT * FROM resume_record ORDER BY time_apply DESC LIMIT 20;
```
