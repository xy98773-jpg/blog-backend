package com.xujinzhou.blogbackend.service;

import com.xujinzhou.blogbackend.entity.User;
import com.xujinzhou.blogbackend.entity.UserLog;
import com.xujinzhou.blogbackend.mapper.UserLogMapper;
import com.xujinzhou.blogbackend.mapper.UserMapper;
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
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        userMapper.insert(user);

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