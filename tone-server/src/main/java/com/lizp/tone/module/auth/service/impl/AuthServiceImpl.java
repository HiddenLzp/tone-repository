package com.lizp.tone.module.auth.service.impl;

import cn.hutool.core.util.StrUtil;
import com.lizp.tone.common.BaseException;
import com.lizp.tone.common.Constants;
import com.lizp.tone.common.ErrorCode;
import com.lizp.tone.config.BaseProperties;
import com.lizp.tone.module.auth.dto.ChangePasswordRequest;
import com.lizp.tone.module.auth.dto.LoginRequest;
import com.lizp.tone.module.auth.dto.ProfileUpdateRequest;
import com.lizp.tone.module.auth.service.AuthService;
import com.lizp.tone.module.auth.vo.LoginVO;
import com.lizp.tone.module.auth.vo.UserInfoVO;
import com.lizp.tone.module.monitor.LoginLogService;
import com.lizp.tone.module.system.dept.entity.SysDept;
import com.lizp.tone.module.system.dept.mapper.SysDeptMapper;
import com.lizp.tone.module.system.file.SysFileService;
import com.lizp.tone.module.system.file.entity.SysFile;
import com.lizp.tone.module.system.user.entity.SysUser;
import com.lizp.tone.module.system.user.entity.SysUserToken;
import com.lizp.tone.module.system.user.mapper.SysUserMapper;
import com.lizp.tone.module.system.user.mapper.SysUserTokenMapper;
import com.lizp.tone.security.JwtTokenProvider;
import com.lizp.tone.security.LoginUser;
import com.lizp.tone.security.LoginUserLoader;
import com.lizp.tone.security.TokenVersionManager;
import com.lizp.tone.utils.ClientInfo;
import com.lizp.tone.utils.RedisUtils;
import com.lizp.tone.utils.ServletUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 认证服务实现。
 *
 * <p>这里集中处理：验证码校验、账号锁定、密码校验、登录日志、令牌签发与吊销。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final LoginUserLoader loginUserLoader;
    private final SysUserMapper userMapper;
    private final SysUserTokenMapper userTokenMapper;
    private final SysDeptMapper deptMapper;
    private final SysFileService fileService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final RedisUtils redisUtils;
    private final LoginLogService loginLogService;
    private final TokenVersionManager tokenVersionManager;
    private final BaseProperties properties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginRequest request) {
        HttpServletRequest httpRequest = ServletUtils.getRequest();
        // 在请求线程内先把 IP/UA 拍成快照，异步写日志时不能再碰 request 对象
        ClientInfo client = ClientInfo.capture(httpRequest);
        verifyCaptcha(request);

        SysUser user = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query
                        .LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername())
                .eq(SysUser::getDelFlag, Constants.DEL_FLAG_NORMAL)
                .last("LIMIT 1"));

        // 用户不存在与密码错误返回同样的提示：避免暴露哪些账号是存在的
        if (user == null) {
            loginLogService.record(request.getUsername(), 1, "用户不存在", client);
            throw new BaseException(ErrorCode.PASSWORD_ERROR);
        }
        checkAccountLock(user);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            handleLoginFailure(user, client);
            throw new BaseException(ErrorCode.PASSWORD_ERROR);
        }
        if (Constants.STATUS_DISABLED.equals(user.getStatus())) {
            loginLogService.record(user.getUsername(), 1, "账号已停用", client);
            throw new BaseException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 登录成功：清空失败计数与锁定状态
        resetLoginFail(user);

        LoginUser loginUser = loginUserLoader.loadByUserId(user.getId());
        if (loginUser == null) {
            loginLogService.record(user.getUsername(), 1, "用户状态异常", client);
            throw new BaseException(ErrorCode.PASSWORD_ERROR);
        }
        loginUser.setTokenVersion(currentTokenVersion(user.getId()));

        String refreshJti = tokenProvider.newJti();
        // 记住 refreshToken 的 jti：登出时要精确吊销这一条会话
        loginUser.setJti(refreshJti);
        String accessJti = tokenProvider.newJti();
        String accessToken = tokenProvider.generateAccessToken(loginUser, accessJti);
        String refreshToken = tokenProvider.generateRefreshToken(user.getId(), refreshJti, loginUser.getTokenVersion());

        long refreshDays = properties.getSecurity().getRefreshExpire();
        // refreshToken 落 Redis：撤销、轮换、并发刷新判定都以它为准
        redisUtils.set(String.format(Constants.KEY_REFRESH, refreshJti), user.getId(), refreshDays, TimeUnit.DAYS);
        /*
         * 登录用户缓存：过滤器每次请求都读它，避免打库。
         * 写进去之前必须抹掉 jti——这个 key 是按「用户」缓存的，一个账号多端登录时，
         * 后登录的那台会把先登录那台的会话ID 顶掉，于是先那台一登出就吊销错了会话
         * （表现为：我在 A 浏览器退出，B 浏览器也跟着掉了）。
         * 会话ID 改由过滤器从 accessToken 的 sid 声明回填。
         */
        loginUser.setJti(null);
        redisUtils.set(String.format(Constants.KEY_LOGIN_USER, user.getId()), loginUser, refreshDays, TimeUnit.DAYS);

        saveUserToken(user, loginUser, refreshJti, refreshToken, client);

        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginTime(LocalDateTime.now());
        update.setLastLoginIp("未知".equals(client.ip()) ? null : client.ip());
        update.setLoginFailCount(0);
        update.setLockTime(null);
        userMapper.updateById(update);

        loginLogService.record(user.getUsername(), 0, "登录成功", client);

        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(properties.getSecurity().getAccessExpire() * 60)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .mustChangePassword(Boolean.TRUE.equals(loginUser.getMustChangePassword()))
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO refresh(String refreshToken) {
        if (StrUtil.isBlank(refreshToken)) {
            throw new BaseException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        Claims claims;
        try {
            claims = tokenProvider.parse(refreshToken);
        } catch (JwtTokenProvider.TokenExpiredException e) {
            throw new BaseException(ErrorCode.REFRESH_TOKEN_INVALID, "登录状态已过期，请重新登录");
        } catch (JwtTokenProvider.TokenInvalidException e) {
            throw new BaseException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (!JwtTokenProvider.TYPE_REFRESH.equals(JwtTokenProvider.getType(claims))) {
            throw new BaseException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        Long userId = JwtTokenProvider.getUserId(claims);
        String oldJti = claims.getId();
        String activeKey = String.format(Constants.KEY_REFRESH, oldJti);
        String graceKey = String.format(Constants.KEY_REFRESH_GRACE, oldJti);

        // 旧 token 要么还在有效期内，要么处于轮换宽限期；两者都不是说明令牌已作废或被重放
        if (!redisUtils.hasKey(activeKey) && !redisUtils.hasKey(graceKey)) {
            // 宽限期外还在用旧 token，判定为泄漏：整条会话链失效
            redisUtils.delete(String.format(Constants.KEY_LOGIN_USER, userId));
            throw new BaseException(ErrorCode.REFRESH_TOKEN_INVALID, "登录状态已失效，请重新登录");
        }
        /*
         * 令牌版本与会话吊销名单：这两道必须在这里补上。
         * 宽限期存在的意义是「并发刷新时旧 token 仍可用」，但它同时也是个后门——
         * 只要被强退/改密/停用发生在宽限期内，用户就能拿旧 refreshToken 换回一套全新令牌，
         * 表现为「强制下线了还能继续操作」。宽限期只放行并发，不放行已失效的令牌。
         */
        /*
         * 这两道必须抛 KICKED_OUT 而不是 REFRESH_TOKEN_INVALID：
         * 前端要按错误码区分「被强制下线」和「登录过期」，
         * 一律报 20009 的话，被管理员踢掉的人只会看到一句"登录已过期"，
         * 他既不知道是谁干的、也不知道该找谁——等于日志记了、当事人却一头雾水。
         */
        if (!java.util.Objects.equals(JwtTokenProvider.getTokenVersion(claims),
                tokenVersionManager.current(userId))) {
            redisUtils.delete(String.format(Constants.KEY_LOGIN_USER, userId));
            throw new BaseException(ErrorCode.KICKED_OUT, "你已被强制下线，请重新登录");
        }
        if (redisUtils.hasKey(String.format(Constants.KEY_SESSION_REVOKED, oldJti))) {
            throw new BaseException(ErrorCode.KICKED_OUT, "你已被强制下线，请重新登录");
        }

        LoginUser loginUser = loadLoginUser(userId);
        if (loginUser == null || !Constants.STATUS_NORMAL.equals(loginUser.getStatus())) {
            throw new BaseException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // 轮换：旧 token 进宽限期，签发新的一对
        long graceSeconds = properties.getSecurity().getRefreshGrace();
        redisUtils.delete(activeKey);
        redisUtils.set(graceKey, String.valueOf(userId), graceSeconds, TimeUnit.SECONDS);

        String newRefreshJti = tokenProvider.newJti();
        // 会话里的 jti 同步更新，否则下次登出吊销的还是旧令牌
        loginUser.setJti(newRefreshJti);
        String newAccessToken = tokenProvider.generateAccessToken(loginUser, tokenProvider.newJti());
        String newRefreshToken = tokenProvider.generateRefreshToken(userId, newRefreshJti, loginUser.getTokenVersion());
        long refreshDays = properties.getSecurity().getRefreshExpire();
        redisUtils.set(String.format(Constants.KEY_REFRESH, newRefreshJti), userId, refreshDays, TimeUnit.DAYS);
        // 同上：缓存是用户级的，不能把会话ID 写进去
        loginUser.setJti(null);
        redisUtils.set(String.format(Constants.KEY_LOGIN_USER, userId), loginUser, refreshDays, TimeUnit.DAYS);

        // 更新会话记录里的 jti，保持在线用户列表准确
        SysUserToken tokenRecord = userTokenMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUserToken>()
                        .eq(SysUserToken::getJti, oldJti).last("LIMIT 1"));
        if (tokenRecord != null) {
            tokenRecord.setJti(newRefreshJti);
            tokenRecord.setRefreshToken(
                    newRefreshToken.substring(0, Math.min(64, newRefreshToken.length())));
            tokenRecord.setAccessExpireTime(LocalDateTime.now()
                    .plusMinutes(properties.getSecurity().getAccessExpire()));
            tokenRecord.setRefreshExpireTime(LocalDateTime.now().plusDays(refreshDays));
            userTokenMapper.updateById(tokenRecord);
        }

        return LoginVO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(properties.getSecurity().getAccessExpire() * 60)
                .userId(userId)
                .username(loginUser.getUsername())
                .nickname(loginUser.getNickname())
                .mustChangePassword(Boolean.TRUE.equals(loginUser.getMustChangePassword()))
                .build();
    }

    @Override
    public void logout(LoginUser loginUser) {
        if (loginUser == null) {
            return;
        }
        String jti = loginUser.getJti();
        if (StrUtil.isNotBlank(jti)) {
            // 1. 吊销当前会话的 refreshToken。宽限期那份也要一起删，
            //    否则刚登出的 30 秒内还能拿旧令牌换回一套新的
            redisUtils.delete(String.format(Constants.KEY_REFRESH, jti));
            redisUtils.delete(String.format(Constants.KEY_REFRESH_GRACE, jti));
            // 2. 写会话吊销名单：accessToken 是无状态的，只能靠名单让它立刻失效
            redisUtils.set(String.format(Constants.KEY_SESSION_REVOKED, jti), 1,
                    properties.getSecurity().getRefreshExpire(), TimeUnit.DAYS);
        }
        /*
         * 这里故意不做「令牌版本 +1」。
         * 令牌版本是用户级的，+1 会把该用户其它设备的会话一起踢掉，
         * 而「退出登录」的语义只是退出当前这台。
         * 真要全端下线（改密 / 停用 / 管理员下线全部）走 SysOnlineService.kickByUserId。
         */

        SysUserToken record = new SysUserToken();
        record.setStatus(1);
        userTokenMapper.update(record, new com.baomidou.mybatisplus.core.conditions.update
                .LambdaUpdateWrapper<SysUserToken>()
                .eq(StrUtil.isNotBlank(jti), SysUserToken::getJti, jti)
                .eq(SysUserToken::getUserId, loginUser.getUserId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(LoginUser loginUser, ChangePasswordRequest request) {
        SysUser user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BaseException(ErrorCode.USER_NOT_FOUND);
        }
        // 是否处于强制改密态：只认库里的 first_login_flag，不认缓存、更不认请求体。
        // LoginUser.mustChangePassword 是登录那一刻拍进 Redis 的快照（LoginUserLoader），
        // 缓存失真时偏差方向不可控（快照残留 true → 普通用户被免原密码改密，那是真越权）；
        // 库记录上面刚读过，与后面的 updateById 在同一事务内，以它为准最权威。
        boolean mustChange = Integer.valueOf(1).equals(user.getFirstLoginFlag());

        // 新密码不允许设成系统初始密码。
        // 光靠下面"与库里当前哈希相同"的比不够：用户二次改密时库里已不是初始密码，
        // 弱口令就能被重新设回来，所以显式比配置值。
        String defaultPassword = properties.getUser().getDefaultPassword();
        if (StrUtil.isNotBlank(defaultPassword) && defaultPassword.equals(request.getNewPassword())) {
            throw new BaseException(ErrorCode.PASSWORD_WEAK, "新密码不能与系统初始密码相同");
        }

        if (mustChange) {
            // 强制态：跳过原密码校验，oldPassword 传了也直接忽略
            log.info("强制改密，跳过原密码校验 userId={}", user.getId());
        } else if (StrUtil.isBlank(request.getOldPassword())) {
            // 这一步不能省：matches(null, hash) 会抛 IllegalArgumentException 落到兜底处理器
            // 变成 10001"系统异常"；空白串虽不抛异常但会误报成"原密码不正确"
            throw new BaseException(ErrorCode.PARAM_INVALID, "原密码不能为空");
        } else if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BaseException(ErrorCode.OLD_PASSWORD_ERROR);
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BaseException(ErrorCode.NEW_PASSWORD_SAME);
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPassword(passwordEncoder.encode(request.getNewPassword()));
        // 改密后清除"需强制改密"标记，否则会一直被拦截
        update.setFirstLoginFlag(0);
        update.setPwdUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);

        // 改密视为一次安全事件：令牌版本 +1，强制所有设备重新登录
        tokenVersionManager.invalidate(user.getId());
        redisUtils.delete(String.format(Constants.KEY_LOGIN_USER, user.getId()));
        if (StrUtil.isNotBlank(loginUser.getJti())) {
            redisUtils.delete(String.format(Constants.KEY_REFRESH, loginUser.getJti()));
        }
    }

    @Override
    public UserInfoVO getUserInfo(LoginUser loginUser) {
        if (loginUser == null) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
        SysUser user = userMapper.selectById(loginUser.getUserId());
        SysDept dept = loginUser.getDeptId() == null ? null : deptMapper.selectById(loginUser.getDeptId());
        return UserInfoVO.builder()
                .userId(loginUser.getUserId())
                .username(loginUser.getUsername())
                .nickname(user == null ? loginUser.getNickname() : user.getNickname())
                .realName(user == null ? null : user.getRealName())
                .avatar(user == null ? null : user.getAvatar())
                .email(user == null ? null : user.getEmail())
                .phone(user == null ? null : user.getPhone())
                .deptId(loginUser.getDeptId())
                .deptName(dept == null ? null : dept.getDeptName())
                .superAdmin(loginUser.isSuperAdmin())
                .roles(loginUser.getRoles())
                .perms(loginUser.getPerms())
                .sex(user == null ? null : user.getSex())
                .mustChangePassword(Boolean.TRUE.equals(loginUser.getMustChangePassword()))
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserInfoVO updateProfile(LoginUser loginUser, ProfileUpdateRequest request) {
        if (loginUser == null) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
        Long userId = loginUser.getUserId();
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BaseException(ErrorCode.USER_NOT_FOUND);
        }
        if (!Constants.STATUS_NORMAL.equals(user.getStatus())) {
            throw new BaseException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (request.getSex() != null && (request.getSex() < 0 || request.getSex() > 2)) {
            throw new BaseException(ErrorCode.PARAM_INVALID, "性别取值非法");
        }
        checkPhoneUnique(request.getPhone(), userId);

        SysUser update = new SysUser();
        update.setId(userId);
        update.setNickname(request.getNickname().trim());
        update.setRealName(blankToNull(request.getRealName()));
        update.setEmail(blankToNull(request.getEmail()));
        update.setPhone(blankToNull(request.getPhone()));
        update.setSex(request.getSex() == null ? user.getSex() : request.getSex());
        // 头像允许前端直接传地址（例如接入了第三方头像），但空值表示"不改动"，
        // 否则清空头像的诉求会把库里的地址冲掉
        if (StrUtil.isNotBlank(request.getAvatar())) {
            update.setAvatar(request.getAvatar().trim());
        }
        userMapper.updateById(update);

        /*
         * 删登录缓存而不是改写它：KEY_LOGIN_USER 是按用户的，而 jti 是会话级的，
         * 直接把当前对象写回去会把这台设备的 jti 存进公共缓存，
         * 多端登录时别的设备登出就会吊销错会话（历史的坑，见 login()）。
         * 删掉最省事：下一个请求回源重建，昵称等字段自然是最新的。
         */
        redisUtils.delete(String.format(Constants.KEY_LOGIN_USER, userId));
        // 同步一下内存里的对象：同一个请求里后续若再用到昵称（比如写审计字段）不会拿到旧值
        loginUser.setNickname(update.getNickname());

        return getUserInfo(loginUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String updateAvatar(LoginUser loginUser, MultipartFile file) {
        if (loginUser == null) {
            throw new BaseException(ErrorCode.UNAUTHORIZED);
        }
        if (file == null || file.isEmpty()) {
            throw new BaseException(ErrorCode.FILE_UPLOAD_ERROR, "请选择要上传的图片");
        }
        // 头像接口不带权限标识（人人可改自己的头像），所以入口处必须自己把门：
        // 只允许图片、且压到 2MB，否则等于给登录用户开了一个任意文件上传口子
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new BaseException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "头像只能是图片文件");
        }
        if (file.getSize() > 2L * 1024 * 1024) {
            throw new BaseException(ErrorCode.FILE_TOO_LARGE, "头像大小不能超过 2MB");
        }

        SysFile saved = fileService.upload(file, "avatar", loginUser.getUserId());
        SysUser update = new SysUser();
        update.setId(loginUser.getUserId());
        update.setAvatar(saved.getFileUrl());
        userMapper.updateById(update);
        return saved.getFileUrl();
    }

    // ==================== 私有方法 ====================

    private void verifyCaptcha(LoginRequest request) {
        if (!properties.getSecurity().isCaptchaEnabled()) {
            return;
        }
        if (StrUtil.isBlank(request.getCaptchaKey()) || StrUtil.isBlank(request.getCaptchaCode())) {
            throw new BaseException(ErrorCode.CAPTCHA_ERROR, "请输入验证码");
        }
        String key = Constants.KEY_CAPTCHA + request.getCaptchaKey();
        String cached = redisUtils.getStr(key);
        // 验证码一次性：用完即删，防止被重放
        redisUtils.delete(key);
        if (cached == null || !cached.equalsIgnoreCase(request.getCaptchaCode().trim())) {
            throw new BaseException(ErrorCode.CAPTCHA_ERROR);
        }
    }

    private void checkAccountLock(SysUser user) {
        if (user.getLockTime() == null) {
            return;
        }
        if (user.getLockTime().isAfter(LocalDateTime.now())) {
            throw new BaseException(ErrorCode.ACCOUNT_LOCKED,
                    "账号已锁定，请在 " + user.getLockTime() + " 后重试");
        }
        // 锁定已到期，自动解锁
        SysUser unlock = new SysUser();
        unlock.setId(user.getId());
        unlock.setLockTime(null);
        unlock.setLoginFailCount(0);
        userMapper.updateById(unlock);
        user.setLockTime(null);
        user.setLoginFailCount(0);
    }

    private void handleLoginFailure(SysUser user, ClientInfo client) {
        int maxFail = properties.getSecurity().getMaxLoginFail();
        String failKey = String.format(Constants.KEY_LOGIN_FAIL, user.getUsername());
        // 计数放 Redis：DB 只存最终结果，避免高频失败把数据库写压力拉满
        long failCount = redisUtils.incr(failKey);
        redisUtils.expire(failKey, properties.getSecurity().getLockMinutes(), TimeUnit.MINUTES);

        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLoginFailCount((int) failCount);
        String msg;
        if (failCount >= maxFail) {
            LocalDateTime unlockTime = LocalDateTime.now()
                    .plusMinutes(properties.getSecurity().getLockMinutes());
            update.setLockTime(unlockTime);
            msg = "连续失败 " + failCount + " 次，账号已锁定至 " + unlockTime;
        } else {
            msg = "密码错误，还可尝试 " + (maxFail - failCount) + " 次";
        }
        userMapper.updateById(update);
        loginLogService.record(user.getUsername(), 1, msg, client);
    }

    /**
     * 手机号被别人占用时拒绝。
     * 手机号常被当作找回凭证 / 登录名，重复了会出现"两个账号绑同一台手机"的糊涂账。
     */
    private void checkPhoneUnique(String phone, Long excludeId) {
        if (StrUtil.isBlank(phone)) {
            return;
        }
        Long count = userMapper.selectCount(new com.baomidou.mybatisplus.core.conditions.query
                .LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getPhone, phone)
                .ne(excludeId != null, SysUser::getId, excludeId)
                .eq(SysUser::getDelFlag, Constants.DEL_FLAG_NORMAL));
        if (count > 0) {
            throw new BaseException(ErrorCode.DATA_EXIST, "手机号已被占用：" + phone);
        }
    }

    /** 空串统一存 null：库里留一堆空串会让"未填写"和"填写了空值"混在一起 */
    private String blankToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private void resetLoginFail(SysUser user) {
        redisUtils.delete(String.format(Constants.KEY_LOGIN_FAIL, user.getUsername()));
    }

    /** 版本号统一交给 TokenVersionManager，避免与 LoginUserLoader 各算各的导致刚登录就被判失效 */
    private Integer currentTokenVersion(Long userId) {
        return tokenVersionManager.current(userId);
    }

    private LoginUser loadLoginUser(Long userId) {
        Object cached = redisUtils.get(String.format(Constants.KEY_LOGIN_USER, userId));
        if (cached instanceof LoginUser loginUser) {
            return loginUser;
        }
        return loginUserLoader.loadByUserId(userId);
    }

    private void saveUserToken(SysUser user, LoginUser loginUser, String jti,
                               String refreshToken, ClientInfo client) {
        SysUserToken record = new SysUserToken();
        record.setUserId(user.getId());
        record.setUsername(user.getUsername());
        record.setJti(jti);
        record.setTv(loginUser.getTokenVersion());
        // 只存前缀：完整 token 存在泄露风险，且没有使用场景
        record.setRefreshToken(StrUtil.isBlank(refreshToken) ? null
                : refreshToken.substring(0, Math.min(64, refreshToken.length())));
        record.setClientType("web");
        record.setIp(client.ip());
        record.setLocation(client.location());
        record.setBrowser(client.browser());
        record.setOs(client.os());
        record.setLoginTime(LocalDateTime.now());
        record.setAccessExpireTime(LocalDateTime.now().plusMinutes(properties.getSecurity().getAccessExpire()));
        record.setRefreshExpireTime(LocalDateTime.now().plusDays(properties.getSecurity().getRefreshExpire()));
        record.setStatus(0);
        userTokenMapper.insert(record);
    }
}
