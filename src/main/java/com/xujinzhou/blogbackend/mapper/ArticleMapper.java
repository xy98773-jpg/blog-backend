package com.xujinzhou.blogbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xujinzhou.blogbackend.entity.Article;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ArticleMapper extends BaseMapper<Article> {
    // 什么都不用写！继承BaseMapper后，增删改查方法全部自动具备
}
