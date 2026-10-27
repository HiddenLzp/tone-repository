package com.lizp.tone.module.wxapi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lizp.tone.module.wxapi.entity.AppWxUser;
import org.apache.ibatis.annotations.Mapper;

/** 小程序用户 Mapper */
@Mapper
public interface AppWxUserMapper extends BaseMapper<AppWxUser> {
}
