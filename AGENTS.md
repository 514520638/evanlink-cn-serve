# 发布约定

日常代码修改只做本地验证与交付。生产发布由 GitHub Actions 在 main/master 收到 push 后执行。
只有用户在当前任务中明确要求直接部署或回滚时，才手动更新服务器。
不得因构建成功或 SSH 可用就自动发布。
流程与一次性配置见 deploy/AUTO_DEPLOY.md。
