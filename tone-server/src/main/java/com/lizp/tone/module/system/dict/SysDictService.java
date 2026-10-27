package com.lizp.tone.module.system.dict;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lizp.tone.common.BaseException;
import com.lizp.tone.common.Constants;
import com.lizp.tone.common.ErrorCode;
import com.lizp.tone.common.PageResult;
import com.lizp.tone.module.system.dict.entity.SysDictData;
import com.lizp.tone.module.system.dict.entity.SysDictType;
import com.lizp.tone.module.system.dict.mapper.SysDictDataMapper;
import com.lizp.tone.module.system.dict.mapper.SysDictTypeMapper;
import com.lizp.tone.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 字典服务。
 *
 * <p>字典数据几乎每个列表页都要用，属于高频读低频写，因此缓存进 Redis；
 * 增删改时主动清掉对应类型的缓存，保证下一次读到的是最新值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysDictService {

    private static final long CACHE_TTL_HOURS = 2;

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;
    private final RedisUtils redisUtils;

    // ==================== 字典类型 ====================

    public PageResult<SysDictType> pageType(long pageNum, long pageSize, String dictName,
                                            String dictType, Integer status) {
        IPage<SysDictType> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<SysDictType>()
                .like(StrUtil.isNotBlank(dictName), SysDictType::getDictName, dictName)
                .like(StrUtil.isNotBlank(dictType), SysDictType::getDictType, dictType)
                .eq(status != null, SysDictType::getStatus, status)
                .orderByAsc(SysDictType::getId);
        return PageResult.of(dictTypeMapper.selectPage(page, wrapper));
    }

    public List<SysDictType> listType() {
        return dictTypeMapper.selectList(new LambdaQueryWrapper<SysDictType>()
                .orderByAsc(SysDictType::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long addType(SysDictType entity) {
        checkTypeUnique(entity.getDictType(), null);
        dictTypeMapper.insert(entity);
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateType(SysDictType entity) {
        checkTypeUnique(entity.getDictType(), entity.getId());
        dictTypeMapper.updateById(entity);
        evictCache(entity.getDictType());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteType(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            SysDictType type = dictTypeMapper.selectById(id);
            if (type == null) {
                continue;
            }
            Long dataCount = dictDataMapper.selectCount(new LambdaQueryWrapper<SysDictData>()
                    .eq(SysDictData::getDictType, type.getDictType()));
            if (dataCount > 0) {
                throw new BaseException(ErrorCode.DICT_HAS_DATA, "字典【" + type.getDictName() + "】下仍有数据");
            }
            dictTypeMapper.deleteById(id);
            evictCache(type.getDictType());
        }
    }

    // ==================== 字典数据 ====================

    public PageResult<SysDictData> pageData(long pageNum, long pageSize, String dictType,
                                            String dictLabel, Integer status) {
        IPage<SysDictData> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<SysDictData>()
                .eq(StrUtil.isNotBlank(dictType), SysDictData::getDictType, dictType)
                .like(StrUtil.isNotBlank(dictLabel), SysDictData::getDictLabel, dictLabel)
                .eq(status != null, SysDictData::getStatus, status)
                .orderByAsc(SysDictData::getDictSort)
                .orderByAsc(SysDictData::getId);
        return PageResult.of(dictDataMapper.selectPage(page, wrapper));
    }

    /** 按类型取字典数据，带缓存；前端下拉框直接调这个 */
    @SuppressWarnings("unchecked")
    public List<SysDictData> listDataByType(String dictType) {
        if (StrUtil.isBlank(dictType)) {
            return List.of();
        }
        String key = String.format(Constants.KEY_DICT, dictType);
        Object cached = redisUtils.get(key);
        if (cached instanceof List<?> list) {
            return (List<SysDictData>) list;
        }
        List<SysDictData> data = dictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType)
                .eq(SysDictData::getStatus, Constants.STATUS_NORMAL)
                .orderByAsc(SysDictData::getDictSort));
        redisUtils.set(key, data, CACHE_TTL_HOURS, TimeUnit.HOURS);
        return data;
    }

    /** label → value 的映射，列表页翻译字典值时用 */
    public Map<String, String> dataMap(String dictType) {
        return listDataByType(dictType).stream()
                .collect(Collectors.toMap(SysDictData::getDictValue, SysDictData::getDictLabel, (a, b) -> a));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long addData(SysDictData entity) {
        dictDataMapper.insert(entity);
        evictCache(entity.getDictType());
        return entity.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateData(SysDictData entity) {
        dictDataMapper.updateById(entity);
        evictCache(entity.getDictType());
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteData(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<SysDictData> list = dictDataMapper.selectBatchIds(ids);
        dictDataMapper.deleteBatchIds(ids);
        list.stream().map(SysDictData::getDictType).distinct().forEach(this::evictCache);
    }

    // ==================== 私有 ====================

    private void checkTypeUnique(String dictType, Long excludeId) {
        if (StrUtil.isBlank(dictType)) {
            throw new BaseException(ErrorCode.PARAM_INVALID, "字典类型不能为空");
        }
        Long count = dictTypeMapper.selectCount(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, dictType)
                .ne(excludeId != null, SysDictType::getId, excludeId));
        if (count > 0) {
            throw new BaseException(ErrorCode.DATA_EXIST, "字典类型已存在：" + dictType);
        }
    }

    private void evictCache(String dictType) {
        if (StrUtil.isBlank(dictType)) {
            return;
        }
        redisUtils.delete(String.format(Constants.KEY_DICT, dictType));
    }
}
