package com.lizp.tone.module.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lizp.tone.module.monitor.entity.SysLoginLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/** 登录日志 Mapper */
@Mapper
public interface SysLoginLogMapper extends BaseMapper<SysLoginLog> {

    @Select("SELECT IFNULL(MAX(id), 0) FROM sys_login_log WHERE login_time < #{time}")
    Long maxIdBefore(@Param("time") LocalDateTime time);

    @Delete("DELETE FROM sys_login_log WHERE id <= #{maxId} AND login_time < #{time} LIMIT #{limit}")
    int deleteBatch(@Param("time") LocalDateTime time,
                    @Param("maxId") Long maxId,
                    @Param("limit") int limit);
}
