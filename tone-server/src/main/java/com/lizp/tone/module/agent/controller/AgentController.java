package com.lizp.tone.module.agent.controller;

import com.lizp.tone.common.R;
import com.lizp.tone.module.agent.dto.CheckRequest;
import com.lizp.tone.module.agent.dto.RefineRequest;
import com.lizp.tone.module.agent.dto.RestoreRequest;
import com.lizp.tone.module.agent.service.AgentService;
import com.lizp.tone.module.agent.vo.CheckResultVO;
import com.lizp.tone.module.agent.vo.RecordDetailVO;
import com.lizp.tone.module.agent.vo.RecentRecordVO;
import com.lizp.tone.module.agent.vo.RefineResultVO;
import com.lizp.tone.module.wxapi.auth.WxUserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 语气检查 Agent 接口（小程序端）。
 */
@Tag(name = "语气检查 Agent")
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @PostMapping("/check")
    @Operation(summary = "语气检查：诊断 + 场景校验 + 3 个改写版本")
    public R<CheckResultVO> check(@Valid @RequestBody CheckRequest request) {
        return R.ok(agentService.check(WxUserContext.getOpenId(), request));
    }

    @PostMapping("/refine")
    @Operation(summary = "微调：对选定版本说'再委婉一点'等，Agent 迭代改写")
    public R<RefineResultVO> refine(@Valid @RequestBody RefineRequest request) {
        return R.ok(agentService.refine(WxUserContext.getOpenId(), request));
    }

    @PostMapping("/restore")
    @Operation(summary = "版本回退：把最终采用文本切回选定的历史版本")
    public R<RefineResultVO> restore(@Valid @RequestBody RestoreRequest request) {
        return R.ok(agentService.restore(WxUserContext.getOpenId(), request.getRecordId(), request.getText()));
    }

    @GetMapping("/records/recent")
    @Operation(summary = "最近记录：首页卡片数据源，以后端为事实源（本地缓存只做兜底）")
    public R<List<RecentRecordVO>> recent(@RequestParam(defaultValue = "3") int limit) {
        return R.ok(agentService.recent(WxUserContext.getOpenId(), limit));
    }

    @GetMapping("/record/{recordId}")
    @Operation(summary = "记录详情（含对话明细）：本地缓存缺失时的回源接口，仅限本人记录")
    public R<RecordDetailVO> recordDetail(@PathVariable Long recordId) {
        return R.ok(agentService.recordDetail(WxUserContext.getOpenId(), recordId));
    }
}
