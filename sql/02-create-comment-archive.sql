-- ============================================================
-- 02-create-comment-archive.sql   评论归档表
--
-- 用途：删文章时，先把该文章的评论【整批搬家】到这张表，
--       再物理删掉 comment 表里的行
--         → comment 表物理行清空 → 外键放行 → 文章删得掉
--         → 评论数据仍然保留 → 可审计、可恢复
--
-- 关键设计（每一条都有原因，不要凭直觉改）：
--
--   1. ★ 不建外键指向 article
--        外键 RESTRICT 不认识"你是归档表"，它只数有没有行引用着这篇父行。
--        归档表里的行【永远】指向一篇已经被删掉的文章，
--        一旦建了外键，这篇文章就永远删不掉 —— 又回到 ERROR 1451。
--        同理 user_id 也不建外键（沿用 comment 表的设计：评论不随用户消失）。
--
--   2. 主键沿用原 comment.id，【不设 AUTO_INCREMENT】
--        归档是"搬家"，不是"新建"。id 保持原值，将来恢复时
--          INSERT INTO comment SELECT ... FROM comment_archive WHERE article_id = ?
--        才能原样搬回，不会因为主键冲突、或 id 错位而丢掉原有关系。
--        若设了 AUTO_INCREMENT，归档时会生成一套新 id，原 id 就永久丢了。
--
--   3. 与 comment 表同构（6 列全保留）+ archived_at
--        status 也必须留：软删状态本身就是数据。丢掉它就丢掉了
--        "这条评论在文章被删之前，就已经处于已删除状态"这个事实。
--
--   4. 索引：article_id / user_id / archived_at
--        · article_id  按文章整批捞回、追溯"这篇文章被删前有哪些评论"
--        · user_id     按人审计"这个用户发过哪些评论（含已归档的）"
--        · archived_at 将来做归档清理策略（例如只保留 2 年）
--        注意：这里【不照抄】comment 表的 (article_id, status, id) 联合索引。
--        因为两表的查询模式不同：
--          comment        服务于"按文章查未删评论 + 按 id 倒序分页"
--          comment_archive 服务于"按文章整批捞回 + 按时间清理"
--        查询模式不同，索引就该不同 —— 索引永远服务于查询，不是越多越好。
--
--   5. 引擎 / 字符集 / 排序规则与 comment 表完全一致
--        utf8mb4 + utf8mb4_0900_ai_ci + InnoDB。
--        不一致的话，恢复时跨表 INSERT SELECT 可能乱码，JOIN 时索引也可能失效。
-- ============================================================

CREATE TABLE IF NOT EXISTS `comment_archive` (
  `id`          bigint       NOT NULL                COMMENT '原 comment.id（搬运时保持原值，不自增）',
  `article_id`  bigint       NOT NULL                COMMENT '原所属文章 id（文章可能已删除，故无外键）',
  `user_id`     bigint       NOT NULL                COMMENT '评论人 user.id（弱耦合，不建外键）',
  `content`     varchar(500) NOT NULL                COMMENT '评论内容',
  `status`      tinyint      NOT NULL DEFAULT 0      COMMENT '归档瞬间的状态快照：0=正常 1=已软删',
  `created_at`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '原评论创建时间',
    `archived_at` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '归档时间（= 文章被删的时间）',
    `article_title_snapshot` varchar(255) DEFAULT NULL COMMENT '文章标题快照：文章会被物理删，事后无从查起，故归档时留一份',
    PRIMARY KEY (`id`),

  KEY `idx_article_id` (`article_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_archived_at` (`archived_at`)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='评论归档表（删文章时整批搬入，用于审计与恢复）';
