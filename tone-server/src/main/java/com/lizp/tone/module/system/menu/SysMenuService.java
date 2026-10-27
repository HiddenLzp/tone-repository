package com.lizp.tone.module.system.menu;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lizp.tone.common.BaseException;
import com.lizp.tone.common.Constants;
import com.lizp.tone.common.ErrorCode;
import com.lizp.tone.module.system.menu.entity.SysMenu;
import com.lizp.tone.module.system.menu.mapper.SysMenuMapper;
import com.lizp.tone.module.system.role.entity.SysRoleMenu;
import com.lizp.tone.module.system.role.mapper.SysRoleMenuMapper;
import com.lizp.tone.security.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 菜单/资源服务 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysMenuService {

    private final SysMenuMapper menuMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    public List<SysMenu> list(String menuName, Integer status) {
        return menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .like(StrUtil.isNotBlank(menuName), SysMenu::getMenuName, menuName)
                .eq(status != null, SysMenu::getStatus, status)
                .orderByAsc(SysMenu::getOrderNum)
                .orderByAsc(SysMenu::getId));
    }

    /** 只有菜单与目录参与路由，按钮(type=3)不生成前端路由 */
    public List<SysMenu> listRouteMenus() {
        return menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .in(SysMenu::getMenuType, List.of(Constants.MENU_TYPE_DIR, Constants.MENU_TYPE_MENU))
                .eq(SysMenu::getStatus, Constants.STATUS_NORMAL)
                .orderByAsc(SysMenu::getOrderNum));
    }

    public SysMenu getById(Long id) {
        return menuMapper.selectById(id);
    }

    /**
     * 当前用户的可见菜单（前端动态路由数据源）。
     * 超级管理员拿全部；其它用户按「角色 → 资源」过滤。
     */
    public List<SysMenu> listUserMenus(LoginUser loginUser) {
        List<SysMenu> all = listRouteMenus();
        if (loginUser == null) {
            return List.of();
        }
        if (loginUser.isSuperAdmin()) {
            return all;
        }
        Set<Long> menuIds = loginUser.getMenuIds();
        if (menuIds == null || menuIds.isEmpty()) {
            return List.of();
        }
        return all.stream().filter(m -> menuIds.contains(m.getId())).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(SysMenu menu) {
        checkPermUnique(menu.getPerms(), null);
        menuMapper.insert(menu);
        return menu.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SysMenu menu) {
        if (menu.getId().equals(menu.getParentId())) {
            throw new BaseException(ErrorCode.PARAM_INVALID, "上级菜单不能是自己");
        }
        checkPermUnique(menu.getPerms(), menu.getId());
        menuMapper.updateById(menu);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long childCount = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getParentId, id));
        if (childCount > 0) {
            throw new BaseException(ErrorCode.MENU_HAS_CHILD);
        }
        menuMapper.deleteById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getMenuId, id));
    }

    /** 角色可见的菜单ID集合，前端分配权限时回显用 */
    public List<Long> listMenuIdsByRoleId(Long roleId) {
        return roleMenuMapper.selectList(new LambdaQueryWrapper<SysRoleMenu>()
                        .eq(SysRoleMenu::getRoleId, roleId))
                .stream().map(SysRoleMenu::getMenuId).toList();
    }

    /** 把平铺列表组装成树 */
    public List<MenuNode> buildTree(List<SysMenu> menus) {
        List<MenuNode> nodes = menus.stream()
                .map(SysMenuService::toNode)
                .sorted(Comparator.comparingInt(MenuNode::orderNum))
                .collect(Collectors.toCollection(ArrayList::new));
        java.util.Map<Long, MenuNode> map = nodes.stream()
                .collect(Collectors.toMap(MenuNode::id, n -> n, (a, b) -> a));
        List<MenuNode> roots = new ArrayList<>();
        for (MenuNode node : nodes) {
            Long parentId = node.parentId == null ? 0L : node.parentId;
            MenuNode parent = map.get(parentId);
            if (parent == null || parent.id.equals(node.id)) {
                roots.add(node);
            } else {
                parent.children.add(node);
            }
        }
        return roots;
    }

    private static MenuNode toNode(SysMenu menu) {
        return new MenuNode(
                menu.getId(),
                menu.getParentId(),
                menu.getMenuName(),
                menu.getMenuType(),
                menu.getPath(),
                menu.getComponent(),
                menu.getPerms(),
                menu.getIcon(),
                menu.getOrderNum() == null ? 0 : menu.getOrderNum(),
                menu.getVisible(),
                menu.getStatus(),
                menu.getIsCache(),
                menu.getIsExternal(),
                new ArrayList<>());
    }

    private void checkPermUnique(String perms, Long excludeId) {
        if (StrUtil.isBlank(perms)) {
            return;
        }
        Long count = menuMapper.selectCount(new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getPerms, perms)
                .ne(excludeId != null, SysMenu::getId, excludeId));
        if (count > 0) {
            throw new BaseException(ErrorCode.DATA_EXIST, "权限标识已存在：" + perms);
        }
    }

    /** 菜单树节点，直接给前端拼路由用 */
    public record MenuNode(
            Long id,
            Long parentId,
            String menuName,
            Integer menuType,
            String path,
            String component,
            String perms,
            String icon,
            int orderNum,
            Integer visible,
            Integer status,
            Integer isCache,
            Integer isExternal,
            List<MenuNode> children
    ) {
    }
}
