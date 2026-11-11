# 管理员凭据配置

真实账号、密码不应出现在源码、application.yml 默认值、README 或前端预填数据中。
管理员账号通过 APP_ADMIN_USERNAME 提供，密码通过 APP_ADMIN_PASSWORD 提供。
当前单管理员架构从受限的后端环境配置读取；若后续改为数据库账号系统，应存加盐密码哈希，不保存明文密码。

## 本地

在仓库根目录已被忽略的 .env 中配置以下变量，再重启 Java。新配置项和空值模板见 .env.example。

- APP_ADMIN_USERNAME：管理员登录账号。
- APP_ADMIN_PASSWORD：新密码，不要继续使用曾提交到仓库的密码。
- SPRING_DATASOURCE_URL、SPRING_DATASOURCE_USERNAME、SPRING_DATASOURCE_PASSWORD：实际开发数据库连接。

## 服务器

统一放在 /opt/evanlink-backend/backend.env，权限 600；不要把真实值放入 GitHub 仓库。
当前容器通过 docker run --env-file 注入环境。修改文件后需按原网络、端口、JAR 挂载重建容器，docker restart 不会重新读取 env 文件。
重建说明见 BAIDU_NETDISK.md 的统一环境文件章节。首次移除此默认值的版本上线前必须先配置账号和新密码。
数据库凭据沿用服务器现有 SPRING_DATASOURCE_*；如果数据库密码也曾被提交，需要协调更新 MySQL 账户密码和后端环境，不可仅改一边。

## 行为变化

- 未配置管理员凭据：登录接口返回 503，普通公开接口继续使用；不允许空密码或默认账号登录。
- 未配置密码：不会签发或接受登录/简历分享令牌。
- 更换密码：旧登录 Cookie 和旧简历分享链接会失效，需要重新登录/生成分享链接。
- 这里没有重新设计为数据库密码系统，服务器环境文件仍需限制读取权限。

## 已暴露凭据

删除当前代码不能删除旧提交中的内容。优先更换已暴露密码；Git 历史清理需要单独安排并协调其他克隆，不能用普通提交声称历史已清除。
本次不重写 Git 历史、不推送提交、不自动更改生产密码。
