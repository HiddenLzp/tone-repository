package com.lizp.tone.module.system.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户令牌 / 在线会话实体。
 *
 * <p>accessToken 是无状态的、不落库；这里只记录 refreshToken，
 * 用于在线用户列表展示与强制下线。
 *
 * <p>注意：本表是「会话流水」而非业务实体，不做逻辑删除、不做审计字段填充，
 * 过期记录由 {@code sessionCleanTask} 物理清理，因此不继承 {@code BaseEntity}。
 */
@Data
@TableName("sys_user_token")
@Schema(description = "用户在线会话")
public class SysUserToken implements Serializable {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "账号（冗余，列表展示免联表）")
    private String username;

    @Schema(description = "refreshToken 唯一ID")
    private String jti;

    @Schema(description = "令牌版本号，与 Redis 中的版本比对，不一致即判定已失效")
    private Integer tv;

    @Schema(description = "refreshToken 前缀，仅作审计展示")
    private String refreshToken;

    @Schema(description = "客户端类型：web/app/h5")
    private String clientType;

    @Schema(description = "设备ID")
    private String deviceId;

    @Schema(description = "登录IP")
    private String ip;

    @Schema(description = "IP归属地")
    private String location;

    @Schema(description = "浏览器")
    private String browser;

    @Schema(description = "操作系统")
    private String os;

    @Schema(description = "登录时间")
    private LocalDateTime loginTime;

    @Schema(description = "accessToken 过期时间")
    private LocalDateTime accessExpireTime;

    @Schema(description = "refreshToken 过期时间")
    private LocalDateTime refreshExpireTime;

    @Schema(description = "状态：0在线 1登出 2过期 3强制下线")
    private Integer status;

    /**
     * 非表字段：是否「当前这一条」会话，前端用来标「本机 / 当前会话」。
     * 只能由服务端按请求令牌比对得出，不接受前端传。
     */
    @TableField(exist = false)
    @Schema(description = "是否当前会话")
    private Boolean current;
}
