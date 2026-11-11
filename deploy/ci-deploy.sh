#!/usr/bin/env bash
# [POS] Java 构建产物部署入口，沿用现有容器的环境变量、端口和网络。
# [INPUT] .github/workflows/deploy.yml (POS: Java 编译验证与 JAR 传输流程)
# [OUTPUT] 备份并覆盖现有 JAR、重启和验证，失败恢复旧 JAR。
set -Eeuo pipefail
incoming=${1:?Incoming directory required}
sha=${2:?Commit SHA required}
run=${3:?Run identifier required}
[[ "$sha" =~ ^[a-f0-9]{40}$ && "$run" =~ ^[0-9]+-[0-9]+$ ]]
[[ "$incoming" == "/tmp/evanlink-backend-ci-$sha-$run" ]]
test -s "$incoming/app.jar"
exec 9>/var/lock/evanlink-release.lock
echo "Waiting for server release lock (up to 10 minutes)"
flock -w 600 9
echo "Release lock acquired"
container=evanlink-backend
jar=/opt/evanlink-backend/app.jar
# 防止服务器部署方式变化后覆盖错误路径；已停止的服务也不擅自启动。
source=$(docker inspect --format '{{range .Mounts}}{{if eq .Destination "/app/app.jar"}}{{.Source}}{{end}}{{end}}' "$container")
test "$source" = "$jar"
test "$(docker inspect --format '{{.State.Running}}' "$container")" = true
test -s "$jar"
test ! -L "$jar"
umask 077
backup="/opt/evanlink-backend/backups/ci-$sha-$run"
install -d -m 700 /opt/evanlink-backend/backups
mkdir -m 700 "$backup"
cp -p "$jar" "$backup/app.jar"
restore() {
  status=$?
  if (( status != 0 )); then
    echo "Backend deployment failed; restoring $backup/app.jar" >&2
    # 覆盖同一个文件，保留 Docker 单文件 bind mount 所指向的 inode。
    if docker stop --time 60 "$container" && cp -p "$backup/app.jar" "$jar"; then
      docker start "$container" || echo 'Rollback start failed; inspect container logs' >&2
    else
      echo 'Rollback failed; inspect the saved JAR and container state' >&2
    fi
  fi
  exit "$status"
}
trap restore EXIT
docker stop --time 60 "$container"
cp -p "$incoming/app.jar" "$jar"
docker start "$container"
ready=0
for attempt in $(seq 1 45); do
  if curl -fsS --max-time 3 http://127.0.0.1:8080/api/articles/filters >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 2
done
test "$ready" -eq 1
curl -fsS --max-time 15 -o /dev/null https://evanlink.cn/api/articles/filters
echo "Backend deployed: $sha; previous JAR: $backup/app.jar"
# 部署成功后清空本次工作目录，避免上传/重试残留的临时文件在服务器堆积。
rm -rf -- "$incoming"
