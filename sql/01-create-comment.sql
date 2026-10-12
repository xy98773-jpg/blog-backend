-- ============================================================
-- 01-create-comment.sql   文章评论表
-- 设计要点：
--   1. 一对多：一篇文章 N 条评论，评论持有 article_id
--   2. article_id 外键 ON DELETE RESTRICT：有评论时不许物理删文章（最后一道保险）
--   3. status 软删：0=正常 1=已删除，绝不物理删评论（UGC 内容可恢复、可审计）
--   4. 联合索引 (article_id, status, id)：
--        对应查询 SELECT ... WHERE article_id=? AND status=0 ORDER BY id DESC
--        InnoDB 二级索引叶子节点自带主键，因此 ORDER BY id 可直接用索引有序性
--   5. user_id 只建索引、不建外键：评论不随用户消失（弱耦合）
-- ============================================================

CREATE TABLE `comment` (
  `id`         bigint       NOT NULL AUTO_INCREMENT COMMENT '评论主键',
  `article_id` bigint       NOT NULL                COMMENT '所属文章 id',
  `user_id`    bigint       NOT NULL                COMMENT '评论人 user.id',
  `content`    varchar(500) NOT NULL                COMMENT '评论内容',
  `status`     tinyint      NOT NULL DEFAULT 0      COMMENT '0=正常 1=已删除（软删）',
  `created_at` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_article_status_id` (`article_id`, `status`, `id`),
  KEY `idx_user_id` (`user_id`),
  CONSTRAINT `fk_comment_article`
    FOREIGN KEY (`article_id`) REFERENCES `article` (`id`)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='文章评论表';