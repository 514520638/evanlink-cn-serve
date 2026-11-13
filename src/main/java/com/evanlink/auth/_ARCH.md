# 管理员令牌撤销

RevokedAdminSession / Repository 保存令牌 SHA256 和到期时间。
AdminAuthService 签发随机化令牌、校验和撤销；AdminController 暴露轻量 session 及退出接口。
Agent 仅调用 session 校验，不共享或读取管理员明文密码。
