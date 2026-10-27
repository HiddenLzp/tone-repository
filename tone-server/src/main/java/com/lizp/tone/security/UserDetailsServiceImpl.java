package com.lizp.tone.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security 的 UserDetailsService 实现。
 *
 * <p>登录主流程不走这里（AuthService 里显式校验密码 + 记登录日志 + 写登录日志表），
 * 但保留它是标准做法：将来接入表单登录、Basic 认证或第三方扩展时直接可用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final LoginUserLoader loginUserLoader;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        LoginUser loginUser = loginUserLoader.loadByUsername(username);
        if (loginUser == null) {
            throw new UsernameNotFoundException("用户不存在：" + username);
        }
        return loginUser;
    }
}
