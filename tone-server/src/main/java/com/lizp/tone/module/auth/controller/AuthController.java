package com.lizp.tone.module.auth.controller;

import com.lizp.tone.aspect.BusinessType;
import com.lizp.tone.aspect.Log;
import com.lizp.tone.aspect.RateLimiter;
import com.lizp.tone.common.R;
import com.lizp.tone.module.auth.dto.ChangePasswordRequest;
import com.lizp.tone.module.auth.dto.LoginRequest;
import com.lizp.tone.module.auth.dto.ProfileUpdateRequest;
import com.lizp.tone.module.auth.dto.RefreshRequest;
import com.lizp.tone.module.auth.service.AuthService;
import com.lizp.tone.module.auth.vo.LoginVO;
import com.lizp.tone.module.auth.vo.UserInfoVO;
import com.lizp.tone.security.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 认证接口 */
@Tag(name = "认证管理")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "账号密码登录")
    // 登录接口是撞库的重灾区：限流 + 失败锁定双管齐下
    // message 里不写等待时间：秒数由 RateLimiterAspect 按冷却剩余时间统一拼
    @RateLimiter(time = 60, count = 20, limitType = RateLimiter.LimitType.IP,
            message = "登录尝试过于频繁")
    @PostMapping("/login")
    public R<LoginVO> login(@RequestBody @Valid LoginRequest request) {
        return R.ok(authService.login(request), "登录成功");
    }

    @Operation(summary = "刷新令牌", description = "用 refreshToken 换取新的一对 token；" +
            "旧 refreshToken 在宽限期内仍可用，避免并发请求互相踢下线")
    @PostMapping("/refresh")
    public R<LoginVO> refresh(@RequestBody @Valid RefreshRequest request) {
        return R.ok(authService.refresh(request.refreshToken()));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout(currentUser());
        SecurityContextHolder.clearContext();
        return R.ok();
    }

    @Operation(summary = "修改密码", description = "修改成功后令牌版本会变更，需要重新登录")
    @Log(title = "修改密码", businessType = BusinessType.UPDATE)
    @PostMapping("/change-password")
    public R<Void> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        authService.changePassword(currentUser(), request);
        return R.ok(null, "密码修改成功，请重新登录");
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/info")
    public R<UserInfoVO> info() {
        return R.ok(authService.getUserInfo(currentUser()));
    }

    @Operation(summary = "修改个人资料", description = "只能改昵称/姓名/手机/邮箱/性别/头像；" +
            "账号、部门、角色、状态属于管理动作，走用户管理页")
    @Log(title = "个人中心", businessType = BusinessType.UPDATE)
    @PutMapping("/profile")
    public R<UserInfoVO> updateProfile(@RequestBody @Valid ProfileUpdateRequest request) {
        return R.ok(authService.updateProfile(currentUser(), request), "资料已更新");
    }

    /**
     * 头像上传单独开口子，不复用 /system/file/upload：
     * 后者挂着 system:file:upload 权限，普通用户没有这个权限，
     * 让"改自己的头像"卡在文件权限上显然不合理。
     * 代价是这里要在服务里自己限制图片类型与大小（见 AuthServiceImpl.updateAvatar）。
     */
    @Operation(summary = "上传当前用户头像")
    @Log(title = "个人中心", businessType = BusinessType.UPDATE)
    @PostMapping("/avatar")
    public R<String> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return R.ok(authService.updateAvatar(currentUser(), file), "头像已更新");
    }

    private LoginUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        return null;
    }
}
