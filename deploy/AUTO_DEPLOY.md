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
app.jar 最多上传 32 分钟，部署最多 20 分钟；会与前端、Agent 共用 /var/lock/evanlink-release.lock。

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

## 服务器 env 变更自动生效

服务器上的 env 文件是 `/opt/evanlink-backend/env/.env`（0600，root 所有）。Docker 的环境变量在容器创建那一刻就固化了，`docker restart` 不会重新读取 `--env-file`，所以只改这个文件不会让新值生效，除非重建容器。

本项目的解法是让应用自己读文件：`src/main/resources/application.yml` 里已经有 `spring.config.import: "optional:file:.env[.properties]"`，`file:` 相对**进程工作目录**解析（服务器已用一次性容器实测确认）。因此把容器工作目录设成 env 所在目录，Spring 就会读到 `/env/.env`。

容器必须**同时**满足三点，缺一不可：

- 挂载**目录**而不是单个文件：`-v /opt/evanlink-backend/env:/env`；
- 工作目录指向该目录：`-w /env`；
- **不能**再带 `--env-file`。操作系统环境变量的优先级高于 `spring.config.import` 导入的配置，两者同时存在时容器里的旧值会一直盖住文件里的新值，改文件就表现为"不生效"。

**为什么必须挂目录**：单文件 bind mount 按 inode 绑定。宿主机文件被 `vi`、`sed -i`、`mv` 替换成新 inode 后，容器仍然读到旧内容，表现为"改了没生效"。实测确认：替换文件后容器内 `cat` 仍是旧值。目录挂载每次访问按路径解析，编辑器怎么写都能生效。`app.jar` 同样是单文件挂载，所以 CI 覆盖 JAR 时必须 `cp` 覆盖同一个文件而不能用 `mv`。

一次性切换（保留旧容器可回滚）：

```bash
cd /opt/evanlink-backend
umask 077
install -d -m 700 /opt/evanlink-backend/env
install -m 600 backend.env /opt/evanlink-backend/env/.env
backend_image=$(docker inspect --format '{{.Image}}' evanlink-backend)
backend_previous="evanlink-backend-env-backup-$(date +%Y%m%d%H%M%S)"
docker stop evanlink-backend
docker rename evanlink-backend "$backend_previous"
docker run -d \
  --name evanlink-backend \
  --pull=never \
  --network evanlink-net \
  --restart unless-stopped \
  -w /env \
  -v /opt/evanlink-backend/env:/env \
  -v /opt/evanlink-backend/app.jar:/app/app.jar \
  -p 127.0.0.1:8080:8080 \
  "$backend_image" java -jar /app/app.jar
```

回滚：`docker rm -f evanlink-backend`，再 `docker rename "$backend_previous" evanlink-backend` 和 `docker start evanlink-backend`。

如果 `/env/.env` 没被读到，应用会连不上数据库（`SPRING_DATASOURCE_URL` 退回 `application.yml` 里的 127.0.0.1 默认值）而启动失败，属于响亮失败，不会静默用空配置。工作目录改为 `/env` 后，`app.upload.dir: uploads` 指向 `/env/uploads`；该配置只用于 `/uploads/**` 静态映射（nginx 本就没有 `/uploads/` 路由，公网始终 404），不影响相册功能。

### 自动重启

服务器安装 systemd path unit 后，改 env 文件即自动生效，无需重建容器：

```bash
install -m 644 deploy/evanlink-backend-env.path /etc/systemd/system/
install -m 644 deploy/evanlink-backend-env.service /etc/systemd/system/
install -m 755 deploy/apply-backend-env.sh /opt/evanlink-backend/apply-backend-env.sh
systemctl daemon-reload
systemctl enable --now evanlink-backend-env.path
```

日常操作只有一条：编辑 `/opt/evanlink-backend/env/.env` 并保存。path unit 监听到文件 mtime 变化后调用 `apply-backend-env.sh`，重启容器、比对容器内文件与宿主机文件是否一致，再轮询 `http://127.0.0.1:8080/api/articles/filters`；健康检查不通过时自动还原旧 env 文件并重启容器。

注意：

- 重启期间接口中断约 10~20 秒，单实例无法避免。
- `apply-backend-env.sh` 与 CI 发布共用 `/var/lock/evanlink-release.lock`，不会和 JAR 发布撞车。
- CI 的 `ci-deploy.sh` 用 `docker stop` + `docker start` 更新 JAR，重启后同样会重新读取 `/env/.env`，行为一致。
- env 文件里出现 `SPRING_DATASOURCE_URL`、`SERVER_PORT` 之外的键是安全的；Spring 会忽略无法绑定的键。
- `/root/start-evanlink-backend.sh` 是 2026-05 的旧脚本，已过时：使用旧的 `--link` 网络方式且只硬编码了 5 个变量，运行它会丢掉 `BAIDU_NETDISK_*` 和 `APP_ADMIN_*`。不要再使用。

## 回退和排障

旧版本保存在 /opt/evanlink-backend/backups/ci-SHA-RUN/app.jar。
手动回退时，先停止容器，再把备份复制覆盖 /opt/evanlink-backend/app.jar，最后启动容器。
必须复制覆盖同一个文件，避免使用 mv 替换文件 inode 导致单文件挂载继续使用旧文件。
数据库 schema 不包含在 JAR 备份中；当前 ddl-auto=update 的数据库结构变更不能由 JAR 回滚逆转。
破坏性数据库变更需另行备份和迁移方案。旧 JAR 备份持续保留，需定期按磁盘空间轮换。

Host key verification failed 时，重新复制已验证的 known_hosts 到本仓库 Secret，不能以关闭 StrictHostKeyChecking 解决。
修正 Secret 可重跑；修改工作流必须将新版本提交合并，重跑旧提交仍使用旧脚本。
完整三项目配置见相邻 evan-agent/docs/AUTO_DEPLOY.md。

上传使用 rsync --append-verify，日志显示字节进度。60 秒无传输活动会失败重试，每次最多 10 分钟，共三次；重试保留部分文件。总上传步骤限制 32 分钟。当前服务器已安装 rsync。传输速度仍取决于 GitHub runner 到服务器的网络，续传不保证提高带宽。

## 跨境链路的传输方式

GitHub runner 在美国，服务器在国内腾讯云，两者之间的链路按连接限速：实测单连接约 19kB/s，46MB 的 JAR 单连接需要约 40 分钟，必然撞上 32 分钟的上传上限。

因此 deploy/ci-upload.sh 改为：

1. 大文件（默认大于 8MB）先切分成 N 个分片，默认 N=16，可用 `UPLOAD_CONCURRENCY` 调整；
2. 16 条 SSH 连接并发上传，分片互不影响，单个分片失败只重试该分片（`UPLOAD_ATTEMPTS`，默认 3 次）并支持断点续传；
3. 服务端按文件名顺序重组，再用随包上传的 sha256 校验文件核对；校验不通过则部署不会开始。

实测同一台 runner 上，单连接 19kB/s、8 连接 85kB/s、16 连接 164kB/s，46MB 约 5 分钟完成。小文件（脚本、校验文件）仍走单连接直传。

可按链路状况覆盖的环境变量：`UPLOAD_CONCURRENCY`（并发分片数）、`UPLOAD_CHUNK_THRESHOLD`（分片阈值字节）、`UPLOAD_ATTEMPTS`（单分片尝试次数）、`UPLOAD_TIMEOUT`（单次传输秒数上限）。

工作流在部署结束后会删除本次远端工作目录，并清理 6 小时前遗留的失败上传目录；服务器 /tmp 不再堆积半成品 JAR。
