# Java 后端部署模块

接收 GitHub runner 构建的 JAR，更新现有 Java 容器，保留其运行配置及外部数据库。

| 文件 | 职责 |
| --- | --- |
| ci-deploy.sh | 挂载路径校验、共享锁、JAR 备份替换、健康检查和失败回滚 |
| AUTO_DEPLOY.md | 一次性 Secrets、发布条件、服务器约束与回退说明 |

依赖 .github/workflows/deploy.yml（Java 编译验证与 JAR 传输流程）、既有 evanlink-backend 容器。
不管理 MySQL；数据库 schema 更新的限制见 [AUTO_DEPLOY.md](AUTO_DEPLOY.md)。

ci-upload.sh：显示上传进度、检测传输停滞、最多三次断点续传；上传成功后才执行部署。依赖 runner 与服务器的 rsync。
