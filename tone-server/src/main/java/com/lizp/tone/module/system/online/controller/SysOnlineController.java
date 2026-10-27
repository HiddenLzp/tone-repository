package com.lizp.tone.module.system.online.controller;

import com.lizp.tone.aspect.BusinessType;
import com.lizp.tone.aspect.Log;
import com.lizp.tone.common.PageResult;
import com.lizp.tone.common.R;
import com.lizp.tone.context.UserContextHolder;
import com.lizp.tone.module.system.online.SysOnlineService;
import com.lizp.tone.module.system.user.entity.SysUserToken;
import com.lizp.tone.security.LoginUser;
import com.lizp.tone.security.authz.RequiresPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 在线用户 */
@Tag(name = "在线用户")
@RestController
@RequestMapping("/system/online")
@RequiredArgsConstructor
public class SysOnlineController {

    private final SysOnlineService onlineService;

    @Operation(summary = "在线用户分页")
    @RequiresPermissions("system:online:list")
    @GetMapping("/page")
    public R<PageResult<SysUserToken>> page(@RequestParam(defaultValue = "1") long pageNum,
                                            @RequestParam(defaultValue = "10") long pageSize,
                                            @RequestParam(required = false) String username,
                                            @RequestParam(required = false) String ip) {
        return R.ok(onlineService.page(pageNum, pageSize, username, ip));
    }

    @Operation(summary = "在线人数")
    @GetMapping("/count")
    public R<Long> count() {
        return R.ok(onlineService.countOnline());
    }

    /**
     * 强制下线：只踢选中的这一条会话。
     * 要连该用户其它设备一起踢，用 {@code DELETE /user/{userId}}。
     */
    @Operation(summary = "强制下线（单条会话）")
    @RequiresPermissions("system:online:kick")
    @Log(title = "在线用户", businessType = BusinessType.FORCE_LOGOUT)
    @DeleteMapping("/{tokenId}")
    public R<Void> kick(@PathVariable Long tokenId) {
        onlineService.kick(tokenId);
        return R.ok();
    }

    @Operation(summary = "下线某用户的全部会话")
    @RequiresPermissions("system:online:kick")
    @Log(title = "在线用户", businessType = BusinessType.FORCE_LOGOUT)
    @DeleteMapping("/user/{userId}")
    public R<Void> kickUser(@PathVariable Long userId) {
        onlineService.kickByUserId(userId);
        return R.ok();
    }

    /** 我的登录设备：不需要额外权限，只能看自己的 */
    @Operation(summary = "我的登录设备")
    @GetMapping("/my")
    public R<List<SysUserToken>> my() {
        LoginUser user = UserContextHolder.get();
        if (user == null) {
            return R.ok(List.of());
        }
        return R.ok(onlineService.mySessions(user.getUserId(), user.getJti()));
    }

    /**
     * 下线我自己的某台设备（不含当前这台）。
     * 同样要留痕：账号被盗后本人剔除陌生设备是一个重要线索。
     */
    @Operation(summary = "下线我的某台设备")
    @Log(title = "在线用户", businessType = BusinessType.FORCE_LOGOUT)
    @DeleteMapping("/my/{tokenId}")
    public R<Void> kickOwn(@PathVariable Long tokenId) {
        LoginUser user = UserContextHolder.get();
        if (user == null) {
            return R.ok();
        }
        onlineService.kickOwn(user.getUserId(), tokenId, user.getJti());
        return R.ok();
    }
}
