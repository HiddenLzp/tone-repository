package com.lizp.tone.module.system.dept.controller;

import com.lizp.tone.aspect.BusinessType;
import com.lizp.tone.aspect.Log;
import com.lizp.tone.common.R;
import com.lizp.tone.module.system.dept.SysDeptService;
import com.lizp.tone.module.system.dept.entity.SysDept;
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

/** 部门管理 */
@Tag(name = "部门管理")
@RestController
@RequestMapping("/system/dept")
@RequiredArgsConstructor
public class SysDeptController {

    private final SysDeptService deptService;

    @Operation(summary = "部门列表")
    @RequiresPermissions("system:dept:list")
    @GetMapping("/list")
    public R<List<SysDept>> list(@RequestParam(required = false) String deptName,
                                 @RequestParam(required = false) Integer status) {
        return R.ok(deptService.list(deptName, status));
    }

    @Operation(summary = "部门详情")
    @RequiresPermissions("system:dept:query")
    @GetMapping("/{id}")
    public R<SysDept> detail(@PathVariable Long id) {
        return R.ok(deptService.getById(id));
    }

    @Operation(summary = "新增部门")
    @RequiresPermissions("system:dept:add")
    @Log(title = "部门管理", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@RequestBody SysDept dept) {
        return R.ok(deptService.add(dept));
    }

    @Operation(summary = "修改部门")
    @RequiresPermissions("system:dept:edit")
    @Log(title = "部门管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> update(@RequestBody SysDept dept) {
        deptService.update(dept);
        return R.ok();
    }

    @Operation(summary = "删除部门")
    @RequiresPermissions("system:dept:remove")
    @Log(title = "部门管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        deptService.delete(id);
        return R.ok();
    }
}
