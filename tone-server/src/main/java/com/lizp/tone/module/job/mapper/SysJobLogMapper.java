package com.lizp.tone.module.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lizp.tone.module.job.entity.SysJobLog;
import org.apache.ibatis.annotations.Mapper;

/** 定时任务日志 Mapper */
@Mapper
public interface SysJobLogMapper extends BaseMapper<SysJobLog> {
}
