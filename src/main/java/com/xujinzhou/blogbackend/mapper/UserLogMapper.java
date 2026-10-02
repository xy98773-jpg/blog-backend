package com.xujinzhou.blogbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xujinzhou.blogbackend.entity.UserLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserLogMapper extends BaseMapper<UserLog> {
}
