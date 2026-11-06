# Java 后端自动发布

触发：PR 目标为 main/master 时编译验证；提交或合并进入 main/master 后构建并发布。
工作流为 .github/workflows/deploy.yml。生产部署仅在 push 事件执行，不需要每次请求 Codex 手动发布。

## 一次性配置

本仓库 GitHub Settings → Secrets and variables → Actions 中添加或修改两个 Repository secrets：

| 名称 | 内容 |
| --- | --- |
| DEPLOY_SSH_KEY | 本机相邻 evan-agent/.local/deploy-keys/github-actions 的完整私钥，包含 BEGIN/END |
| DEPLOY_KNOWN_HOSTS | 本机相邻 evan-agent/.local/deploy-keys/known_hosts 的完整内容 |

这些文件已验证可连接 root@62.234.72.18，不需要在聊天中提供。
Secrets 属于各个仓库，前端、Agent 的配置不会自动共享给后端。

## 执行过程

GitHub runner 使用 Java 17 和 Maven clean verify 构建 Spring Boot JAR。
部署任务验证 SSH 私钥格式、known_hosts 中的主机记录，并测试连接，输出 SSH_READY。
app.jar 最多上传 8 分钟，部署最多 20 分钟；会与前端、Agent 共用 /var/lock/evanlink-release.lock。

deploy/ci-deploy.sh 校验容器的 JAR 挂载路径并备份旧文件，然后停止 evanlink-backend，覆盖 JAR 并启动同一容器。
保留容器环境变量、端口、网络、挂载。服务器不运行 Maven，不构建 Java 镜像，不重启 MySQL。
检查 http://127.0.0.1:8080/api/articles/filters 和 https://evanlink.cn/api/articles/filters；失败恢复旧 JAR 并启动容器。
这里检查到数据库查询接口，仍需在部署后验收实际业务功能。

要求现有容器 evanlink-backend 正在运行，且挂载为：

```text
/opt/evanlink-backend/app.jar -> /app/app.jar
```

这与当前服务器核实的配置一致。脚本不创建首次安装所需的数据库和容器。
Java 停止到健康检查通过期间会短暂中断接口；这台 2GB 服务器使用单实例更新。

## 回退和排障

旧版本保存在 /opt/evanlink-backend/backups/ci-SHA-RUN/app.jar。
手动回退时，先停止容器，再把备份复制覆盖 /opt/evanlink-backend/app.jar，最后启动容器。
必须复制覆盖同一个文件，避免使用 mv 替换文件 inode 导致单文件挂载继续使用旧文件。
数据库 schema 不包含在 JAR 备份中；当前 ddl-auto=update 的数据库结构变更不能由 JAR 回滚逆转。
破坏性数据库变更需另行备份和迁移方案。旧 JAR 备份持续保留，需定期按磁盘空间轮换。

Host key verification failed 时，重新复制已验证的 known_hosts 到本仓库 Secret，不能以关闭 StrictHostKeyChecking 解决。
修正 Secret 可重跑；修改工作流必须将新版本提交合并，重跑旧提交仍使用旧脚本。
完整三项目配置见相邻 evan-agent/docs/AUTO_DEPLOY.md。
