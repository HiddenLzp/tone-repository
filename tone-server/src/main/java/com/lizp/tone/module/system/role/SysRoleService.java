package com.lizp.tone.module.system.role;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lizp.tone.common.BaseException;
import com.lizp.tone.common.Constants;
import com.lizp.tone.common.ErrorCode;
import com.lizp.tone.common.PageResult;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 角色服务 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysRoleDeptMapper roleDeptMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysMenuMapper menuMapper;

    public PageResult<SysRole> page(long pageNum, long pageSize, String roleName, String roleCode, Integer status) {
        IPage<SysRole> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<SysRole>()
                .like(StrUtil.isNotBlank(roleName), SysRole::getRoleName, roleName)
                .like(StrUtil.isNotBlank(roleCode), SysRole::getRoleCode, roleCode)
                .eq(status != null, SysRole::getStatus, status)
                .orderByAsc(SysRole::getRoleSort);
        return PageResult.of(roleMapper.selectPage(page, wrapper));
    }

    /** 下拉框用：只返回启用角色 */
    public List<SysRole> listEnabled() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getStatus, Constants.STATUS_NORMAL)
                .orderByAsc(SysRole::getRoleSort));
    }

    public SysRole getById(Long id) {
        return roleMapper.selectById(id);
    }

    public List<Long> listIdsByCodes(List<String> roleCodes) {
        if (CollUtil.isEmpty(roleCodes)) {
            return List.of();
        }
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>()
                        .in(SysRole::getRoleCode, roleCodes))
                .stream().map(SysRole::getId).toList();
    }

    /** 某用户拥有的角色ID集合 */
    public List<Long> listRoleIdsByUserId(Long userId) {
        return userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                        .eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(SysRole role) {
        checkFieldsUnique(role, null);
        roleMapper.insert(role);
        return role.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SysRole role) {
        // 超级管理员是兜底账号，不允许改名/停用，否则可能把自己锁在外面
        SysRole old = roleMapper.selectById(role.getId());
        if (old != null && Constants.SUPER_ADMIN_ROLE.equals(old.getRoleCode())
                && !Constants.SUPER_ADMIN_ROLE.equals(role.getRoleCode())) {
            throw new BaseException(ErrorCode.SUPER_ADMIN_PROTECTED, "超级管理员的角色编码不允许修改");
        }
        checkFieldsUnique(role, role.getId());
        roleMapper.updateById(role);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role != null && Constants.SUPER_ADMIN_ROLE.equals(role.getRoleCode())) {
            throw new BaseException(ErrorCode.SUPER_ADMIN_PROTECTED, "超级管理员不允许删除");
        }
        Long usedCount = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, id));
        if (usedCount > 0) {
            throw new BaseException(ErrorCode.ROLE_HAS_USER);
        }
        roleMapper.deleteById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        roleDeptMapper.delete(new LambdaQueryWrapper<SysRoleDept>().eq(SysRoleDept::getRoleId, id));
    }

    /**
     * 分配权限：先删后插。
     * 关联表数据量小（几百条），全量替换比逐条比对更简单也更不容易出错。
     *
     * <p>入库前必须过一遍 {@link #normalizeMenuIds}。它是「勾一个父目录 ≠ 拥有全部子孙」
     * 这条语义的唯一权威落点：前端即使因为历史数据被放大、或者接口被第三方直接调用，
     * 存储层也不会写入一个「没有任何子孙被授权」的空目录。
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long roleId, List<Long> menuIds) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BaseException(ErrorCode.DATA_NOT_FOUND, "角色不存在：" + roleId);
        }
        Set<Long> finalIds = normalizeMenuIds(menuIds);

        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, roleId));
        if (CollUtil.isEmpty(finalIds)) {
            return;
        }
        for (Long menuId : finalIds) {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            roleMenuMapper.insert(rm);
        }
    }

    /**
     * 把前端提交的任意 id 集合整理成「叶子优先 + 祖先补齐」的最小闭包。
     *
     * <ol>
     *   <li>剔除不存在的 menu_id——接口对外是开放的，不能被脏数据写穿；</li>
     *   <li>向上补齐祖先链——侧边栏要靠目录/父菜单节点才能展开到目标页；</li>
     *   <li>剔除「没有任何被授权子孙」的目录节点（menu_type=1）。这一步是关键：
     *       它让「勾父目录 = 拥有全部子孙」在存储层彻底不成立，同时能顺带修复
     *       历史脏数据（旧版前端父子联动放大写入的父 id，只要子孙没真被勾选就会被清掉）。</li>
     * </ol>
     */
    private Set<Long> normalizeMenuIds(List<Long> menuIds) {
        if (CollUtil.isEmpty(menuIds)) {
            return Set.of();
        }
        List<SysMenu> all = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>());
        Map<Long, SysMenu> byId = all.stream()
                .collect(Collectors.toMap(SysMenu::getId, Function.identity(), (a, b) -> a));

        // ① 只保留真实存在的菜单
        Set<Long> picked = menuIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .filter(byId::containsKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (picked.isEmpty()) {
            return Set.of();
        }

        // ② 向上补齐祖先链（parent_id = 0 为顶级）
        Set<Long> result = new HashSet<>(picked);
        for (Long id : new ArrayList<>(picked)) {
            Long parentId = byId.get(id).getParentId();
            int depth = 0;
            while (parentId != null && parentId != 0L && byId.containsKey(parentId) && depth++ < 32) {
                // 已在集合里说明上面都补过了；顺带防止脏数据成环时死循环
                if (!result.add(parentId)) {
                    break;
                }
                parentId = byId.get(parentId).getParentId();
            }
        }

        // ③ 自底向上标记「活着的节点」：非目录天然活着；目录只要有活着的孩子就活着
        Map<Long, List<SysMenu>> childrenByParent = all.stream()
                .filter(m -> m.getParentId() != null && m.getParentId() != 0L)
                .collect(Collectors.groupingBy(SysMenu::getParentId));
        Set<Long> alive = result.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .filter(m -> !Constants.MENU_TYPE_DIR.equals(m.getMenuType()))
                .map(SysMenu::getId)
                .collect(Collectors.toCollection(HashSet::new));

        boolean changed = true;
        while (changed) {
            changed = false;
            for (SysMenu m : all) {
                if (!result.contains(m.getId()) || alive.contains(m.getId())) {
                    continue;
                }
                boolean anyChildAlive = childrenByParent.getOrDefault(m.getId(), List.of())
                        .stream().anyMatch(c -> alive.contains(c.getId()));
                if (anyChildAlive) {
                    alive.add(m.getId());
                    changed = true;
                }
            }
        }
        result.retainAll(alive);
        return result;
    }

    /** 角色已分配的数据权限部门ID，data_scope=2 自定义时用于回显 */
    public List<Long> listDeptIdsByRoleId(Long roleId) {
        return roleDeptMapper.selectList(new LambdaQueryWrapper<SysRoleDept>()
                        .eq(SysRoleDept::getRoleId, roleId))
                .stream().map(SysRoleDept::getDeptId).toList();
    }

    /** 分配数据权限范围（仅 data_scope=2 自定义时有意义） */
    @Transactional(rollbackFor = Exception.class)
    public void assignDepts(Long roleId, List<Long> deptIds) {
        roleDeptMapper.delete(new LambdaQueryWrapper<SysRoleDept>().eq(SysRoleDept::getRoleId, roleId));
        if (CollUtil.isEmpty(deptIds)) {
            return;
        }
        deptIds.stream().distinct().forEach(deptId -> {
            SysRoleDept rd = new SysRoleDept();
            rd.setRoleId(roleId);
            rd.setDeptId(deptId);
            roleDeptMapper.insert(rd);
        });
    }

    private void checkFieldsUnique(SysRole role, Long excludeId) {
        Long nameCount = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleName, role.getRoleName())
                .ne(excludeId != null, SysRole::getId, excludeId));
        if (nameCount > 0) {
            throw new BaseException(ErrorCode.DATA_EXIST, "角色名称已存在");
        }
        Long codeCount = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, role.getRoleCode())
                .ne(excludeId != null, SysRole::getId, excludeId));
        if (codeCount > 0) {
            throw new BaseException(ErrorCode.DATA_EXIST, "角色编码已存在");
        }
    }
}
