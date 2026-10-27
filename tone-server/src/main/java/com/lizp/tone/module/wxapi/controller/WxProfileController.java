package com.lizp.tone.module.wxapi.controller;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lizp.tone.common.BaseException;
import com.lizp.tone.common.ErrorCode;
import com.lizp.tone.common.R;
import com.lizp.tone.config.BaseProperties;
import com.lizp.tone.module.system.file.storage.FileStorageFactory;
import com.lizp.tone.module.system.file.storage.StorageArea;
import com.lizp.tone.module.wxapi.auth.WxUserContext;
import com.lizp.tone.module.wxapi.entity.AppWxUser;
import com.lizp.tone.module.wxapi.mapper.AppWxUserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 小程序用户资料（头像/昵称填写能力，个人主体可用）。
 *
 * <p>为什么做这个：个人主体拿不到 wx.getUserProfile，微信官方提供的替代方案是
 * 「头像昵称填写能力」——前端 button open-type="chooseAvatar"（拿临时文件）
 * + input type="nickname"（键盘带微信昵称），个人主体不受限。
 * 头像的临时文件必须换成本服务可持久访问的 URL，所以有 avatar 上传接口：
 * 复用模板的 FileStorageStrategy 存 PUBLIC 区（/upload/** 静态映射可直接访问）。
 *
 * <p>为什么不复用管理台的 /system/file 上传：那是 RBAC 保护的资产管理接口，
 * 小程序用户没有管理台角色；头像属于用户资料而非资产文件，独立轻量入口更合理
 * （不写 sys_file 表，管理台文件列表不展示）。
 */
@Tag(name = "小程序用户资料")
@RestController
@RequestMapping("/api/wx/profile")
@RequiredArgsConstructor
public class WxProfileController {

    private static final Set<String> ALLOWED_AVATAR_EXT = Set.of("png", "jpg", "jpeg", "webp");
    private static final long MAX_AVATAR_BYTES = 2L * 1024 * 1024;
    private static final int MAX_NICKNAME_LEN = 30;

    private final AppWxUserMapper appWxUserMapper;
    private final FileStorageFactory fileStorageFactory;
    private final BaseProperties baseProperties;

    @Data
    public static class ProfileVO {
        private String openId;
        private String nickname;
        private String avatar;
    }

    @Data
    public static class NicknameRequest {
        private String nickname;
    }

    @GetMapping
    @Operation(summary = "获取当前用户资料")
    public R<ProfileVO> get() {
        AppWxUser user = requireUser();
        ProfileVO vo = new ProfileVO();
        vo.setOpenId(user.getOpenId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        return R.ok(vo);
    }

    @PostMapping("/nickname")
    @Operation(summary = "保存昵称（前端用 input type=nickname 获取微信昵称）")
    public R<ProfileVO> saveNickname(@RequestBody NicknameRequest request) {
        String nickname = request.getNickname() == null ? "" : request.getNickname().trim();
        if (!StringUtils.hasText(nickname)) {
            throw new BaseException(ErrorCode.PARAM_INVALID, "昵称不能为空");
        }
        if (nickname.length() > MAX_NICKNAME_LEN) {
            throw new BaseException(ErrorCode.PARAM_INVALID, "昵称最长 " + MAX_NICKNAME_LEN + " 个字符");
        }
        AppWxUser user = requireUser();
        user.setNickname(nickname);
        appWxUserMapper.updateById(user);
        ProfileVO vo = new ProfileVO();
        vo.setOpenId(user.getOpenId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        return R.ok(vo);
    }

    /**
     * 上传头像（前端 button open-type=chooseAvatar 拿到临时文件后 wx.uploadFile 到此）。
     * 返回可直接访问的 URL（PUBLIC 区静态映射）。
     */
    @PostMapping("/avatar")
    @Operation(summary = "上传头像（multipart 字段名 file）")
    public R<ProfileVO> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BaseException(ErrorCode.PARAM_INVALID, "请选择头像文件");
        }
        if (file.getSize() > MAX_AVATAR_BYTES) {
            throw new BaseException(ErrorCode.PARAM_INVALID, "头像不能超过 2MB");
        }
        String ext = StrUtil.subAfter(file.getOriginalFilename(), ".", true).toLowerCase();
        if (!ALLOWED_AVATAR_EXT.contains(ext)) {
            throw new BaseException(ErrorCode.PARAM_INVALID, "仅支持 png/jpg/webp");
        }
        AppWxUser user = requireUser();

        try {
            // PUBLIC 区：/upload/** 已被静态映射，可直接访问；key 用 avatar/ 前缀分组
            String key = "avatar/" + IdUtil.simpleUUID() + "." + ext;
            fileStorageFactory.resolve().save(StorageArea.PUBLIC, key,
                    file.getBytes(), file.getContentType());
            user.setAvatar(baseProperties.getFile().getUrlPrefix() + "/" + key);
        } catch (Exception e) {
            throw new BaseException(ErrorCode.FILE_UPLOAD_ERROR, "头像上传失败：" + e.getMessage());
        }
        user.setUpdateTime(LocalDateTime.now());
        appWxUserMapper.updateById(user);

        ProfileVO vo = new ProfileVO();
        vo.setOpenId(user.getOpenId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        return R.ok(vo);
    }

    private AppWxUser requireUser() {
        String openId = WxUserContext.getOpenId();
        AppWxUser user = appWxUserMapper.selectOne(
                Wrappers.<AppWxUser>lambdaQuery().eq(AppWxUser::getOpenId, openId));
        if (user == null) {
            throw new BaseException(ErrorCode.RECORD_NOT_FOUND);
        }
        return user;
    }
}
