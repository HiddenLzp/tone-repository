package com.lizp.tone.module.system.user;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lizp.tone.common.BaseException;
import com.lizp.tone.common.Constants;
import com.lizp.tone.common.ErrorCode;
import com.lizp.tone.common.PageResult;
import com.lizp.tone.config.BaseProperties;
import com.lizp.tone.dataperm.DataScope;
import com.lizp.tone.module.system.dept.SysDeptService;
import com.lizp.tone.module.system.role.SysRoleService;
import com.lizp.tone.module.system.role.entity.SysUserRole;
import com.lizp.tone.module.system.role.mapper.SysUserRoleMapper;
import com.lizp.tone.module.system.user.dto.UserQuery;
import com.lizp.tone.module.system.user.dto.UserSaveRequest;
import com.lizp.tone.module.system.user.entity.SysUser;
import com.lizp.tone.module.system.user.mapper.SysUserMapper;
import com.lizp.tone.module.system.user.vo.UserVO;
import com.lizp.tone.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 用户服务 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleService roleService;
    private final SysDeptService deptService;
    private final PasswordEncoder passwordEncoder;
    private final BaseProperties properties;
    private final com.lizp.tone.utils.RedisUtils redisUtils;
    private final com.lizp.tone.security.TokenVersionManager tokenVersionManager;

    /**
     * 分页查询用户。
     *
     * <p>{@code @DataScope} 会自动给 SQL 拼上部门过滤条件：
     * 超管看全部，部门经理看本部门及以下，普通用户只看自己。
     */
    @DataScope
    public PageResult<UserVO> page(UserQuery query) {
        IPage<SysUser> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .and(StrUtil.isNotBlank(query.getKeyword()), w -> w
                        .like(SysUser::getUsername, query.getKeyword())
                        .or().like(SysUser::getNickname, query.getKeyword())
                        .or().like(SysUser::getRealName, query.getKeyword()))
                .like(StrUtil.isNotBlank(query.getPhone()), SysUser::getPhone, query.getPhone())
                .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                .eq(query.getDeptId() != null, SysUser::getDeptId, query.getDeptId())
                .ge(query.getBeginTime() != null, SysUser::getCreateTime, query.getBeginTime())
                .le(query.getEndTime() != null, SysUser::getCreateTime, query.getEndTime())
                .orderByAsc(SysUser::getId);
        IPage<SysUser> result = userMapper.selectPage(page, wrapper);
        return PageResult.of(result, this::toVO);
    }

    public UserVO getDetail(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BaseException(ErrorCode.DATA_NOT_FOUND);
        }
        UserVO vo = toVO(user);
        vo.setRoleIds(roleService.listRoleIdsByUserId(id));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(UserSaveRequest request) {
        checkUsernameUnique(request.getUsername(), null);
        SysUser user = new SysUser();
        BeanUtil.copyProperties(request, user, "id", "roleIds");
        // 新用户统一用默认密码，并打上"首次登录强制改密"标记
        user.setPassword(passwordEncoder.encode(properties.getUser().getDefaultPassword()));
        user.setFirstLoginFlag(1);
        user.setUserType(0);
        user.setDelFlag(Constants.DEL_FLAG_NORMAL);
        user.setPwdUpdateTime(LocalDateTime.now());
        user.setLoginFailCount(0);
        if (user.getStatus() == null) {
            user.setStatus(Constants.STATUS_NORMAL);
        }
        userMapper.insert(user);
        saveUserRoles(user.getId(), request.getRoleIds());
        return user.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(UserSaveRequest request) {
        if (request.getId() == null) {
            throw new BaseException(ErrorCode.PARAM_INVALID, "用户ID不能为空");
        }
        SysUser old = userMapper.selectById(request.getId());
        if (old == null) {
            throw new BaseException(ErrorCode.DATA_NOT_FOUND);
        }
        checkUsernameUnique(request.getUsername(), request.getId());
        SysUser user = new SysUser();
        BeanUtil.copyProperties(request, user, "roleIds");
        // 账号不允许改：改了等于换了个登录身份，历史日志与会话会对不上
        user.setUsername(old.getUsername());
        userMapper.updateById(user);
        saveUserRoles(request.getId(), request.getRoleIds());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        LoginUser current = com.lizp.tone.context.UserContextHolder.get();
        for (Long id : ids) {
            if (current != null && id.equals(current.getUserId())) {
                throw new BaseException(ErrorCode.CANNOT_DELETE_SELF);
            }
            if (Constants.SUPER_ADMIN_USER_ID.equals(id)) {
                throw new BaseException(ErrorCode.SUPER_ADMIN_PROTECTED, "内置管理员不允许删除");
            }
            userMapper.deleteById(id);
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BaseException(ErrorCode.DATA_NOT_FOUND);
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(properties.getUser().getDefaultPassword()));
        // 重置后同样要求首次登录改密，避免管理员知道别人的初始密码
        update.setFirstLoginFlag(1);
        update.setPwdUpdateTime(LocalDateTime.now());
        update.setLoginFailCount(0);
        update.setLockTime(null);
        userMapper.updateById(update);
        // 令牌版本 +1：重置密码后强制下线，防止旧会话继续用
        tokenVersionManager.invalidate(id);
        redisUtils.delete(String.format(Constants.KEY_LOGIN_USER, id));
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        LoginUser current = com.lizp.tone.context.UserContextHolder.get();
        if (current != null && id.equals(current.getUserId())) {
            throw new BaseException(ErrorCode.CANNOT_DISABLE_SELF);
        }
        if (Constants.SUPER_ADMIN_USER_ID.equals(id)) {
            throw new BaseException(ErrorCode.SUPER_ADMIN_PROTECTED, "内置管理员不允许停用");
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setStatus(status);
        userMapper.updateById(update);
        // 停用后立刻踢下线，否则已登录的会话还能继续操作
        if (Constants.STATUS_DISABLED.equals(status)) {
            tokenVersionManager.invalidate(id);
            redisUtils.delete(String.format(Constants.KEY_LOGIN_USER, id));
        }
    }

    /** 分配角色：先删后插，保证与提交结果完全一致 */
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long userId, List<Long> roleIds) {
        saveUserRoles(userId, roleIds);
    }

    private void saveUserRoles(Long userId, List<Long> roleIds) {
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        if (CollUtil.isEmpty(roleIds)) {
            return;
        }
        roleIds.stream().distinct().forEach(roleId -> {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        });
    }

    private void checkUsernameUnique(String username, Long excludeId) {
        if (StrUtil.isBlank(username)) {
            return;
        }
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .ne(excludeId != null, SysUser::getId, excludeId));
        if (count > 0) {
            throw new BaseException(ErrorCode.DATA_EXIST, "账号已存在：" + username);
        }
    }

    /**
     * 实体转 VO。
     * 部门名称这里用一次性批量查询，避免列表里每一行都查一次部门（N+1）。
     */
    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();
        BeanUtil.copyProperties(user, vo);
        if (user.getDeptId() != null) {
            Map<Long, String> deptNames = deptService.deptNameMap(Set.of(user.getDeptId()));
            vo.setDeptName(deptNames.get(user.getDeptId()));
        }
        return vo;
    }
}
