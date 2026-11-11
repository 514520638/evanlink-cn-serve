package com.evanlink.repository;

import com.evanlink.model.Article;
import com.evanlink.model.ArticleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long>, JpaSpecificationExecutor<Article> {
    Optional<Article> findBySlugAndDeletedFalse(String slug);
    boolean existsBySlugAndDeletedFalse(String slug);
    boolean existsBySlugAndIdNotAndDeletedFalse(String slug, Long id);
    long countByStatusAndDeletedFalse(ArticleStatus status);

    @Modifying
    @Query("update Article a set a.views = a.views + 1 where a.slug = :slug and a.deleted = false")
    int incrementViews(@Param("slug") String slug);
}
