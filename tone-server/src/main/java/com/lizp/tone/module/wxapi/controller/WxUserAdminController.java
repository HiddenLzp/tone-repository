package com.lizp.tone.module.wxapi.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lizp.tone.common.PageResult;
import com.lizp.tone.common.R;
import com.lizp.tone.module.wxapi.entity.AppWxUser;
import com.lizp.tone.module.wxapi.mapper.AppWxUserMapper;
import com.lizp.tone.security.authz.RequiresPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 微信用户管理（管理台）：查看小程序用户及其访问信息（IP/归属地/UA/访问次数/活跃时间）。
 *
 * <p>个人主体小程序拿不到微信昵称头像接口，这里的用户身份就是 openId +
 * 登录/访问过程中被动采集到的信息（见 WxUserActivityService 的注释）。
 */
@Tag(name = "微信用户管理")
@RestController
@RequestMapping("/agent/wxusers")
@RequiredArgsConstructor
public class WxUserAdminController {

    private final AppWxUserMapper appWxUserMapper;

    /** 列表/详情共用的 VO：时间转字符串（Boot 4 Web 层 Jackson3 对 LocalDateTime 的序列化格式不同，统一显式转换省心） */
    @Data
    public static class WxUserVO {
        private Long id;
        private String openId;
        private String nickname;
        private String avatar;
        private String lastIp;
        private String lastLocation;
        private String lastUa;
        private Long visitCount;
        private String lastActiveAt;
        private String createTime;
    }

    @GetMapping
    @RequiresPermissions("agent:wxuser:list")
    @Operation(summary = "微信用户分页（按最近活跃倒序）")
    public R<PageResult<WxUserVO>> page(@RequestParam(defaultValue = "1") long pageNum,
                                        @RequestParam(defaultValue = "10") long pageSize,
                                        @RequestParam(required = false) String openId) {
        Page<AppWxUser> page = appWxUserMapper.selectPage(new Page<>(pageNum, pageSize),
                Wrappers.<AppWxUser>lambdaQuery()
                        .like(StringUtils.hasText(openId), AppWxUser::getOpenId, openId)
                        .orderByDesc(AppWxUser::getLastActiveAt));
        return R.ok(PageResult.of(page, this::toVO));
    }

    @GetMapping("/{id}")
    @RequiresPermissions("agent:wxuser:query")
    @Operation(summary = "用户详情")
    public R<WxUserVO> detail(@PathVariable Long id) {
        AppWxUser user = appWxUserMapper.selectById(id);
        if (user == null) {
            return R.fail("用户不存在");
        }
        return R.ok(toVO(user));
    }

    private WxUserVO toVO(AppWxUser user) {
        WxUserVO vo = new WxUserVO();
        vo.setId(user.getId());
        vo.setOpenId(user.getOpenId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setLastIp(user.getLastIp());
        vo.setLastLocation(user.getLastLocation());
        vo.setLastUa(user.getLastUa());
        vo.setVisitCount(user.getVisitCount());
        vo.setLastActiveAt(fmt(user.getLastActiveAt()));
        vo.setCreateTime(fmt(user.getCreateTime()));
        return vo;
    }

    private String fmt(LocalDateTime t) {
        return t == null ? null : t.toString().replace('T', ' ');
    }
}
