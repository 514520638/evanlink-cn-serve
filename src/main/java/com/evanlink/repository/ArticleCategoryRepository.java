package com.evanlink.repository;

import com.evanlink.model.ArticleCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArticleCategoryRepository extends JpaRepository<ArticleCategory, Long> {
    List<ArticleCategory> findByEnabledTrueOrderBySortOrderAscIdAsc();
    Optional<ArticleCategory> findByName(String name);
}
