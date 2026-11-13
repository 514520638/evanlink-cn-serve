package com.evanlink.album;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AlbumLikeRepository extends JpaRepository<AlbumLike,String> {
    long countByPhotoId(Long photoId);
}
