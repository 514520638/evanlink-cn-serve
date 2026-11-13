# 相册点赞与统一登录

## 点赞

新增 album_like 表，photo_id + visitor_hash 唯一；记录不保存 IP 或原始 Cookie。
访问者获得 HttpOnly、Lax、HTTPS 时 Secure 的签名 Cookie，每张已公开照片每个浏览器身份最多 1 赞。
签名使用 APP_ALBUM_LIKE_SECRET，未设置时由现有管理员密码派生；建议独立随机密钥，变更会重置访客识别。
同源写入头 X-Album-Request: 1，拒绝跨站，IP 限流每分钟最多 20 次新点赞。反向代理仅在来源为本机时采信 X-Real-IP，Nginx 必须覆盖该头。
清理 Cookie、换 IP/设备仍可能绕过匿名身份限制；严格真人防刷需要登录或验证码，本实现不宣称能阻挡所有机器人。
前端可视区懒读、350ms 防抖、请求互斥；重复提交在后端幂等，不是只靠按钮禁用。

## 统一登录

新增 revoked_admin_session 表，保存已撤销令牌的 SHA256 摘要和失效时间，不存原始令牌。
GET /api/admin/session 是轻量认证接口，替代为检查身份而加载完整个人资料和技能。
Java 单独保留管理员密码；Agent 通过固定服务端地址检查浏览器 Cookie。退出后旧 Cookie 会失效。
浏览器登录/退出通知在同页和标签页间同步，前端 Header 显示管理员身份菜单，提供访客提问和退出。
CORS 改为 APP_ALLOWED_ORIGINS 白名单，默认支持正式域名和本地 8081，不再开放任意带 Cookie 来源。

## 环境与发布

backend.env 仍保留 APP_ADMIN_USERNAME、APP_ADMIN_PASSWORD、SPRING_DATASOURCE_*。
可选增加 APP_ALBUM_LIKE_SECRET；没有新建的“统一密码”供两个服务共享。
Agent 配置 BLOG_API_BASE_URL，生产可使用 https://evanlink.cn/api，本地 http://127.0.0.1:8080/api。
先发布 Java，再发布 Agent 和前端。ddl-auto=update 将创建以上新表；其他迁移模式要先创建结构。
详细 Agent 集成与问题保留规则见相邻 evan-agent/docs/BLOG_INTEGRATION.md。

## 清理

移除未使用的二维码提前预加载 hook，删除相册重复认证 effect。
保留 Album/管理资料页各自的业务登录后操作，不删除仍被使用的兼容路由和数据库模型。
生产未被手动更新，按现有 main/master 自动发布约定执行。
