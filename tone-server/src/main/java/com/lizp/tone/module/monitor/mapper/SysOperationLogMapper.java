package com.lizp.tone.module.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lizp.tone.module.monitor.entity.SysOperationLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/** 操作日志 Mapper */
@Mapper
public interface SysOperationLogMapper extends BaseMapper<SysOperationLog> {

    /**
     * 查指定时间点之前的最大主键，作为批量删除的游标上界。
     *
     * <p>先锁定上界再按 id 分批删，比 {@code DELETE ... WHERE create_time < ? LIMIT n}
     * 更稳：后者每次都要扫索引找符合条件的行，游标方式则走主键，且不会漏删新写入的数据。
     */
    @Select("SELECT IFNULL(MAX(id), 0) FROM sys_operation_log WHERE create_time < #{time}")
    Long maxIdBefore(@Param("time") LocalDateTime time);

    /** 限量删除，避免一次大事务锁表、撑爆 undo 日志、造成主从延迟 */
    @Delete("DELETE FROM sys_operation_log WHERE id <= #{maxId} AND create_time < #{time} LIMIT #{limit}")
    int deleteBatch(@Param("time") LocalDateTime time,
                    @Param("maxId") Long maxId,
                    @Param("limit") int limit);
}
