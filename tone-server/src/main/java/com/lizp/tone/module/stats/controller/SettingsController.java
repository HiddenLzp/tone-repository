package com.lizp.tone.module.stats.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lizp.tone.common.BaseException;
import com.lizp.tone.common.ErrorCode;
import com.lizp.tone.common.R;
import com.lizp.tone.module.agent.service.AgentRuntimeConfigService;
import com.lizp.tone.module.system.config.entity.SysConfig;
import com.lizp.tone.module.system.config.mapper.SysConfigMapper;
import com.lizp.tone.module.system.config.SysConfigService;
import com.lizp.tone.security.authz.RequiresPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 运营设置：LLM 模型/温度/备用模型、配额、Token 告警阈值的在线管理。
 *
 * <p>写入走 SysConfigService.add/update（自带缓存清除），读取方 AgentRuntimeConfigService
 * 经 30 分钟缓存即时拿到新值——改完立刻生效，无需重启。
 * 白名单键严格收口在本 Controller，避免把 sys_config 变成任意系统参数的后门。
 */
@Tag(name = "运营设置")
@RestController
@RequestMapping("/agent/settings")
@RequiredArgsConstructor
public class SettingsController {

    /** 允许在线修改的参数键及中文名（展示与落库共用） */
    private static final Map<String, String> ALLOWED = Map.of(
            AgentRuntimeConfigService.KEY_MODEL, "LLM 模型",
            AgentRuntimeConfigService.KEY_TEMPERATURE, "LLM 温度",
            AgentRuntimeConfigService.KEY_FALLBACK_MODEL, "备用模型（降级）",
            AgentRuntimeConfigService.KEY_DAILY_FREE, "每日免费次数",
            AgentRuntimeConfigService.KEY_DAILY_REWARD_MAX, "每日奖励上限",
            AgentRuntimeConfigService.KEY_TOKEN_ALERT, "Token 日告警阈值");

    private final AgentRuntimeConfigService runtimeConfig;
    private final SysConfigService sysConfigService;
    private final SysConfigMapper sysConfigMapper;

    @Data
    public static class SettingsVO {
        private String model;
        private Double temperature;
        private String fallbackModel;
        private Integer dailyFreeCount;
        private Integer dailyRewardMax;
        private Long tokenAlertThreshold;
    }

    @GetMapping
    @RequiresPermissions("agent:settings:view")
    @Operation(summary = "当前生效的运营配置（sys_config 覆盖值 + yml 默认值合成结果）")
    public R<SettingsVO> get() {
        SettingsVO vo = new SettingsVO();
        vo.setModel(runtimeConfig.model());
        vo.setTemperature(runtimeConfig.temperature());
        vo.setFallbackModel(runtimeConfig.fallbackModel());
        vo.setDailyFreeCount(runtimeConfig.dailyFreeCount());
        vo.setDailyRewardMax(runtimeConfig.dailyRewardMax());
        vo.setTokenAlertThreshold(runtimeConfig.tokenAlertThreshold());
        return R.ok(vo);
    }

    @PutMapping
    @RequiresPermissions("agent:settings:edit")
    @Operation(summary = "保存运营配置（部分字段可省略，只改传了的）")
    public R<SettingsVO> save(@RequestBody Map<String, Object> body) {
        // 数值/文本统一转字符串校验后落库；非法值直接拒绝而不是静默吞掉
        Map<String, String> toUpsert = new LinkedHashMap<>();
        for (String key : ALLOWED.keySet()) {
            // 获取 key 中的最后一个.后面的字符串
            String last = key.substring(key.lastIndexOf('.') + 1);

            // 判断条件：如果 body 中包含解析出来的最后一个字符串
            if (body.containsKey(last)) {
                toUpsert.put(key, String.valueOf(body.get(last)).trim());
            }
        }
        if (toUpsert.isEmpty()) {
            throw new BaseException(ErrorCode.PARAM_INVALID, "没有可保存的配置项");
        }
        // 先整体校验再写：温度/次数/阈值必须是合法数字，模型名必须非空
        for (Map.Entry<String, String> e : toUpsert.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            if (Set.of(AgentRuntimeConfigService.KEY_TEMPERATURE,
                    AgentRuntimeConfigService.KEY_DAILY_FREE,
                    AgentRuntimeConfigService.KEY_DAILY_REWARD_MAX,
                    AgentRuntimeConfigService.KEY_TOKEN_ALERT).contains(key)) {
                try {
                    Double.parseDouble(value);
                } catch (NumberFormatException ex) {
                    throw new BaseException(ErrorCode.PARAM_INVALID, ALLOWED.get(key) + " 必须是数字");
                }
            }
            if ((key.equals(AgentRuntimeConfigService.KEY_MODEL)
                    || key.equals(AgentRuntimeConfigService.KEY_FALLBACK_MODEL)) && value.isEmpty()) {
                // 允许清空备用模型（表示跟 yml），但不允许清空主模型
                if (key.equals(AgentRuntimeConfigService.KEY_MODEL)) {
                    throw new BaseException(ErrorCode.PARAM_INVALID, "模型名不能为空");
                }
            }
        }
        toUpsert.forEach(this::upsert);
        return get();
    }

    /** 按 key upsert sys_config；走 Service 以复用它的唯一性校验与缓存清除 */
    private void upsert(String key, String value) {
        SysConfig existing = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, key).last("LIMIT 1"));
        if (existing == null) {
            SysConfig entity = new SysConfig();
            entity.setConfigKey(key);
            entity.setConfigValue(value);
            entity.setConfigName(ALLOWED.get(key));
            entity.setConfigType(1); // 1=系统内置类（模板语义：不可在前台随便删）
            entity.setRemark("语气助手运营设置（运营设置页维护）");
            sysConfigService.add(entity);
        } else {
            existing.setConfigValue(value);
            sysConfigService.update(existing);
        }
    }
}
