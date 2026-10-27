package com.lizp.tone.module.system.config.controller;

import com.lizp.tone.aspect.BusinessType;
import com.lizp.tone.aspect.Log;
import com.lizp.tone.common.PageResult;
import com.lizp.tone.common.R;
import com.lizp.tone.module.system.config.SysConfigService;
import com.lizp.tone.module.system.config.entity.SysConfig;
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

/** 系统参数配置 */
@Tag(name = "参数配置")
@RestController
@RequestMapping("/system/config")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigService configService;

    @Operation(summary = "参数分页")
    @RequiresPermissions("system:config:list")
    @GetMapping("/page")
    public R<PageResult<SysConfig>> page(@RequestParam(defaultValue = "1") long pageNum,
                                         @RequestParam(defaultValue = "10") long pageSize,
                                         @RequestParam(required = false) String configName,
                                         @RequestParam(required = false) String configKey,
                                         @RequestParam(required = false) Integer configType) {
        return R.ok(configService.page(pageNum, pageSize, configName, configKey, configType));
    }

    @Operation(summary = "取参数值", description = "没配置时返回传入的默认值")
    @GetMapping("/value/{configKey}")
    public R<String> value(@PathVariable String configKey,
                           @RequestParam(required = false) String defaultValue) {
        return R.ok(configService.getValue(configKey, defaultValue));
    }

    @Operation(summary = "新增参数")
    @RequiresPermissions("system:config:add")
    @Log(title = "参数配置", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@RequestBody SysConfig entity) {
        return R.ok(configService.add(entity));
    }

    @Operation(summary = "修改参数")
    @RequiresPermissions("system:config:edit")
    @Log(title = "参数配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> update(@RequestBody SysConfig entity) {
        configService.update(entity);
        return R.ok();
    }

    @Operation(summary = "删除参数")
    @RequiresPermissions("system:config:remove")
    @Log(title = "参数配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> delete(@PathVariable List<Long> ids) {
        configService.delete(ids);
        return R.ok();
    }
}
