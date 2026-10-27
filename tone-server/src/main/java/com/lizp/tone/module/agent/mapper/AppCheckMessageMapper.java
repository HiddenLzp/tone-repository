package com.lizp.tone.module.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lizp.tone.module.agent.entity.AppCheckMessage;
import org.apache.ibatis.annotations.Mapper;

/** 检查消息明细 Mapper */
@Mapper
public interface AppCheckMessageMapper extends BaseMapper<AppCheckMessage> {
}
