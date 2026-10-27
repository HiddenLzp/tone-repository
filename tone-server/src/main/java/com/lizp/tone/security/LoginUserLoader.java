package com.lizp.tone.security;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lizp.tone.common.Constants;
import com.lizp.tone.module.system.dept.entity.SysDept;
import com.lizp.tone.module.system.dept.mapper.SysDeptMapper;
import com.lizp.tone.module.system.menu.entity.SysMenu;
import com.lizp.tone.module.system.menu.mapper.SysMenuMapper;
import com.lizp.tone.module.system.role.entity.SysRole;
import com.lizp.tone.module.system.role.entity.SysRoleDept;
import com.lizp.tone.module.system.role.entity.SysRoleMenu;
import com.lizp.tone.module.system.role.entity.SysUserRole;
import com.lizp.tone.module.system.role.mapper.SysRoleDeptMapper;
import com.lizp.tone.module.system.role.mapper.SysRoleMapper;
import com.lizp.tone.module.system.role.mapper.SysRoleMenuMapper;
import com.lizp.tone.module.system.role.mapper.SysUserRoleMapper;
import com.lizp.tone.module.system.user.entity.SysUser;
import com.lizp.tone.module.system.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 组装 {@link LoginUser}：把「用户 → 角色 → 资源/部门」的关系一次性查出来。
 *
 * <p>登录时调用一次，结果缓存进 Redis；后续请求从缓存取，不再打数据库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginUserLoader {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final SysMenuMapper menuMapper;
    private final SysDeptMapper deptMapper;
    private final TokenVersionManager tokenVersionManager;

    public LoginUser loadByUsername(String username) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .eq(SysUser::getDelFlag, Constants.DEL_FLAG_NORMAL)
                .last("LIMIT 1"));
        if (user == null) {
            return null;
        }
        return build(user);
    }

    public LoginUser loadByUserId(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || !Constants.DEL_FLAG_NORMAL.equals(user.getDelFlag())) {
            return null;
        }
        return build(user);
    }

    private LoginUser build(SysUser user) {
        List<Long> roleIds = userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, user.getId()))
                .stream().map(SysUserRole::getRoleId).toList();

        List<SysRole> roles = roleIds.isEmpty() ? List.of()
                : roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .in(SysRole::getId, roleIds)
                .eq(SysRole::getStatus, Constants.STATUS_NORMAL));

        List<String> roleCodes = roles.stream().map(SysRole::getRoleCode).toList();
        boolean superAdmin = roleCodes.contains(Constants.SUPER_ADMIN_ROLE);

        List<Long> menuIds = loadMenuIds(roleIds);
        Set<String> perms = new HashSet<>(loadPerms(menuIds));

        // 数据范围取所有角色里最宽的那个（数值越小范围越大），避免多角色时取到最窄的导致看不到数据
        Integer dataScope = roles.stream()
                .map(SysRole::getDataScope)
                .filter(java.util.Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(1);

        Set<Long> deptIds = resolveDeptIds(user.getDeptId(), roleIds, dataScope);

        return LoginUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .password(user.getPassword())
                .nickname(user.getNickname())
                .deptId(user.getDeptId())
                .status(user.getStatus())
                .superAdmin(superAdmin)
                .roles(roleCodes)
                .perms(perms)
                .menuIds(new HashSet<>(menuIds))
                .dataScope(dataScope)
                .customDeptIds(deptIds)
                .tokenVersion(currentTokenVersion(user.getId()))
                .mustChangePassword(Integer.valueOf(1).equals(user.getFirstLoginFlag()))
                .build();
    }

    /** 角色可见的菜单ID：role → menu */
    private List<Long> loadMenuIds(List<Long> roleIds) {
        if (CollUtil.isEmpty(roleIds)) {
            return List.of();
        }
        return roleMenuMapper.selectList(
                        new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds))
                .stream().map(SysRoleMenu::getMenuId).distinct().toList();
    }

    /** 角色拥有的权限标识：role → menu(type=3 按钮) → perms */
    private List<String> loadPerms(List<Long> menuIds) {
        if (CollUtil.isEmpty(menuIds)) {
            return List.of();
        }
        List<SysMenu> menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .in(SysMenu::getId, menuIds)
                .eq(SysMenu::getStatus, Constants.STATUS_NORMAL)
                .isNotNull(SysMenu::getPerms)
                .ne(SysMenu::getPerms, ""));
        return menus.stream().map(SysMenu::getPerms).filter(p -> p != null && !p.isBlank()).toList();
    }

    /**
     * 计算可见部门集合。
     * 3 本部门 / 4 本部门及以下 / 2 自定义（走 sys_role_dept）都在这里算好，
     * 数据权限拦截器直接拿 IDs 拼 SQL，避免运行时再查库或写子查询。
     */
    private Set<Long> resolveDeptIds(Long deptId, List<Long> roleIds, Integer dataScope) {
        if (dataScope == null || dataScope == 1 || deptId == null) {
            return Set.of();
        }
        if (dataScope == 2) {
            List<Long> custom = roleDeptMapper.selectList(
                            new LambdaQueryWrapper<SysRoleDept>().in(SysRoleDept::getRoleId, roleIds))
                    .stream().map(SysRoleDept::getDeptId).collect(Collectors.toSet())
                    .stream().toList();
            return new HashSet<>(custom);
        }
        if (dataScope == 3) {
            return Set.of(deptId);
        }
        if (dataScope == 4) {
            Set<Long> ids = new HashSet<>();
            ids.add(deptId);
            // ancestors 形如 "0,100,101"，用 FIND_IN_SET 一次查出所有子孙部门，避免递归
            List<SysDept> children = deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                    .apply("FIND_IN_SET({0}, ancestors)", deptId)
                    .eq(SysDept::getStatus, Constants.STATUS_NORMAL));
            children.forEach(d -> ids.add(d.getId()));
            return ids;
        }
        return Set.of();
    }

    /**
     * 取当前令牌版本号。
     * 必须走 Redis 而不是返回默认值：缓存一旦失效（Redis 重启 / key 过期），
     * 过滤器就会回源重新构建 LoginUser，若这里填 0 就会和 token 里真实的 tv 对不上，
     * 表现为「刚登录就被提示异地登录」。
     */
    private Integer currentTokenVersion(Long userId) {
        return tokenVersionManager.current(userId);
    }

    /**
     * 重新加载（角色权限变更后刷新会话用）。
     * 保留原 tokenVersion，避免刷新权限时把用户踢下线。
     */
    public LoginUser reload(LoginUser old) {
        LoginUser fresh = loadByUserId(old.getUserId());
        if (fresh == null) {
            return null;
        }
        fresh.setTokenVersion(old.getTokenVersion());
        fresh.setJti(old.getJti());
        return fresh;
    }

    /** 供批量场景使用：一次性拿到多个部门的名称，避免 N+1 */
    public List<Long> allChildDeptIds(Long deptId) {
        List<Long> ids = new ArrayList<>();
        ids.add(deptId);
        deptMapper.selectList(new LambdaQueryWrapper<SysDept>()
                        .apply("FIND_IN_SET({0}, ancestors)", deptId))
                .forEach(d -> ids.add(d.getId()));
        return ids;
    }
}
