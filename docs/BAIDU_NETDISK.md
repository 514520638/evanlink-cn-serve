# 百度网盘相册接入

## 当前范围

博客上传 → 百度网盘存储 → 博客查看 → 博客删除并同步删除网盘原图。
上传只接收 JPEG/PNG/WebP/GIF/HEIC/HEIF，单张最多 20MB，前端逐张请求，后端最多一路上传；检测文件签名，不信任扩展名。
采用原始二进制请求体，关闭 Spring multipart 自动解析。文件只在有界内存中处理，按 4MB 分片调用百度预创建、上传、合并接口，不写服务器文件。
下载最多 3 路，小缓冲流式转发；不将图片永久保存在服务器，但上传和访问仍经过服务器带宽。
删除先调用百度同步删除接口，确认单文件成功后才更新数据库；网盘删除失败时保留记录。百度是否将删除文件放入回收站由百度侧规则决定，本功能不清空网盘回收站。
上传成功但数据库写入失败时尝试撤销网盘原图；若撤销也失败，会明确提示在管理面板核对，避免静默留下孤立文件。
批量中途失败不会撤销此前成功照片，界面会重新加载成功记录；只重试失败照片以免重复上传。
原本镜像上传服务与 mirror API 已移除，所有新上传不再使用本地存储。
历史本地记录/文件没有自动迁移或销毁，保留原有相册展示；删除此类记录会提示先迁移，避免丢失唯一原图。通用 /uploads 静态读取暂保留兼容历史资源。
网盘目录内已有照片仍可通过面板导入，但导入后在博客删除也会同步删除该网盘原图。

## Nginx 必需配置：上传不落临时磁盘

将 deploy/nginx-album.location.conf 的 location 块加入现有 evanlink.cn HTTPS server 中；保留现有其他 location。
该配置提供 21MB 请求限制、禁用请求和响应缓冲、禁止该接口代理缓存、延长百度分片写入等待时间。
执行 nginx -t 通过后 reload。代码提交不会自动更改宿主机 Nginx，首次上线需同步此配置。
未配置时，默认请求大小限制可能导致 413；默认请求缓冲也可能将大请求暂存在 Nginx 临时目录。
Vite 开发代理不是 Nginx；本地与线上要分别验证。

## 配置

1. 在百度应用中登记 `https://evanlink.cn/api/album/baidu/callback`。
2. 在 Java 仓库根目录复制 `.env.example` 为 `.env`，填入重置后的 AppKey、SecretKey。
3. `openssl rand -base64 32` 生成 TOKEN_KEY，填入配置并备份，不能公开或提交 Git。
4. ROOT 指定专用照片目录。若百度应用只能访问应用专属目录，应改为获准路径。
5. Java 启动时从工作目录读取 `.env`（Spring properties 格式，值不加引号），也支持系统环境变量覆盖。

本地调试：前端使用 Vite 8081 的 /api 代理，同时百度平台需要允许对应本地回调，例如 `http://127.0.0.1:8081/api/album/baidu/callback`。
如果百度不允许该地址，使用可访问开发服务的 HTTPS 测试域名；生产回调不能把浏览器带回未部署的新接口。
浏览器访问域名应与回调域名一致，确保管理员 Cookie 和 OAuth 会话能够关联。

## 生产环境

Java 容器需要注入上述六项环境变量。若由 Compose 管理，在现有 Java 服务中配置 env_file 指向服务器受限权限的配置文件，然后按原配置重建该容器。
仅修改服务器上的文件或 docker restart 不会更新原容器创建时的环境变量。
不要直接覆盖现有容器启动参数或数据库凭据。此次代码未修改服务器，也未读取或使用聊天截图中的密钥。
前后端代码分别提交并合并到 main/master 后由现有 CI 发布。首次增加环境变量需要单独完成容器配置更新。
反向代理已支持 /api；不需要新代理前缀。OAuth state 存在服务端 HTTP Session，授权期间重启 Java 会导致本次授权失效，重新连接即可。
建议在 Nginx 为精确路径 `/api/album/baidu/callback` 关闭 access_log，避免日志长期保留一次性授权 code（必须保留该路径的 Java 代理转发）。

## 数据结构

- 新增 `baidu_netdisk_credential`：id=1，对应博客主人的单个绑定；encrypted_token 保存 AES-256-GCM 密文（随机 IV + 认证标签）。
- `album_photo` 新增可空 `baidu_file_id`。原有数据不迁移，旧记录保持 null。
- 当前项目 ddl-auto=update 在启动时创建/增加字段。生产上线前备份 MySQL。禁用自动更新的环境需显式迁移。

## 接口

- POST /api/album/photos?name=...&title=...：application/octet-stream 单张上传到百度网盘；需管理员和 X-Netdisk-Request: 1。
- DELETE /api/album/photos/{id}：先删除网盘原图，再移除相册记录。
- GET /api/album/baidu/status：管理员查看配置与绑定状态。
- POST /api/album/baidu/authorize：管理员发起授权，生成一次性 state，10 分钟有效。
- GET /api/album/baidu/callback：同一浏览器、有效管理员登录、匹配 state 后兑换令牌。
- DELETE /api/album/baidu/connection：移除存储的令牌（不是撤销百度端授权；彻底撤销可在百度账号授权管理操作）。
- GET /api/album/baidu/files?path=...&page=1：只浏览 ROOT 内目录，每页 50 条上游条目，过滤非照片文件。
- POST /api/album/baidu/publish：最多 20 个 ids，重新从百度验证文件，不信任客户端提供的标题/URL/路径。
- GET /api/album/baidu/media/{fsid}：管理员可预览 ROOT 内照片，访客只可读取已发布照片。

修改请求要求 X-Netdisk-Request: 1、管理员认证并检查浏览器 Origin。访问令牌和网盘直链不返回给浏览器。
文件读取只允许百度 HTTPS 域名；逐跳校验重定向；响应类型必须是支持的图片，不转发 HTML/SVG。
相册是公开展示页面：未登录可读取相册列表和已发布图片；上传、删除、导入、授权和网盘目录浏览均要求管理员认证。
已移除 BAIDU_NETDISK_PUBLIC_ENABLED 开关，旧环境文件中即使保留 false 也不再生效。网盘未导入/未发布的文件始终不能被访客读取。

## 手动验收

1. 无凭据时打开面板显示“等待后端配置”，不能发起授权。
2. 配置后连接百度，授权成功回到 /album，并显示网盘目录；拒绝或过期则显示授权失败。
3. 预览 JPG/PNG，刷新页面和重启 Java 后仍可读取；加密密钥必须保持一致。
4. 上传一张测试照片，或从网盘选择一张已有照片确认发布；退出管理员登录后验证相册仍可查看和大图预览。
5. 未发布照片的 media URL 对访客返回 404；越界目录请求被拒绝。
6. 删除相册记录后访客不能读取，百度原图也已移除。
7. 断开绑定后读取报错但不泄露令牌；可以重新授权恢复。

官方接口参考：百度网盘开放平台 OAuth 授权文档、file list，以及百度官方 baidu-netdisk/mcp 的 multimedia/filemetas 实现。
真实百度调用仍需用户配置有效应用凭据后联调；本地编译与测试不证明平台应用已获公开分发权限。

## unknown client id 排查

CLIENT_ID 对应百度页面 AppKey，不是 AppID；CLIENT_SECRET 对应 SecretKey，不是 SignKey。
修改 .env 后需要重启本地 Java 进程；系统环境变量优先级高于 .env，若仍使用旧值需检查启动环境。
生产容器需要单独注入配置，Git 忽略的本地 .env 不会随自动部署上传；修改 env_file 后需按原启动配置重建容器。
重新从博客点击“连接百度网盘”，不要复用之前错误参数生成的授权网址。

## 访客与管理员交互

访客进入 /album 直接加载已发布照片，无需博客或百度账号登录，仅显示登录管理入口。
管理员登录后显示上传照片、百度网盘管理和删除入口；后端独立校验写入权限，不能通过直接调用接口绕过登录。
本人退出博客登录不会断开百度绑定；访客加载图片时由后端使用已加密存储的网盘授权。仅主动断开百度连接、授权失效或供应商异常会影响网盘图片读取。

## HEIC/HEIF

目录识别支持 .heic/.heif（大小写不敏感），元信息与发布校验接受这两种格式；仍受 20MB 单张限制。
读取优先尝试百度返回的兼容缩略图，没有可用预览则读取原图。Java 不解码 HEIC，也不保存转换文件。
前端 AlbumImage 按需读取，通过独立 Worker 中的 heic-to 解码为 JPEG 展示，每次只处理一张，销毁 Worker 和对象 URL 回收资源。转换只发生在访客浏览器，不改动百度原图。
特殊编码、损坏文件或客户端内存不足仍可能失败，页面提供重试并提示转换为 JPG。
从网盘上传的 HEIC 会出现在管理列表，仍需选择发布后才加入公开相册；不是自动同步整个目录。

## 当前服务器统一 Java 环境文件（一次性操作）

已核实当前容器由 docker run 创建，没有 Compose 标签；数据库参数在容器环境中，JAR 是唯一挂载。
`--env-file` 只能作为 docker run 参数使用，不是 Bash 命令。建议统一 `/opt/evanlink-backend/backend.env`，不要求百度单独维护一个文件。
下面步骤先导出当前容器全部环境，再追加已填写的 baidu.env，避免重建时丢失数据库连接。文件中包含密钥，禁止提交 Git 或复制到聊天。

```bash
cd /opt/evanlink-backend
umask 077
# 若 backend.env 已经存在，先备份并核对它，不要再次覆盖。
test ! -e backend.env && docker inspect evanlink-backend --format '{{range .Config.Env}}{{println .}}{{end}}' > backend.env
cat baidu.env >> backend.env
chmod 600 backend.env
```

确认 backend.env 内容无误后，一次性按已核实的原网络、端口和 JAR 挂载重建。该操作会短暂中断 Java 接口：

```bash
backend_image=$(docker inspect --format '{{.Image}}' evanlink-backend)
backend_previous="evanlink-backend-env-backup-$(date +%Y%m%d%H%M%S)"
docker stop evanlink-backend
docker rename evanlink-backend "$backend_previous"
docker run -d --name evanlink-backend --pull=never \
  --network evanlink-net --restart unless-stopped \
  --env-file /opt/evanlink-backend/backend.env \
  -p 127.0.0.1:8080:8080 \
  -v /opt/evanlink-backend/app.jar:/app/app.jar \
  "$backend_image" java -jar /app/app.jar
```

等待启动后检查 `curl -fsS https://evanlink.cn/api/articles/filters`，随后在博客重新连接网盘。
失败回退（同一个终端中 backup 变量仍有效）：

```bash
docker rm -f evanlink-backend
docker rename "$backend_previous" evanlink-backend
docker start evanlink-backend
```

今后百度变量统一维护 backend.env。修改环境文件仍需重建容器才能生效；正常 CI 更新 JAR 不需要重建，继续复用已注入环境。
本地 .env 只在本地 Java 启动工作目录生效；未挂载的宿主机文件不会自动出现在容器内部。

## 图片请求与缩略图

管理列表显示 48px 小预览，使用 media/{id}?variant=thumb，优先百度 url1 小图，其次 url2/url3，无可用预览时回退原图。目录外和未发布照片的授权规则不变。
服务端仍限制最多 3 路图片转发，超出时最多等 2 秒，再返回带 Retry-After: 2 的 429。
前端统一队列最多 2 个请求，同 URL 合并，短期内存缓存 45 秒、总量最多 24MB/32 项；登录、退出、删除、断开时清理。其他浏览器已加载图片不能即时撤回。
429/502/503/504 自动退避重试，最多 4 次请求；401/403/404 不重复请求。客户端关闭页面/弹窗时取消无人使用的请求。图片仍不落服务器磁盘。
首页头像和微信二维码若使用外部图床，其限流由对方控制；浏览器跨域限制可能只能用原生图片加载并有限重试，不能承诺消除第三方 429。

## LIVP 与取消发布

网盘列表支持 LIVP。优先使用百度提供的预览图，缺少预览时流式扫描 ZIP 容器并返回其中 JPG/HEIC/HEIF 等静态照片，前端负责 HEIC 解码。此版本不播放 MOV 动态部分，原始 LIVP 完整保留；包仍受 20MB 限制。最多扫描 32 项，限制跳过条目和照片的解压大小，不创建解压目录。
新增 album_photo.published 字段，默认 true 保持历史公开照片；ddl-auto=update 会增加字段，手动迁移环境使用 `ALTER TABLE album_photo ADD COLUMN published BOOLEAN DEFAULT TRUE;`（仅字段尚不存在时执行）。
GET /files 返回 published 状态；POST /unpublish 接收 ids 数组，管理员专用，仅将 published 设为 false，不调用百度删除。列表和访客图片读取同时受此字段约束。重新发布复用原记录，不生成重复相册条目。
未公开照片仍允许管理员预览；其他访客无法继续请求隐藏照片。用户已下载/已加载的图片无法远程收回，短期客户端缓存最多 45 秒，当前管理员取消发布时主动清理本页缓存。
前端普通网络并发 3，saveData/2G 情况并发 2；这只是同时下载的数量，所有照片仍会依次加载。后端仍最多 3 路转发，多访客情况下继续自动退避重试。
