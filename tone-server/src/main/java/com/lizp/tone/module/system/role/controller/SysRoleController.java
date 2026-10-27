package com.lizp.tone.module.system.role.controller;

import com.lizp.tone.aspect.BusinessType;
import com.lizp.tone.aspect.Log;
import com.lizp.tone.common.PageResult;
import com.lizp.tone.common.R;
import com.lizp.tone.module.system.role.SysRoleService;
import com.lizp.tone.module.system.role.entity.SysRole;
import com.lizp.tone.security.authz.RequiresPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

/** 角色管理 */
@Tag(name = "角色管理")
@RestController
@RequestMapping("/system/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    @Operation(summary = "分页查询角色")
    @RequiresPermissions("system:role:list")
    @GetMapping("/page")
    public R<PageResult<SysRole>> page(@RequestParam(defaultValue = "1") long pageNum,
                                       @RequestParam(defaultValue = "10") long pageSize,
                                       @RequestParam(required = false) String roleName,
                                       @RequestParam(required = false) String roleCode,
                                       @RequestParam(required = false) Integer status) {
        return R.ok(roleService.page(pageNum, pageSize, roleName, roleCode, status));
    }

    @Operation(summary = "启用角色列表", description = "下拉框数据源")
    @GetMapping("/options")
    public R<List<SysRole>> options() {
        return R.ok(roleService.listEnabled());
    }

    @Operation(summary = "角色详情")
    @RequiresPermissions("system:role:query")
    @GetMapping("/{id}")
    public R<SysRole> detail(@PathVariable Long id) {
        return R.ok(roleService.getById(id));
    }

    @Operation(summary = "新增角色")
    @RequiresPermissions("system:role:add")
    @Log(title = "角色管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@RequestBody SysRole role) {
        return R.ok(roleService.add(role));
    }

    @Operation(summary = "修改角色")
    @RequiresPermissions("system:role:edit")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> update(@RequestBody SysRole role) {
        roleService.update(role);
        return R.ok();
    }

    @Operation(summary = "删除角色")
    @RequiresPermissions("system:role:remove")
    @Log(title = "角色管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return R.ok();
    }

    @Operation(summary = "分配权限（菜单/按钮）")
    @RequiresPermissions("system:role:assignPerm")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @PutMapping("/{id}/menus")
    public R<Void> assignMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        roleService.assignMenus(id, menuIds);
        return R.ok();
    }

    @Operation(summary = "角色已分配的数据权限部门", description = "data_scope=2 自定义时用于回显")
    @RequiresPermissions("system:role:assignPerm")
    @GetMapping("/{id}/depts")
    public R<List<Long>> roleDeptIds(@PathVariable Long id) {
        return R.ok(roleService.listDeptIdsByRoleId(id));
    }

    @Operation(summary = "分配数据权限部门")
    @RequiresPermissions("system:role:assignPerm")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @PutMapping("/{id}/depts")
    public R<Void> assignDepts(@PathVariable Long id, @RequestBody List<Long> deptIds) {
        roleService.assignDepts(id, deptIds);
        return R.ok();
    }
}
