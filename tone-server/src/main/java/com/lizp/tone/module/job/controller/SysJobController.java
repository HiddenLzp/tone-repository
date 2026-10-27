package com.lizp.tone.module.job.controller;

import com.lizp.tone.aspect.BusinessType;
import com.lizp.tone.aspect.Log;
import com.lizp.tone.common.PageResult;
import com.lizp.tone.common.R;
import com.lizp.tone.module.job.SysJobService;
import com.lizp.tone.module.job.entity.SysJob;
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

/** 定时任务 */
@Tag(name = "定时任务")
@RestController
@RequestMapping("/job/task")
@RequiredArgsConstructor
public class SysJobController {

    private final SysJobService jobService;

    @Operation(summary = "任务分页")
    @RequiresPermissions("job:task:list")
    @GetMapping("/page")
    public R<PageResult<SysJob>> page(@RequestParam(defaultValue = "1") long pageNum,
                                      @RequestParam(defaultValue = "10") long pageSize,
                                      @RequestParam(required = false) String jobName,
                                      @RequestParam(required = false) String jobGroup,
                                      @RequestParam(required = false) Integer status) {
        return R.ok(jobService.page(pageNum, pageSize, jobName, jobGroup, status));
    }

    @Operation(summary = "任务详情")
    @RequiresPermissions("job:task:query")
    @GetMapping("/{id}")
    public R<SysJob> detail(@PathVariable Long id) {
        return R.ok(jobService.getById(id));
    }

    @Operation(summary = "新增任务")
    @RequiresPermissions("job:task:add")
    @Log(title = "定时任务", businessType = BusinessType.INSERT)
    @PostMapping
    public R<Long> add(@RequestBody SysJob job) {
        return R.ok(jobService.add(job));
    }

    @Operation(summary = "修改任务", description = "修改后自动重新注册调度，无需重启")
    @RequiresPermissions("job:task:edit")
    @Log(title = "定时任务", businessType = BusinessType.UPDATE)
    @PutMapping
    public R<Void> update(@RequestBody SysJob job) {
        jobService.update(job);
        return R.ok();
    }

    @Operation(summary = "删除任务")
    @RequiresPermissions("job:task:remove")
    @Log(title = "定时任务", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> delete(@PathVariable List<Long> ids) {
        jobService.delete(ids);
        return R.ok();
    }

    @Operation(summary = "启停任务")
    @RequiresPermissions("job:task:changeStatus")
    @Log(title = "定时任务", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        jobService.changeStatus(id, status);
        return R.ok();
    }

    @Operation(summary = "立即执行一次")
    @RequiresPermissions("job:task:run")
    @Log(title = "定时任务", businessType = BusinessType.OTHER)
    @PostMapping("/{id}/run")
    public R<Void> run(@PathVariable Long id) {
        jobService.runOnce(id);
        return R.ok();
    }
}
