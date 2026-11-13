# 相册点赞

AlbumLikeController：公开照片点赞读取/写入、Cookie 签名、IP 限流。
AlbumLike / AlbumLikeRepository：唯一约束和点赞计数持久化。
依赖 AlbumPhotoRepository 的照片公开状态与站点配置。UI 为前端 AlbumLike。
