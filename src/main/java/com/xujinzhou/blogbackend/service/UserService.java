package com.xujinzhou.blogbackend.service;

import com.xujinzhou.blogbackend.entity.User;
import com.xujinzhou.blogbackend.entity.UserLog;
import com.xujinzhou.blogbackend.mapper.UserLogMapper;
import com.xujinzhou.blogbackend.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xujinzhou.blogbackend.exception.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserMapper userMapper;
    private final UserLogMapper userLogMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, UserLogMapper userLogMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.userLogMapper = userLogMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void register(String username, String rawPassword) {
        // ① 用户名查重 —— 必须在 insert【之前】，否则只能靠数据库报错才知道撞名
        Long existed = userMapper.selectCount(new QueryWrapper<User>().eq("username", username));
        if (existed != null && existed > 0) {
            throw new ApiException(ApiException.CONFLICT, "用户名已被注册：" + username);
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // ② 并发兜底：两个请求同时查重都通过，insert 时必有一个撞唯一索引。
            //    应用层查重是为了"报错友好"，唯一索引才是最后一道防线。
            throw new ApiException(ApiException.CONFLICT, "用户名已被注册：" + username);
        }

        if (username.equals("testfail")) {
            throw new RuntimeException("模拟日志服务异常");
        }

        UserLog log = new UserLog();
        log.setUsername(username);
        log.setAction("注册");
        userLogMapper.insert(log);
    }

    // 实验用：通过 this 自调用 register，绕过了 Spring 代理
    public void registerWrapper(String username, String rawPassword) {
        this.register(username, rawPassword);   // 关键就是这里的 this
    }
}