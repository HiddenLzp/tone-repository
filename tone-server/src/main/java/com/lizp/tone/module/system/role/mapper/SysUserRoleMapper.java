package com.lizp.tone.module.system.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lizp.tone.module.system.role.entity.SysUserRole;
import org.apache.ibatis.annotations.Mapper;

/** 用户角色关联 Mapper */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}
