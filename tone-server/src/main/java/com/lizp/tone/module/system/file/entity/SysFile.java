package com.lizp.tone.module.system.file.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lizp.tone.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 文件实体 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_file")
@Schema(description = "文件")
public class SysFile extends BaseEntity {

    @Schema(description = "存储文件名")
    private String fileName;

    @Schema(description = "原始文件名")
    private String originalName;

    @Schema(description = "存储相对路径")
    private String filePath;

    @Schema(description = "访问URL")
    private String fileUrl;

    @Schema(description = "文件大小(字节)，即前端默认读到的那份（图片为压缩件）")
    private Long fileSize;

    /**
     * 原件（压缩前的原始图）存储相对路径。
     *
     * <p><b>为空不代表文件没有原图</b>：压完反而更大、或小到没必要压时，
     * 不会单独存一份原件，展示件就是它本身，此时该字段为空。
     */
    @Schema(description = "原件相对路径，为空表示未单独存储原图")
    private String originPath;

    @Schema(description = "原件大小(字节)，未单独存储时等于 fileSize")
    private Long originSize;

    @Schema(description = "是否生成了压缩展示件：0否 1是")
    private Integer compressed;

    @Schema(description = "扩展名")
    private String ext;

    @Schema(description = "MIME类型")
    private String mimeType;

    @Schema(description = "MD5，用于秒传与去重")
    private String md5;

    @Schema(description = "存储类型：1本地 2MinIO 3OSS")
    private Integer storageType;

    @Schema(description = "业务类型")
    private String bizType;

    @Schema(description = "上传人")
    private Long uploadBy;

    /**
     * 查看原图的签名链接（有时效，非数据库字段）。
     *
     * <p>每次查询现算：链接带到期时间，落库的话存进去那一刻就在倒计时，
     * 用户下次打开文件列表多半已经过期了。
     */
    @TableField(exist = false)
    @Schema(description = "原图签名链接，有时效")
    private String originUrl;
}
