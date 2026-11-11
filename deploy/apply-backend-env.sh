#!/usr/bin/env bash
# [POS] 应用服务器 env 文件变更：重启 Java 容器并做健康检查，失败自动回滚。
# [INPUT] /opt/evanlink-backend/env/.env (POS: 由 systemd path unit 监控写入)
# [OUTPUT] 容器以新 env 重启并通过健康检查；失败时还原旧 env 文件并重启。
set -Eeuo pipefail

container=evanlink-backend
env_file=/opt/evanlink-backend/env/.env
container_file=/env/.env
health_url=http://127.0.0.1:8080/api/articles/filters
max_wait=${ENV_APPLY_MAX_WAIT:-90}

# 与 CI 自动发布共用同一把锁，避免和 JAR 发布同时操作容器。
exec 9>/var/lock/evanlink-release.lock
flock -w 600 9

# 关键键缺失时直接拒绝应用，避免把容器重启成一个坏配置。
test -s "$env_file" || { echo "env file missing or empty: $env_file" >&2; exit 1; }
for key in SPRING_DATASOURCE_URL SERVER_PORT; do
  grep -q "^${key}=" "$env_file" || { echo "required key missing in $env_file: $key" >&2; exit 1; }
done
for key in APP_ADMIN_USERNAME APP_ADMIN_PASSWORD; do
  grep -q "^${key}=" "$env_file" || echo "WARNING: $key missing in $env_file; admin login will be disabled" >&2
done

snapshot=$(mktemp /opt/evanlink-backend/.env.rollback.XXXXXX)
cp -p "$env_file" "$snapshot"

restore() {
  status=$?
  if (( status != 0 )); then
    echo "env apply failed; rolling back to the previous $env_file" >&2
    # 先停掉监控单元：回写文件产生的新变更事件就不会再次触发本服务。
    systemctl stop evanlink-backend-env.path >/dev/null 2>&1 || true
    cp -p "$snapshot" "$env_file"
    docker restart "$container" >/dev/null 2>&1 || true
    systemctl start evanlink-backend-env.path >/dev/null 2>&1 || true
  fi
  rm -f "$snapshot"
  exit "$status"
}
trap restore EXIT

docker restart "$container" >/dev/null

# 目录挂载下容器读到的必须是宿主机当前文件；不一致说明挂载没生效。
host_sha=$(sha256sum "$env_file" | cut -d' ' -f1)
container_sha=$(docker exec "$container" cat "$container_file" | sha256sum | cut -d' ' -f1)
if [[ "$host_sha" != "$container_sha" ]]; then
  echo "container sees a different $container_file than the host file" >&2
  exit 1
fi

for _ in $(seq 1 "$max_wait"); do
  if curl -fsS --max-time 3 "$health_url" >/dev/null 2>&1; then
    echo "env applied to $container"
    exit 0
  fi
  sleep 2
done

echo "health check did not pass within ${max_wait}s: $health_url" >&2
exit 1
