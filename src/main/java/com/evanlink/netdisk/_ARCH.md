# 百度网盘相册模块

负责单个博客主人的网盘绑定、目录读取和发布引用；依赖 AdminAuthService 的管理员认证及 AlbumPhotoRepository 的相册记录。

| 文件 | 职责 |
| --- | --- |
| BaiduNetdiskController.java | 管理接口、OAuth state、访客读取授权、流式响应 |
| BaiduNetdiskService.java | 百度接口、令牌加解密及刷新、目录边界、文件验证、分片上传和原图删除 |
| NetdiskCredential.java | 加密令牌实体 |
| NetdiskCredentialRepository.java | 凭据持久化 |

前端入口为 NetdiskAlbum。配置、数据库变更和验收见仓库 docs/BAIDU_NETDISK.md。

AlbumPhotoService 负责网盘与数据库写入顺序、失败补偿；AlbumPhotoController 使用有界原始请求体，拒绝本地存储回退。Nginx 上传缓冲设置见 deploy/nginx-album.location.conf。

权限边界：公开相册列表与已发布图片无需登录；管理员才能上传、删除、导入和访问未发布目录。公开访问不再受独立环境开关影响。

HEIC/HEIF：格式筛选与文件头验证已支持；media 优先百度预览图，否则流式返回原图，由前端 Worker 解码。
