package com.lizp.tone.module.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lizp.tone.module.job.entity.SysJob;
import org.apache.ibatis.annotations.Mapper;

/** 定时任务 Mapper */
@Mapper
public interface SysJobMapper extends BaseMapper<SysJob> {
}
