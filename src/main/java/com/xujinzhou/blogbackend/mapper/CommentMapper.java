package com.xujinzhou.blogbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xujinzhou.blogbackend.entity.Comment;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface CommentMapper extends BaseMapper<Comment> {

    // 继承 BaseMapper 后，增删改查方法全部自动具备 —— 平时这里什么都不用写。
    // 但"统计多篇文章的评论数"这种【聚合查询】通用方法做不到，必须手写 SQL。

    /**
     * ★ 一次查询统计多篇文章的评论数（解决 N+1 的关键）
     *
     * 生成的 SQL：
     *   SELECT article_id AS articleId, COUNT(*) AS cnt
     *   FROM comment
     *   WHERE status = 0 AND article_id IN (1, 2, 3)
     *   GROUP BY article_id
     *
     * 为什么返回 List<Map<String, Object>> 而不是自定义 DTO？
     *   → 这个查询的结果只有两列（articleId / cnt），为它建一个类值得吗？不值得。
     *   → 临时结构用 Map 是最轻的选择。
     *   → 什么时候该建 DTO：列数多（>3）、或这个结构要跨多层传递时。
     *
     * ★★ 统计规则的【唯一定义处】★★
     *   下面 SQL 里的 status = 0 就是"什么样的评论算有效"这条规则。
     *   它和 CommentService.STATUS_NORMAL（=0）是【同一条规则的两副面孔】：
     *     · 这里管【计数】  （SQL 字面量）
     *     · 那边管【列表 / 软删】（Java 常量）
     *   → 规则变更时（例如"审核不通过的也不算"）【两处必须同步改】，
     *     只改一边就会出现"列表显示 3 条、计数说 2 条"这种最难查的 bug。
     *   → 为什么不做成同一个常量？SQL 是字符串，想引用 Java 常量只能用编译期常量
     *     拼接（"WHERE " + STATUS_SQL），可读性反而下降。跨语言边界（Java ↔ SQL）
     *     的重复，用注释固化约定比强行抽象更划算 —— 不是所有重复都值得消除。
     *
     * 关于 @Param("articleIds")：
     *   告诉 MyBatis "把这个 List 绑定到 SQL 里的 #{articleIds} 参数名"。
     *   不加 @Param 时，MyBatis 需要靠编译期保留的参数名来找它，
     *   如果编译参数没开 -parameters 就会报 Parameter 'xxx' not found。
     *   → 显式加 @Param 是更稳妥的写法。
     *
     * ★ 关于 <script> 标签（重点）：
     *   动态 SQL 标签（<if> / <foreach> / <where>...）在注解里【必须】被
     *   <script> 包起来，否则 MyBatis 不把它们当标签解析，而是当普通字符串，
     *   最终报错（类似 Parameter 'articleIds' not found 或 SQL 语法错误）。
     *   原因：MyBatis 对动态 SQL 的支持建立在 XML 解析之上，
     *        @Select 的字符串要被当成 XML 片段来处理，就必须有 <script> 根节点。
     *   → 对比：XML 版写 <select> 标签时不需要 <script>，因为 <select> 本身就是 XML 节点。
     *
     * ⚠️ 这是本项目第一个【手写 SQL】的地方。
     *   之前全靠 BaseMapper 的 selectCount(Wrapper) / selectPage 等通用方法。
     *   一旦数据需要【聚合 / 分组 / 多表连接】，通用方法就不够用。
     */
    @Select("<script>" +
            "SELECT article_id AS articleId, COUNT(*) AS cnt FROM comment " +
            "WHERE status = 0 " +
            "<if test='articleIds != null and articleIds.size() > 0'>" +
            "  AND article_id IN " +
            "  <foreach collection='articleIds' item='id' open='(' separator=',' close=')'>" +
            "    #{id}" +
            "  </foreach>" +
            "</if>" +
            " GROUP BY article_id" +
            "</script>")
    List<Map<String, Object>> countByArticleIds(@Param("articleIds") List<Long> articleIds);

    /**
     * 把某篇文章的评论【整批搬进归档表】（删文章时调用）
     *
     * ★ 为什么用 INSERT ... SELECT，而不是"查出来再循环插入"？
     *   循环插入 = N 条评论要 N 条 INSERT，外加 1 条 SELECT ——
     *   这正是我们上一次刚消灭掉的 N+1 毛病，不能换个地方再犯一次。
     *   INSERT ... SELECT 是【一条】SQL，数据在数据库内部直接搬过去，
     *   不用捞进 Java 内存再写回去，又快又省内存。
     *
     * ★ 为什么列列表里没有 archived_at？
     *   建表时给了 DEFAULT CURRENT_TIMESTAMP，不写就会自动填当前时间，
     *   而这个时刻正好就是"文章被删掉的时刻"。
     *
     * ★ 为什么 status 也要一起搬？
     *   "这条评论在文章被删之前就已经是软删状态"本身是数据，不能丢。
     *
     * ★ 为什么连【文章标题】也要快照一份（article_title_snapshot）？
     *   文章会被【物理删】，标题也就跟着没了。将来查归档时，如果只剩一个 article_id，
     *   根本看不出这条评论当初发在哪篇文章底下 —— 数据保住了，上下文却丢了。
     *   所以归档时把标题一并存下来，成本一列，收益是"看得懂"。
     *
     * ★ 为什么 SQL 的 SELECT 列表里能写 #{articleTitle}？
     *   MyBatis 的 #{} 最终会翻译成 JDBC 的 ? 占位符，于是 SQL 变成
     *     SELECT id, article_id, ..., ? FROM comment WHERE article_id = ?
     *   这在 SQL 里是合法的（SELECT 一个常量列，每行都返回同一个值），
     *   PreparedStatement 会把它当参数绑进去。
     *
     * @param articleId    要归档哪篇文章的评论
     * @param articleTitle 文章标题快照（标题随文章一起消失，故此处留档）
     * @return 归档了多少条（由调用方记录进日志）
     */
    @Insert("INSERT INTO comment_archive (id, article_id, user_id, content, status, created_at, article_title_snapshot) " +
            "SELECT id, article_id, user_id, content, status, created_at, #{articleTitle, jdbcType=VARCHAR} " +
            "FROM comment WHERE article_id = #{articleId}")
    int archiveByArticleId(@Param("articleId") Long articleId,
                           @Param("articleTitle") String articleTitle);

    /**
     * 反向操作：把评论【从归档表搬回 comment 表】（误删恢复用）
     *
     * ★ 为什么显式带上 id？
     *   归档表的主键刻意不自增、保持原值，就是为了这一步能【原样搬回】。
     *   不写 id 的话数据库会重新分配，评论 id 就变了 ——
     *   软删记录、分页顺序、可能的外部引用全都会错位。
     *
     * ⚠️ 调用前提：目标文章必须【已经存在】
     *   comment.article_id 上的外键是 RESTRICT，
     *   文章不存在时搬回去会直接报 1451。顺序必须是：先恢复文章，再恢复评论。
     *
     * ⚠️ 主键冲突风险（理论上）：
     *   InnoDB 的 AUTO_INCREMENT 只增不减，删掉的行 id 不会被重新分配，
     *   所以正常情况下搬回来不会撞主键。
     *
     * @return 恢复了多少条
     */
    @Insert("INSERT INTO comment (id, article_id, user_id, content, status, created_at) " +
            "SELECT id, article_id, user_id, content, status, created_at " +
            "FROM comment_archive WHERE article_id = #{articleId}")
    int restoreByArticleId(@Param("articleId") Long articleId);

    /**
     * 清空归档表里某篇文章的记录 —— 恢复流程的【收尾一步】
     *
     * ★ 为什么必须有这一步？
     *   restoreByArticleId 只是 INSERT SELECT（复制语义），归档表里那批行还在。
     *   不清的话，下次再删这篇文章 → 归档 INSERT 会撞主键 → ERROR 1062
     *   → 整个事务回滚 → 文章删不掉。
     *
     *   所以【恢复】的正确语义是【搬回】：搬走（INSERT 回 comment）+ 清空原处（DELETE 归档）。
     *   少了后半步，这套机制第二次使用就会自锁。
     *
     * @return 清理了多少条（正常情况下应等于刚才恢复的条数）
     */
    @Delete("DELETE FROM comment_archive WHERE article_id = #{articleId}")
    int deleteArchiveByArticleId(@Param("articleId") Long articleId);
}
