package com.lizp.tone.module.wxapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lizp.tone.module.wxapi.entity.AppQuotaEvent;
import org.apache.ibatis.annotations.Mapper;

/** 配额事件流水 Mapper */
@Mapper
public interface AppQuotaEventMapper extends BaseMapper<AppQuotaEvent> {
}
