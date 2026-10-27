package com.lizp.tone.module.monitor.controller;

import com.lizp.tone.aspect.BusinessType;
import com.lizp.tone.aspect.Log;
import com.lizp.tone.common.PageResult;
import com.lizp.tone.common.R;
import com.lizp.tone.module.monitor.LoginLogService;
import com.lizp.tone.module.monitor.OperationLogService;
import com.lizp.tone.module.monitor.dto.LoginLogQuery;
import com.lizp.tone.module.monitor.dto.OperationLogQuery;
import com.lizp.tone.module.monitor.entity.SysLoginLog;
import com.lizp.tone.module.monitor.entity.SysOperationLog;
import com.lizp.tone.security.authz.RequiresPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/** 日志查询 */
@Tag(name = "日志监控")
@RestController
@RequestMapping("/monitor")
@RequiredArgsConstructor
public class MonitorLogController {

    private final OperationLogService operationLogService;
    private final LoginLogService loginLogService;

    @Operation(summary = "操作日志分页")
    @RequiresPermissions("monitor:oplog:list")
    @GetMapping("/oplog/page")
    public R<PageResult<SysOperationLog>> pageOperationLog(@Valid OperationLogQuery query) {
        return R.ok(operationLogService.page(query));
    }

    @Operation(summary = "删除操作日志")
    @RequiresPermissions("monitor:oplog:remove")
    @Log(title = "操作日志", businessType = BusinessType.DELETE)
    @DeleteMapping("/oplog/{ids}")
    public R<Void> deleteOperationLog(@PathVariable String ids) {
        operationLogService.deleteByIds(parseIds(ids));
        return R.ok();
    }

    @Operation(summary = "登录日志分页", description = "支持按账号、IP、状态、时间段查询")
    @RequiresPermissions("monitor:loginlog:list")
    @GetMapping("/loginlog/page")
    public R<PageResult<SysLoginLog>> pageLoginLog(@Valid LoginLogQuery query) {
        return R.ok(loginLogService.page(query));
    }

    @Operation(summary = "删除登录日志")
    @RequiresPermissions("monitor:loginlog:remove")
    @Log(title = "登录日志", businessType = BusinessType.DELETE)
    @DeleteMapping("/loginlog/{ids}")
    public R<Void> deleteLoginLog(@PathVariable String ids) {
        loginLogService.deleteByIds(parseIds(ids));
        return R.ok();
    }

    private List<Long> parseIds(String ids) {
        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();
    }
}
