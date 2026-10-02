package com.xujinzhou.blogbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xujinzhou.blogbackend.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}