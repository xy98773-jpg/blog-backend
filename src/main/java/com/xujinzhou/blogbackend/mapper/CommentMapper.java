package com.xujinzhou.blogbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xujinzhou.blogbackend.entity.Comment;
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
}
