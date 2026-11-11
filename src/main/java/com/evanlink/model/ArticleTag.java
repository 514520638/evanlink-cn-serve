package com.evanlink.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "article_tag")
@IdClass(ArticleTag.ArticleTagId.class)
public class ArticleTag {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id")
    private Article article;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tag_id")
    private Tag tag;

    public Article getArticle() { return article; }
    public void setArticle(Article article) { this.article = article; }

    public Tag getTag() { return tag; }
    public void setTag(Tag tag) { this.tag = tag; }

    public static class ArticleTagId implements Serializable {
        private Long article;
        private Long tag;

        public ArticleTagId() {}

        public ArticleTagId(Long article, Long tag) {
            this.article = article;
            this.tag = tag;
        }

        public Long getArticle() { return article; }
        public void setArticle(Long article) { this.article = article; }

        public Long getTag() { return tag; }
        public void setTag(Long tag) { this.tag = tag; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ArticleTagId that)) return false;
            return Objects.equals(article, that.article) && Objects.equals(tag, that.tag);
        }

        @Override
        public int hashCode() {
            return Objects.hash(article, tag);
        }
    }
}
