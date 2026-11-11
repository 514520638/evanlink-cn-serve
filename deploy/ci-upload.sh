#!/usr/bin/env bash
# 上传构建产物到部署服务器。
#
# 背景：GitHub Actions runner（美国）到国内服务器的跨境链路按连接限速（约 19kB/s），
# 单连接传 46MB 的 jar 需要约 40 分钟，必然超时。这里改成：
#   1) 大文件切块，多条 SSH 连接并发上传（并发可叠加带宽，16 连接实测约 164kB/s）；
#   2) 每个分片独立重试，支持断点续传（--append-verify）；
#   3) 服务端按序重组并用 sha256 校验，校验不通过则部署不会开始。
#
# 用法: ci-upload.sh <remote_dir> <file> [file...]
set -Eeuo pipefail
remote=${1:?Remote directory required}
shift
[[ "$remote" =~ ^/tmp/evan[a-z-]*ci-[a-f0-9]{40}-[0-9]+-[0-9]+$ ]]
: "${HOST:?HOST required}"
key=${UPLOAD_SSH_KEY:-$HOME/.ssh/deploy}
ssh_opts="-i $key -o BatchMode=yes -o StrictHostKeyChecking=yes -o ConnectTimeout=15 -o ServerAliveInterval=15 -o ServerAliveCountMax=4"
export RSYNC_RSH="ssh $ssh_opts"

# 并发分片数、分片阈值、单分片尝试次数均可按链路状况覆盖。
concurrency=${UPLOAD_CONCURRENCY:-16}
chunk_threshold=${UPLOAD_CHUNK_THRESHOLD:-8388608}
attempts=${UPLOAD_ATTEMPTS:-3}
per_attempt_timeout=${UPLOAD_TIMEOUT:-900}

work=$(mktemp -d)
cleanup() {
  find "$work" -type f -delete 2>/dev/null || true
  rmdir "$work" 2>/dev/null || true
}
trap cleanup EXIT

size_of() { wc -c < "$1" | tr -d '[:space:]'; }
name_of() { basename "$1"; }
log() { printf '%s\n' "$*"; }

# CI runner 是 Linux（coreutils），本地调试时可能只有 BSD 工具链。
if command -v timeout >/dev/null 2>&1; then
  run_with_timeout() { timeout "$1s" "${@:2}"; }
elif command -v gtimeout >/dev/null 2>&1; then
  run_with_timeout() { gtimeout "$1s" "${@:2}"; }
else
  run_with_timeout() { log "WARNING: no timeout command available; relying on the job timeout"; "${@:2}"; }
fi

ssh_exec() { ssh $ssh_opts "root@$HOST" "$@"; }

# sha256 摘要；GNU 与 BSD 输出格式一致（"<hash>  <file>"）。
if command -v sha256sum >/dev/null 2>&1; then
  checksum_of() { sha256sum "$@"; }
else
  checksum_of() { shasum -a 256 "$@"; }
fi

# 单文件直传：失败自动重试并断点续传。
upload_direct() {
  local file=$1 target=$2 attempt
  for attempt in $(seq 1 "$attempts"); do
    if run_with_timeout "$per_attempt_timeout" rsync -rt --partial --append-verify \
        --info=progress2 --outbuf=L --timeout=60 "$file" "root@$HOST:$remote/$target"; then
      return 0
    fi
    log "  $target attempt $attempt/$attempts interrupted; resuming partial file" >&2
    sleep 3
  done
  return 1
}

# 大文件：切块并发上传 -> 服务端重组 -> sha256 校验。
upload_chunked() {
  local file=$1 base size chunk_size index offset part pids=() rc=0 started elapsed attempt
  base=$(name_of "$file")
  size=$(size_of "$file")
  chunk_size=$(( (size + concurrency - 1) / concurrency ))
  (( chunk_size > 0 )) || chunk_size=1

  # 先上传校验文件，重组阶段要立刻可用。
  ( cd "$(dirname "$file")" && checksum_of "$base" ) > "$work/$base.sha256"
  upload_direct "$work/$base.sha256" "$base.sha256" || {
    log "Failed to upload checksum for $base" >&2
    return 1
  }

  started=$(date +%s)
  index=0
  while (( index < concurrency )); do
    offset=$(( index * chunk_size ))
    (( offset >= size )) && break
    part="$work/$base.part.$(printf '%04d' "$index")"
    # tail -c +N 从第 N 字节开始，head -c 截断；不依赖 GNU split 的命名规则。
    tail -c +$(( offset + 1 )) "$file" | head -c "$chunk_size" > "$part"
    index=$(( index + 1 ))
  done

  for part in "$work"/"$base".part.*; do
    (
      for attempt in $(seq 1 "$attempts"); do
        if run_with_timeout "$per_attempt_timeout" rsync -rt --partial --append-verify \
            --info=stats2 --outbuf=L --timeout=60 "$part" "root@$HOST:$remote/"; then
          exit 0
        fi
        sleep 3
      done
      echo "Chunk $(basename "$part") failed after $attempts attempts" >&2
      exit 1
    ) &
    pids+=($!)
  done
  for p in "${pids[@]}"; do
    wait "$p" || rc=1
  done
  if (( rc != 0 )); then
    log "Chunked upload failed for $base" >&2
    return 1
  fi

  elapsed=$(( $(date +%s) - started ))
  log "  $base: $size bytes in ${elapsed}s ($(( size / (elapsed > 0 ? elapsed : 1) )) B/s over $concurrency connections)"

  # 服务端按序重组并校验；失败即中止部署。
  if ! ssh_exec "cd '$remote' && cat '$base'.part.* > '$base' && sha256sum -c '$base'.sha256 && rm -f '$base'.part.*"; then
    log "Checksum verification failed for $base; deployment will not start" >&2
    return 1
  fi
  log "  $base reassembled and verified on server"
}

big=()
small=()
for f in "$@"; do
  if (( $(size_of "$f") > chunk_threshold )); then
    big+=("$f")
  else
    small+=("$f")
  fi
done

if (( ${#small[@]} > 0 )); then
  for f in "${small[@]}"; do
    log "Uploading $(name_of "$f") ($(size_of "$f") bytes)"
    upload_direct "$f" "$(name_of "$f")" || {
      log "Upload failed for $f" >&2
      exit 1
    }
  done
fi

if (( ${#big[@]} > 0 )); then
  for f in "${big[@]}"; do
    log "Uploading $(name_of "$f") ($(size_of "$f") bytes) in $concurrency concurrent chunks"
    upload_chunked "$f" || exit 1
  done
fi

echo UPLOAD_COMPLETE
