package com.evanlink.repository;

import com.evanlink.model.ArticleTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ArticleTagRepository extends JpaRepository<ArticleTag, ArticleTag.ArticleTagId> {
    @Query("select count(at) from ArticleTag at where at.tag.id = :tagId and at.article.deleted = false")
    long countActiveArticlesByTagId(Long tagId);
}
