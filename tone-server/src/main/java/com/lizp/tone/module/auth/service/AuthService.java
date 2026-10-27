package com.lizp.tone.module.auth.service;

import com.lizp.tone.module.auth.dto.ChangePasswordRequest;
import com.lizp.tone.module.auth.dto.LoginRequest;
import com.lizp.tone.module.auth.dto.ProfileUpdateRequest;
import com.lizp.tone.module.auth.vo.LoginVO;
import com.lizp.tone.module.auth.vo.UserInfoVO;
import com.lizp.tone.security.LoginUser;
import org.springframework.web.multipart.MultipartFile;

/** 认证服务 */
public interface AuthService {

    /**
     * 账号密码登录。
     *
     * @return 双 token 与用户基本信息
     */
    LoginVO login(LoginRequest request);

    /**
     * 用 refreshToken 换新的一对 token（无感刷新）。
     *
     * <p>采用「轮换 + 宽限期」：旧 refreshToken 换新后不立即作废，
     * 而是在 {@code base.security.refresh-grace} 秒内仍可使用。
     * 这样同一时刻并发发出的多个刷新请求都能成功，不会互相把对方踢下线；
     * 超过宽限期仍拿旧 token 来换，则判定为令牌泄漏，直接失效。
     */
    LoginVO refresh(String refreshToken);

    /** 主动登出：吊销 refreshToken 并让该用户所有 accessToken 立即失效 */
    void logout(LoginUser loginUser);

    /** 修改当前登录用户密码，成功后需要重新登录 */
    void changePassword(LoginUser loginUser, ChangePasswordRequest request);

    /** 获取当前登录用户的详细信息（含角色与权限标识） */
    UserInfoVO getUserInfo(LoginUser loginUser);

    /**
     * 修改当前登录用户的个人资料（昵称 / 姓名 / 手机 / 邮箱 / 性别 / 头像）。
     *
     * <p>账号、部门、角色、状态一律不改：这些属于管理动作，
     * 只有用户管理页（system:user:edit）能改，个人中心开口子就是提权漏洞。
     *
     * @return 更新后的用户信息，前端可以直接拿它刷新右上角与表单
     */
    UserInfoVO updateProfile(LoginUser loginUser, ProfileUpdateRequest request);

    /** 上传并保存当前登录用户头像，返回头像访问地址 */
    String updateAvatar(LoginUser loginUser, MultipartFile file);
}
