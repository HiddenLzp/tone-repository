package com.lizp.tone.module.system.user.controller;

import com.lizp.tone.aspect.BusinessType;
import com.lizp.tone.aspect.Log;
import com.lizp.tone.common.R;
import com.lizp.tone.common.PageResult;
import com.lizp.tone.module.system.user.SysUserService;
import com.lizp.tone.module.system.user.dto.UserQuery;
import com.lizp.tone.module.system.user.dto.UserSaveRequest;
import com.lizp.tone.module.system.user.vo.UserVO;
import com.lizp.tone.security.authz.RequiresPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.util.Arrays;
import java.util.List;

/** 用户管理 */
@Tag(name = "用户管理")
@RestController
@RequestMapping("/system/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;

    @Operation(summary = "分页查询用户")
    @RequiresPermissions("system:user:list")
    @GetMapping("/page")
    public R<PageResult<UserVO>> page(@Valid UserQuery query) {
        return R.ok(userService.page(query));
    }

    @Operation(summary = "用户详情")
    @RequiresPermissions("system:user:query")
    @GetMapping("/{id}")
    public R<UserVO> detail(@PathVariable Long id) {
        return R.ok(userService.getDetail(id));
    }

    @Operation(summary = "新增用户", description = "默认密码取自 base.user.default-password，首次登录强制改密")
    @RequiresPermissions("system:user:add")
    @Log(title = "用户管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@RequestBody @Valid UserSaveRequest request) {
        return R.ok(userService.add(request));
    }

    @Operation(summary = "修改用户")
    @RequiresPermissions("system:user:edit")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> update(@RequestBody @Valid UserSaveRequest request) {
        userService.update(request);
        return R.ok();
    }

    @Operation(summary = "删除用户", description = "支持逗号分隔的多个ID")
    @RequiresPermissions("system:user:remove")
    @Log(title = "用户管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> delete(@PathVariable String ids) {
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
        userService.delete(idList);
        return R.ok();
    }

    @Operation(summary = "启用/停用用户")
    @RequiresPermissions("system:user:edit")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.changeStatus(id, status);
        return R.ok();
    }

    @Operation(summary = "重置密码", description = "重置为默认密码并要求下次登录修改")
    @RequiresPermissions("system:user:resetPwd")
    @Log(title = "用户管理", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/reset-password")
    public R<Void> resetPassword(@PathVariable Long id) {
        userService.resetPassword(id);
        return R.ok();
    }

    @Operation(summary = "分配角色")
    @RequiresPermissions("system:user:assignRole")
    @Log(title = "用户管理", businessType = BusinessType.GRANT)
    @PutMapping("/{id}/roles")
    public R<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        userService.assignRoles(id, roleIds);
        return R.ok();
    }
}
