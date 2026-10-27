package com.lizp.tone.module.agent.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 小程序端记录详情：记录主体 + 对话明细（首轮/微调），
 * 供"本地缓存没有这条记录"时从后端回源（换设备/清缓存/切 appid 的场景）。
 */
@Schema(description = "记录详情（小程序端）")
public record RecordDetailVO(
        @Schema(description = "记录主体") RecentRecordVO record,
        @Schema(description = "对话明细") List<MessageVO> messages) {

    @Schema(description = "对话消息")
    public record MessageVO(
            @Schema(description = "角色：user/assistant") String role,
            @Schema(description = "内容") String content,
            @Schema(description = "是否微调对话：0首轮 1微调") Integer refineFlag) {
    }
}
