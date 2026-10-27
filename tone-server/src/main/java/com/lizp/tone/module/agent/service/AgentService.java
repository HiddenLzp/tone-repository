package com.lizp.tone.module.agent.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lizp.tone.common.BaseException;
import com.lizp.tone.common.ErrorCode;
import com.lizp.tone.module.agent.config.AgentProperties;
import com.lizp.tone.module.agent.dto.CheckRequest;
import com.lizp.tone.module.agent.dto.RefineRequest;
import com.lizp.tone.module.agent.entity.AppCheckMessage;
import com.lizp.tone.module.agent.entity.AppCheckRecord;
import com.lizp.tone.module.agent.mapper.AppCheckMessageMapper;
import com.lizp.tone.module.agent.mapper.AppCheckRecordMapper;
import com.lizp.tone.module.agent.stream.AgentProgressListener;
import com.lizp.tone.module.agent.vo.CheckResultVO;
import com.lizp.tone.module.agent.vo.IssueVO;
import com.lizp.tone.module.agent.vo.RecentRecordVO;
import com.lizp.tone.module.agent.vo.RecordDetailVO;
import com.lizp.tone.module.agent.vo.RefineResultVO;
import com.lizp.tone.module.agent.vo.ScoreVO;
import com.lizp.tone.module.agent.vo.VersionVO;
import com.lizp.tone.module.wxapi.service.QuotaService;
import com.lizp.tone.module.wxapi.service.WxSecService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.util.List;

/**
 * 语气检查业务流：敏感词硬拦截 → 配额 → Agent 循环 → 落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentService {

    private static final List<String> SCENES = List.of("workplace", "social", "relationship", "moments", "service");

    private final AgentOrchestrator orchestrator;
    private final SensitiveWordService sensitiveWordService;
    private final AgentProperties agentProperties;
    private final QuotaService quotaService;
    private final WxSecService wxSecService;
    private final AppCheckRecordMapper recordMapper;
    private final AppCheckMessageMapper messageMapper;
    private final ObjectMapper objectMapper;

    /** 同步入口：无进度监听的便捷重载 */
    public CheckResultVO check(String openId, CheckRequest request) {
        return check(openId, request, AgentProgressListener.noop());
    }

    /**
     * 带进度监听的检查入口：业务规则（长度/场景/敏感词/配额）与流式无关，统一收口在这里；
     * 监听器一路传到工具层，SSE 接口传"写事件"实现，同步接口传 noop。
     */
    public CheckResultVO check(String openId, CheckRequest request, AgentProgressListener listener) {
        String text = request.getText() == null ? "" : request.getText().trim();
        String scene = request.getScene();
        if (text.isEmpty()) {
            throw new BaseException(ErrorCode.TEXT_EMPTY);
        }
        if (text.length() > agentProperties.getMaxTextLength()) {
            throw new BaseException(ErrorCode.TEXT_TOO_LONG);
        }
        if (!SCENES.contains(scene)) {
            throw new BaseException(ErrorCode.SCENE_INVALID);
        }

        // 敏感词硬拦截：不进 Agent 循环、不消耗配额；命中词写入 issues 供管理台审计
        List<String> hits = sensitiveWordService.findHits(text);
        // 平台侧内容安全（msgSecCheck）：UGC 合规硬要求，本地词库只是前置自保；
        // 检测不通过与本地词库同样走 blocked 路径，不消耗配额
        boolean secPassed = wxSecService.checkText(openId, text);
        if (!hits.isEmpty() || !secPassed) {
            if (!secPassed) {
                hits = List.of("内容安全检测未通过");
            }
            // 组装 issues 字段
            String blockedIssues = toJson(hits.stream()
                    .map(hit -> new IssueVO("sensitive", "high", hit, "命中敏感词或未通过微信内容安全检测，已按安全策略拦截，未进入改写"))
                    .toList());

            // 组装 语气检查记录。
            AppCheckRecord blocked = persist(openId, scene, text, null, blockedIssues, null, 0, 0, 0, "blocked");
            return new CheckResultVO(blocked.getId(), true, hits, null, null, null, 0, scene);
        }

        // 配额：先消费，Agent 失败退还
        String bucket = quotaService.consume(openId);
        AgentOrchestrator.AgentRunResult run;
        try {
            run = orchestrator.run(scene, text, listener);
        } catch (RuntimeException e) {
            // 失败也落库：看板要能看到失败率与失败请求的原文，否则请求量是失真的
            persist(openId, scene, text, null, null, null, 0, 0, 0, "error");
            quotaService.refund(openId, bucket);
            // 模型服务的 401/429/5xx 直接透传成"第三方调用失败"，不要落到"系统异常"
            if (e.getClass().getName().startsWith("com.openai.errors.")
                    || e.getClass().getName().startsWith("org.springframework.ai.")) {
                throw new BaseException(ErrorCode.THIRD_PARTY_ERROR,
                        "模型服务调用失败：" + e.getMessage());
            }
            throw e;
        }

        AppCheckRecord record = persist(openId, scene, text,
                toJson(run.scores()), toJson(run.issues()), toJson(run.versions()),
                run.rounds(), run.promptTokens(), run.completionTokens(), "done");
        saveMessage(record.getId(), "user", text, 0);
        saveMessage(record.getId(), "assistant", toJson(run.versions()), 0);

        return new CheckResultVO(record.getId(), false, List.of(),
                run.scores(), run.issues(), run.versions(), run.rounds(), scene);
    }

    /** 同步入口：不关心 token 增量的便捷重载 */
    public RefineResultVO refine(String openId, RefineRequest request) {
        return refine(openId, request, null);
    }

    /**
     * 带增量回调的微调入口：onDelta 每收到一个模型输出片段回调一次（可为 null）。
     *
     * <p>持久化时机的设计：落库只发生在流**结束**后——增量阶段模型输出是 {@code {"text":...}}
     * 的残缺 JSON，直接落库会把半截 JSON 当最终文本；同时 model/usage 只在末帧可取。
     * 前端在 delta 事件里拿到的文本只用于"逐字渲染"，最终以 result 事件为准。
     */
    public RefineResultVO refine(String openId, RefineRequest request,
                                 java.util.function.Consumer<String> onDelta) {
        if (!StringUtils.hasText(request.getInstruction())) {
            throw new BaseException(ErrorCode.REFINE_EMPTY);
        }
        AppCheckRecord record = recordMapper.selectById(request.getRecordId());
        // selectById 由 @TableLogic 自动过滤已删除记录
        if (record == null) {
            throw new BaseException(ErrorCode.RECORD_NOT_FOUND);
        }
        if (!openId.equals(record.getOpenId())) {
            throw new BaseException(ErrorCode.RECORD_NOT_FOUND);
        }

        String currentText = StringUtils.hasText(record.getFinalText())
                ? record.getFinalText()
                : latestVersionText(record);

        AgentOrchestrator.RefineRun run = orchestrator.refineStream(currentText, request.getInstruction().trim(), onDelta);
        String finalText = orchestrator.parseRefineText(run.text());

        // 改写结果同样属于 UGC：展示前过微信内容安全。不通过时不落库、不改动现有 finalText
        // （异常抛出由全局处理器转成 31004，前端 toast；微调本身不消耗配额，无需退还）
        if (!wxSecService.checkText(openId, finalText)) {
            throw new BaseException(ErrorCode.SENSITIVE_BLOCKED, "改写结果未通过微信内容安全检测，请调整微调方向后重试");
        }

        record.setFinalText(finalText);
        record.setPromptTokens(nz(record.getPromptTokens()) + run.promptTokens());
        record.setCompletionTokens(nz(record.getCompletionTokens()) + run.completionTokens());
        recordMapper.updateById(record);

        saveMessage(record.getId(), "user", request.getInstruction(), 1);
        saveMessage(record.getId(), "assistant", finalText, 1);

        return new RefineResultVO(record.getId(), finalText, quotaService.snapshot(openId).remain());
    }

    /**
     * 版本回退：把最终采用文本切回用户选定的历史版本。
     * 服务端同步改 finalText，保证下一次微调的上下文与用户看到的一致。
     */
    public RefineResultVO restore(String openId, Long recordId, String text) {
        AppCheckRecord record = recordMapper.selectById(recordId);
        if (record == null || !openId.equals(record.getOpenId())) {
            throw new BaseException(ErrorCode.RECORD_NOT_FOUND);
        }
        if (!StringUtils.hasText(text)) {
            throw new BaseException(ErrorCode.REFINE_EMPTY);
        }
        record.setFinalText(text.trim());
        recordMapper.updateById(record);
        saveMessage(record.getId(), "user", "（采用历史版本）", 1);
        saveMessage(record.getId(), "assistant", record.getFinalText(), 1);
        return new RefineResultVO(record.getId(), record.getFinalText(), quotaService.snapshot(openId).remain());
    }

    private String latestVersionText(AppCheckRecord record) {
        try {
            List<VersionVO> versions = objectMapper.readValue(
                    record.getVersions() == null ? "[]" : record.getVersions(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, VersionVO.class));
            return versions.isEmpty() ? record.getOriginalText() : versions.get(versions.size() - 1).text();
        } catch (Exception e) {
            return record.getOriginalText();
        }
    }

    /**
     * 当前用户的最近 N 条记录（首页"最近记录"卡片的数据源）。
     *
     * <p>为什么做后端接口而不是继续读小程序本地缓存：本地 storage 按 appid 隔离且可被
     * 清缓存清掉（评审实测：换了真实 appid 后本地历史清零，但后端记录都在），
     * 后端才是换设备/清缓存/切 appid 都不丢的事实源。
     * 只返回 done 状态——与原本地历史语义一致（blocked/error 不是"成功检查"）。
     */
    public List<RecentRecordVO> recent(String openId, int limit) {
        int capped = Math.max(1, Math.min(limit, 20));
        List<AppCheckRecord> rows = recordMapper.selectList(
                Wrappers.<AppCheckRecord>lambdaQuery()
                        .eq(AppCheckRecord::getOpenId, openId)
                        .eq(AppCheckRecord::getStatus, "done")
                        .orderByDesc(AppCheckRecord::getId)
                        .last("LIMIT " + capped));
        return rows.stream().map(this::toRecentVO).toList();
    }

    /**
     * 单条记录详情（含对话明细）：本地缓存缺失时的回源接口，
     * 结果页拿到后会写回本地历史，后续微调/复制照常工作。
     * 归属校验：只能看自己的记录，别人的 id 一律按不存在处理（不泄露存在性）。
     */
    public RecordDetailVO recordDetail(String openId, Long recordId) {
        AppCheckRecord record = recordMapper.selectById(recordId);
        if (record == null || !openId.equals(record.getOpenId())) {
            throw new BaseException(ErrorCode.RECORD_NOT_FOUND);
        }
        List<AppCheckMessage> messages = messageMapper.selectList(
                Wrappers.<AppCheckMessage>lambdaQuery()
                        .eq(AppCheckMessage::getRecordId, recordId)
                        .orderByAsc(AppCheckMessage::getId));
        List<RecordDetailVO.MessageVO> messageVOs = messages.stream()
                .map(m -> new RecordDetailVO.MessageVO(m.getRole(), m.getContent(), m.getRefineFlag()))
                .toList();
        return new RecordDetailVO(toRecentVO(record), messageVOs);
    }

    /** 实体 → 前端条目：JSON 字符串在此解析成对象（管理台那条链路的 Jackson3 坑在这里同样适用） */
    private RecentRecordVO toRecentVO(AppCheckRecord record) {
        return new RecentRecordVO(
                record.getId(),
                record.getScene(),
                record.getOriginalText(),
                parseJson(record.getScores()),
                parseList(record.getIssues(), IssueVO.class),
                parseList(record.getVersions(), VersionVO.class),
                record.getFinalText(),
                record.getCreateTime() == null
                        ? null
                        : record.getCreateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                record.getStatus());
    }

    private Object parseJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    private <T> List<T> parseList(String json, Class<T> type) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, type));
        } catch (Exception e) {
            return List.of();
        }
    }

    private AppCheckRecord persist(String openId, String scene, String originalText,
                                   String scores, String issues, String versions,
                                   int rounds, long promptTokens, long completionTokens, String status) {
        AppCheckRecord record = new AppCheckRecord();
        record.setOpenId(openId);
        record.setScene(scene);
        record.setOriginalText(originalText);
        record.setScores(scores);
        record.setIssues(issues);
        record.setVersions(versions);
        record.setSelectedVersion(-1);
        record.setRounds(rounds);
        record.setPromptTokens(promptTokens);
        record.setCompletionTokens(completionTokens);
        record.setStatus(status);
        recordMapper.insert(record);
        return record;
    }

    private void saveMessage(Long recordId, String role, String content, int refineFlag) {
        AppCheckMessage message = new AppCheckMessage();
        message.setRecordId(recordId);
        message.setRole(role);
        message.setContent(content);
        message.setRefineFlag(refineFlag);
        messageMapper.insert(message);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private long nz(Long v) {
        return v == null ? 0 : v;
    }
}
