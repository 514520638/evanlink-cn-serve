package com.evanlink.repository;

import com.evanlink.model.AlbumPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AlbumPhotoRepository extends JpaRepository<AlbumPhoto, Long> {
    List<AlbumPhoto> findByDeletedFalseOrderBySortOrderAscCreatedAtDesc();
    Optional<AlbumPhoto> findByIdAndDeletedFalse(Long id);
}
