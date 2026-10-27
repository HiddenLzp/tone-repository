package com.lizp.tone.module.job.controller;

import com.lizp.tone.aspect.BusinessType;
import com.lizp.tone.aspect.Log;
import com.lizp.tone.common.PageResult;
import com.lizp.tone.common.R;
import com.lizp.tone.module.job.SysJobService;
import com.lizp.tone.module.job.entity.SysJobLog;
import com.lizp.tone.security.authz.RequiresPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 定时任务日志 */
@Tag(name = "任务日志")
@RestController
@RequestMapping("/job/log")
@RequiredArgsConstructor
public class SysJobLogController {

    private final SysJobService jobService;

    @Operation(summary = "任务日志分页")
    @RequiresPermissions("job:log:list")
    @GetMapping("/page")
    public R<PageResult<SysJobLog>> page(@RequestParam(defaultValue = "1") long pageNum,
                                         @RequestParam(defaultValue = "10") long pageSize,
                                         @RequestParam(required = false) Long jobId,
                                         @RequestParam(required = false) String jobName,
                                         @RequestParam(required = false) Integer status) {
        return R.ok(jobService.pageLog(pageNum, pageSize, jobId, jobName, status));
    }

    @Operation(summary = "删除任务日志")
    @RequiresPermissions("job:log:remove")
    @Log(title = "任务日志", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> delete(@PathVariable List<Long> ids) {
        jobService.deleteLog(ids);
        return R.ok();
    }
}
