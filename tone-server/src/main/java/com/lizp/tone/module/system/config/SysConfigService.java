package com.lizp.tone.module.system.config;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lizp.tone.common.BaseException;
import com.lizp.tone.common.ErrorCode;
import com.lizp.tone.common.PageResult;
import com.lizp.tone.module.system.config.entity.SysConfig;
import com.lizp.tone.module.system.config.mapper.SysConfigMapper;
import com.lizp.tone.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** 系统参数配置服务 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysConfigService {

    private static final String KEY_CONFIG = "sys:config:%s";
    private static final long CACHE_TTL_MINUTES = 30;

    private final SysConfigMapper configMapper;
    private final RedisUtils redisUtils;

    public PageResult<SysConfig> page(long pageNum, long pageSize, String configName,
                                      String configKey, Integer configType) {
        IPage<SysConfig> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<SysConfig>()
                .like(StrUtil.isNotBlank(configName), SysConfig::getConfigName, configName)
                .like(StrUtil.isNotBlank(configKey), SysConfig::getConfigKey, configKey)
                .eq(configType != null, SysConfig::getConfigType, configType)
                .orderByAsc(SysConfig::getId);
        return PageResult.of(configMapper.selectPage(page, wrapper));
    }

    /** 取参数值：读多写少，缓存 30 分钟；没配则返回默认值 */
    public String getValue(String configKey, String defaultValue) {
        if (StrUtil.isBlank(configKey)) {
            return defaultValue;
        }
        String cacheKey = String.format(KEY_CONFIG, configKey);
        Object cached = redisUtils.get(cacheKey);
        if (cached != null) {
            return String.valueOf(cached);
        }
        SysConfig config = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, configKey)
                .last("LIMIT 1"));
        String value = config == null ? defaultValue : config.getConfigValue();
        if (config != null) {
            redisUtils.set(cacheKey, value, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        }
        return value;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(SysConfig entity) {
        checkKeyUnique(entity.getConfigKey(), null);
        configMapper.insert(entity);
        evict(entity.getConfigKey());
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(SysConfig entity) {
        checkKeyUnique(entity.getConfigKey(), entity.getId());
        configMapper.updateById(entity);
        evict(entity.getConfigKey());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<SysConfig> list = configMapper.selectBatchIds(ids);
        for (SysConfig config : list) {
            // 内置参数是框架运行依赖的，删了会导致功能异常
            if (Integer.valueOf(1).equals(config.getConfigType())) {
                throw new BaseException(ErrorCode.STATUS_INVALID, "内置参数不允许删除：" + config.getConfigName());
            }
        }
        configMapper.deleteBatchIds(ids);
        list.forEach(c -> evict(c.getConfigKey()));
    }

    private void checkKeyUnique(String configKey, Long excludeId) {
        if (StrUtil.isBlank(configKey)) {
            throw new BaseException(ErrorCode.PARAM_INVALID, "参数键名不能为空");
        }
        Long count = configMapper.selectCount(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, configKey)
                .ne(excludeId != null, SysConfig::getId, excludeId));
        if (count > 0) {
            throw new BaseException(ErrorCode.DATA_EXIST, "参数键名已存在：" + configKey);
        }
    }

    private void evict(String configKey) {
        if (StrUtil.isNotBlank(configKey)) {
            redisUtils.delete(String.format(KEY_CONFIG, configKey));
        }
    }
}
