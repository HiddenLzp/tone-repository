package com.lizp.tone.module.system.file.storage;

/**
 * 存储区域。
 *
 * <p>同一个文件可能被存成两份，每份归属不同的区域，权限与生命周期都不一样：
 * <ul>
 *   <li>{@link #PUBLIC} 展示件：压缩后的图片 / 普通文件，可被 Nginx、CDN 直接托管，
 *       地址可以直接暴露在页面上；</li>
 *   <li>{@link #ORIGIN} 原件：未处理的原始文件，不参与任何静态映射，
 *       只能经服务端签名后临时访问。</li>
 * </ul>
 *
 * <p>分成两个区域而不是靠文件名后缀区分，是防护的关键：
 * 后缀可猜（去掉 {@code _thumb} 就是原图），跨目录 / 跨 key 前缀猜不出来。
 */
public enum StorageArea {

    /** 公开区 */
    PUBLIC("public"),
    /** 原件区 */
    ORIGIN("origin");

    private final String value;

    StorageArea(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
