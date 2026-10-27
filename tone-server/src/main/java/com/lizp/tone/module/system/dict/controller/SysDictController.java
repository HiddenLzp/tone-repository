package com.lizp.tone.module.system.dict.controller;

import com.lizp.tone.aspect.BusinessType;
import com.lizp.tone.aspect.Log;
import com.lizp.tone.common.PageResult;
import com.lizp.tone.common.R;
import com.lizp.tone.module.system.dict.SysDictService;
import com.lizp.tone.module.system.dict.entity.SysDictData;
import com.lizp.tone.module.system.dict.entity.SysDictType;
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
import java.util.Map;

/** 字典管理 */
@Tag(name = "字典管理")
@RestController
@RequestMapping("/system/dict")
@RequiredArgsConstructor
public class SysDictController {

    private final SysDictService dictService;

    @Operation(summary = "字典类型分页")
    @RequiresPermissions("system:dict:list")
    @GetMapping("/type/page")
    public R<PageResult<SysDictType>> pageType(@RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "10") long pageSize,
                                               @RequestParam(required = false) String dictName,
                                               @RequestParam(required = false) String dictType,
                                               @RequestParam(required = false) Integer status) {
        return R.ok(dictService.pageType(pageNum, pageSize, dictName, dictType, status));
    }

    @Operation(summary = "字典类型列表")
    @GetMapping("/type/list")
    public R<List<SysDictType>> listType() {
        return R.ok(dictService.listType());
    }

    @Operation(summary = "新增字典类型")
    @RequiresPermissions("system:dict:add")
    @Log(title = "字典管理", businessType = BusinessType.INSERT)
    @PostMapping("/type")
    public R<Long> addType(@RequestBody SysDictType entity) {
        return R.ok(dictService.addType(entity));
    }

    @Operation(summary = "修改字典类型")
    @RequiresPermissions("system:dict:edit")
    @Log(title = "字典管理", businessType = BusinessType.UPDATE)
    @PutMapping("/type")
    public R<Void> updateType(@RequestBody SysDictType entity) {
        dictService.updateType(entity);
        return R.ok();
    }

    @Operation(summary = "删除字典类型")
    @RequiresPermissions("system:dict:remove")
    @Log(title = "字典管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/type/{ids}")
    public R<Void> deleteType(@PathVariable List<Long> ids) {
        dictService.deleteType(ids);
        return R.ok();
    }

    @Operation(summary = "字典数据分页")
    @RequiresPermissions("system:dict:list")
    @GetMapping("/data/page")
    public R<PageResult<SysDictData>> pageData(@RequestParam(defaultValue = "1") long pageNum,
                                               @RequestParam(defaultValue = "10") long pageSize,
                                               @RequestParam(required = false) String dictType,
                                               @RequestParam(required = false) String dictLabel,
                                               @RequestParam(required = false) Integer status) {
        return R.ok(dictService.pageData(pageNum, pageSize, dictType, dictLabel, status));
    }

    @Operation(summary = "按类型取字典数据", description = "前端下拉框直接调用，结果带缓存")
    @GetMapping("/data/{dictType}")
    public R<List<SysDictData>> listData(@PathVariable String dictType) {
        return R.ok(dictService.listDataByType(dictType));
    }

    @Operation(summary = "字典值映射", description = "返回 {value: label}，列表页翻译字段用")
    @GetMapping("/data/{dictType}/map")
    public R<Map<String, String>> dataMap(@PathVariable String dictType) {
        return R.ok(dictService.dataMap(dictType));
    }

    @Operation(summary = "新增字典数据")
    @RequiresPermissions("system:dict:add")
    @Log(title = "字典管理", businessType = BusinessType.INSERT)
    @PostMapping("/data")
    public R<Long> addData(@RequestBody SysDictData entity) {
        return R.ok(dictService.addData(entity));
    }

    @Operation(summary = "修改字典数据")
    @RequiresPermissions("system:dict:edit")
    @Log(title = "字典管理", businessType = BusinessType.UPDATE)
    @PutMapping("/data")
    public R<Void> updateData(@RequestBody SysDictData entity) {
        dictService.updateData(entity);
        return R.ok();
    }

    @Operation(summary = "删除字典数据")
    @RequiresPermissions("system:dict:remove")
    @Log(title = "字典管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/data/{ids}")
    public R<Void> deleteData(@PathVariable List<Long> ids) {
        dictService.deleteData(ids);
        return R.ok();
    }
}
