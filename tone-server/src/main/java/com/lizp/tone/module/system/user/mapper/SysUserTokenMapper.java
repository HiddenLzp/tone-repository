package com.lizp.tone.module.system.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lizp.tone.module.system.user.entity.SysUserToken;
import org.apache.ibatis.annotations.Mapper;

/** 用户令牌 Mapper */
@Mapper
public interface SysUserTokenMapper extends BaseMapper<SysUserToken> {
}
