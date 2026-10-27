package com.lizp.tone.module.system.file.compress;

import com.lizp.tone.config.BaseProperties;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.Set;

/**
 * 图片压缩：产出一份适合网页直接展示的副本。
 *
 * <p>产出的副本要做到「体积明显小于原图」才有意义，所以压缩完会和原图比一比，
 * 压不下来（已经很小、或是本身就不适合压缩的图）就返回 null —— 由调用方决定怎么处理，
 * 而不是硬塞一份更大的"压缩图"给前端。
 *
 * <p>几个刻意的处理：
 * <ul>
 *   <li><b>只缩不放</b>：尺寸小于上限时保持原尺寸，只降质量，避免小图被拉伸变大；</li>
 *   <li><b>统一转 JPG</b>：PNG 的透明通道在网页上多半渲染成黑底/花屏，转成 JPG 前先铺白底
 *       （也能顺手把 PNG 的体积压掉一个数量级）；</li>
 *   <li><b>GIF / SVG 跳过</b>：GIF 可能有动画，JDK 的 ImageIO 只取第一帧，压了等于把动画弄坏了；</li>
 *   <li><b>读不出来就跳过</b>：扩展名是 jpg 不代表内容真是 jpg，解不了就不要阻塞上传。</li>
 * </ul>
 */
@Slf4j
@Component
public class ImageCompressor {

    /** 参与压缩的图片扩展名 */
    private static final Set<String> IMAGE_EXTS = Set.of("jpg", "jpeg", "png", "bmp", "webp");

    private final BaseProperties properties;

    public ImageCompressor(BaseProperties properties) {
        this.properties = properties;
    }

    /** 是否纳入压缩流程（压缩开关 + 扩展名白名单 + 跳过名单） */
    public boolean support(String ext) {
        if (ext == null || ext.isBlank()) {
            return false;
        }
        BaseProperties.FileProps.Compress cfg = properties.getFile().getCompress();
        if (!cfg.isEnabled()) {
            return false;
        }
        String lower = ext.toLowerCase(Locale.ROOT);
        if (!IMAGE_EXTS.contains(lower)) {
            return false;
        }
        return !skipped(lower);
    }

    /**
     * 生成展示副本。
     *
     * @param originBytes 原始图片字节
     * @param ext        原始扩展名
     * @return 压缩后的字节；压不了或压完没变小则返回 {@code null}
     */
    public byte[] compress(byte[] originBytes, String ext) {
        if (!support(ext)) {
            return null;
        }
        BaseProperties.FileProps.Compress cfg = properties.getFile().getCompress();
        // 太小的图压了也省不了几 KB，还要付一次编解码的 CPU
        if (originBytes.length <= cfg.getMinSizeKb() * 1024L) {
            return null;
        }
        try {
            byte[] compressed = doCompress(originBytes, cfg);
            if (compressed == null || compressed.length >= originBytes.length) {
                // 压完没变小：返回 null，前端直接拿原图当展示图，不做无效存储
                return null;
            }
            log.debug("图片压缩 {}KB -> {}KB", originBytes.length / 1024, compressed.length / 1024);
            return compressed;
        } catch (Exception e) {
            // 压缩失败不等于上传失败：降级为用原图展示，别把用户的上传流程打断
            log.warn("图片压缩失败，将直接使用原图 ext={} size={}", ext, originBytes.length, e);
            return null;
        }
    }

    private byte[] doCompress(byte[] originBytes, BaseProperties.FileProps.Compress cfg) throws Exception {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(originBytes));
        if (source == null) {
            // 扩展名与实际内容不符，或 JDK 不支持该格式（如 HEIC、部分 WebP）
            return null;
        }
        BufferedImage prepared = flattenAlpha(source);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var builder = Thumbnails.of(prepared);
            /*
             * 只缩不放：Thumbnailator 的 size() 是"最长边填满这个框"，
             * 对小于上限的图会放大（1600x1200 -> 1920x1440），体积反而变胖。
             * 所以只有真的超限时才缩放，不然保持原尺寸、仅降 quality。
             */
            if (prepared.getWidth() > cfg.getMaxWidth() || prepared.getHeight() > cfg.getMaxHeight()) {
                builder.size(cfg.getMaxWidth(), cfg.getMaxHeight());
            } else {
                builder.scale(1.0);
            }
            builder.outputFormat(cfg.getFormat())
                    .outputQuality(checkedQuality(cfg.getQuality()))
                    .toOutputStream(out);
            return out.toByteArray();
        }
    }

    /**
     * 透明像素铺白底。
     *
     * <p>PNG 常见的透明通道在 JPEG 里没有对应物：直接交给 ImageIO 写，
     * 透明区域会合成到<b>黑色</b>背景上，头像、Logo 这类图会变成一块黑方块。
     * 网页默认背景是白的，所以这里显式铺白。
     */
    private BufferedImage flattenAlpha(BufferedImage source) {
        if (!source.getColorModel().hasAlpha()) {
            return source;
        }
        BufferedImage flat = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = flat.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, flat.getWidth(), flat.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return flat;
    }

    private double checkedQuality(double quality) {
        if (quality <= 0) {
            return 0.82;
        }
        return Math.min(quality, 1.0);
    }

    private boolean skipped(String ext) {
        String skip = properties.getFile().getCompress().getSkipExtensions();
        if (skip == null || skip.isBlank()) {
            return false;
        }
        return Set.of(skip.split(",")).contains(ext);
    }
}
