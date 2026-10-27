package com.lizp.tone.module.system.menu.controller;

import com.lizp.tone.aspect.BusinessType;
import com.lizp.tone.aspect.Log;
import com.lizp.tone.common.R;
import com.lizp.tone.module.system.menu.SysMenuService;
import com.lizp.tone.module.system.menu.entity.SysMenu;
import com.lizp.tone.security.LoginUser;
import com.lizp.tone.security.authz.RequiresPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 菜单/资源管理 */
@Tag(name = "菜单管理")
@RestController
@RequestMapping("/system/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService menuService;

    @Operation(summary = "菜单列表（平铺）")
    @RequiresPermissions("system:menu:list")
    @GetMapping("/list")
    public R<List<SysMenu>> list(@RequestParam(required = false) String menuName,
                                 @RequestParam(required = false) Integer status) {
        return R.ok(menuService.list(menuName, status));
    }

    @Operation(summary = "菜单树")
    @RequiresPermissions("system:menu:list")
    @GetMapping("/tree")
    public R<List<SysMenuService.MenuNode>> tree(@RequestParam(required = false) String menuName,
                                                 @RequestParam(required = false) Integer status) {
        return R.ok(menuService.buildTree(menuService.list(menuName, status)));
    }

    /**
     * 当前用户的路由菜单：前端动态路由数据源，登录后即可调用，不需要分配任何权限标识
     * ——拿不到菜单就进不了系统，给菜单再加一道权限是死锁。
     * 返回的是按角色过滤后的结果，没有角色的用户拿到空数组。
     */
    @Operation(summary = "当前用户的路由菜单", description = "前端动态路由数据源，登录后即可访问，按角色过滤")
    @GetMapping("/routes")
    public R<List<SysMenuService.MenuNode>> routes() {
        return R.ok(menuService.buildTree(menuService.listUserMenus(currentUser())));
    }

    /**
     * 菜单详情。
     *
     * <p>路径刻意写成 /detail/{id} 而不是 /{id}：权限规则是按 Ant 模式匹配的，
     * /{id} 会把 /routes 一起匹配上（单段路径占位符不认数字），
     * 结果就是"拉自己的菜单"也要 system:menu:query 权限。加一段固定前缀彻底隔开。
     */
    @Operation(summary = "菜单详情")
    @RequiresPermissions("system:menu:query")
    @GetMapping("/detail/{id}")
    public R<SysMenu> detail(@PathVariable Long id) {
        return R.ok(menuService.getById(id));
    }

    @Operation(summary = "新增菜单")
    @RequiresPermissions("system:menu:add")
    @Log(title = "菜单管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@RequestBody SysMenu menu) {
        return R.ok(menuService.add(menu));
    }

    @Operation(summary = "修改菜单")
    @RequiresPermissions("system:menu:edit")
    @Log(title = "菜单管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> update(@RequestBody SysMenu menu) {
        menuService.update(menu);
        return R.ok();
    }

    @Operation(summary = "删除菜单")
    @RequiresPermissions("system:menu:remove")
    @Log(title = "菜单管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return R.ok();
    }

    @Operation(summary = "角色已分配的菜单ID", description = "分配权限时用于回显勾选状态")
    @RequiresPermissions("system:role:assignPerm")
    @GetMapping("/role/{roleId}")
    public R<List<Long>> roleMenuIds(@PathVariable Long roleId) {
        return R.ok(menuService.listMenuIdsByRoleId(roleId));
    }

    private LoginUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        return null;
    }
}
