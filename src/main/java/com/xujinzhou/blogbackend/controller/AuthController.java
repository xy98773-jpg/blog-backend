package com.xujinzhou.blogbackend.controller;

import com.xujinzhou.blogbackend.common.ApiResponse;
import com.xujinzhou.blogbackend.entity.User;
import com.xujinzhou.blogbackend.mapper.UserMapper;
import com.xujinzhou.blogbackend.security.JwtUtil;
import com.xujinzhou.blogbackend.service.UserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final UserService userService;

    public AuthController(UserMapper userMapper, PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil, UserService userService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.userService = userService;
    }

    @PostMapping("/register")
    public ApiResponse<String> register(@RequestBody Map<String, String> body) {
        userService.register(body.get("username"), body.get("password"));
        return ApiResponse.success("注册成功");
    }

    @PostMapping("/login")
    public ApiResponse<String> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String rawPassword = body.get("password");

        User user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                        .eq("username", username)
        );

        if (user == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            return ApiResponse.error(401, "用户名或密码错误");
        }

        String token = jwtUtil.generateToken(username);
        return ApiResponse.success(token);
    }
}