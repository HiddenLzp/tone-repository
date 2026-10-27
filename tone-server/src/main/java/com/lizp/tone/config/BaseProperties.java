package com.lizp.tone.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 脚手架自定义配置，对应 application.yml 中的 {@code base.*}。
 *
 * <p>抽成配置类的好处：后续业务模块加开关时，只要在这里加字段 + yml 里配一行，
 * 不需要到处散落 @Value。
 */
@Data
@Component
@ConfigurationProperties(prefix = "tone")
public class BaseProperties {

    private Security security = new Security();

    private User user = new User();

    private FileProps file = new FileProps();

    private LogProps log = new LogProps();

    /** JWT 与安全相关 */
    @Data
    public static class Security {
        /** JWT 签名密钥，生产环境必须通过环境变量覆盖 */
        private String jwtSecret;
        /** accessToken 有效期（分钟） */
        private long accessExpire = 30;
        /** refreshToken 有效期（天） */
        private long refreshExpire = 7;
        /** refreshToken 轮换宽限期（秒），用于解决并发刷新的竞态 */
        private long refreshGrace = 30;
        /** 无需登录即可访问的路径 */
        private List<String> whiteList = new ArrayList<>();
        /** 登录是否需要图形验证码 */
        private boolean captchaEnabled = true;
        /** 连续登录失败多少次后锁定 */
        private int maxLoginFail = 5;
        /** 锁定时长（分钟） */
        private int lockMinutes = 15;
        /**
         * 没有标注 @RequiresPermissions 的接口是否拒绝访问。
         * 默认 false（放行）：脚手架阶段接口多，逐个加注解成本高；
         * 上线前建议改为 true，转为「默认拒绝」，避免新接口忘了加权限注解就直接裸奔。
         *
         * <p>注意：改成 true 之前，先给「登录后就该能用」的接口补上放行手段，
         * 典型是 {@code GET /system/menu/routes}（动态路由数据源，没有它进不了系统，
         * 再要求权限就是死锁）。它目前靠本开关为 false 才放行。
         */
        private boolean denyUnregisteredPerm = false;
    }

    /** 用户相关默认值 */
    @Data
    public static class User {
        /** 新建用户 / 重置密码时的默认密码 */
        private String defaultPassword = "123456";
    }

    /**
     * 文件存储。
     *
     * <p>上传后会产出两种物件：
     * <ul>
     *   <li><b>展示件</b>（public 区）：页面默认用它，图片经压缩后落到这里，可被 CDN / Nginx 直接托管；</li>
     *   <li><b>原件</b>（origin 区）：不做任何处理，只在用户点「查看原图」时经签名 URL 访问。
     *       该区域<b>不参与静态资源映射</b>， guessing 不到路径就是爬不到。</li>
     * </ul>
     */
    @Data
    public static class FileProps {
        /** 存储类型：local / minio / oss（小写，与 FileStorageStrategy#type 对应） */
        private String storageType = "local";
        /** 本地存储根路径（public 区） */
        private String localPath = "./upload";
        /** 允许的扩展名，为空表示不限制 */
        private String allowExtensions = "";
        /** 单个文件大小上限字符串，如 50MB */
        private String maxSize = "50MB";
        /** 文件访问的 URL 前缀。本地为 /upload；MinIO/OSS 为 bucket 的公网域名，如 http://127.0.0.1:9000/base */
        private String urlPrefix = "/upload";
        /**
         * 原图签名链接有效期（秒）。
         *
         * <p>原图走的是「带签名的短链」而非登录态：{@code <img>} 标签发不出 Authorization 头，
         * 把原图挂在登录态上会导致查看原图功能不可用，所以改成服务端签发、到期失效的签名。
         */
        private long originUrlExpire = 1800;

        private Origin origin = new Origin();
        private Compress compress = new Compress();
        private Minio minio = new Minio();
        private Oss oss = new Oss();

        /** 原件（原图）存放区：只在用户点「查看原图」时才用得到 */
        @Data
        public static class Origin {
            /** 本地模式下的原件根目录，必须与 public 区分开、互不嵌套（否则等于没隔离） */
            private String localPath = "./upload-origin";
            /** MinIO / OSS 模式下原件的 key 前缀 */
            private String keyPrefix = "origin";
        }

        /** 图片压缩 */
        @Data
        public static class Compress {
            /** 是否启用压缩。关掉后不生成展示副本，列表页的 fileUrl 会直接指到原件 */
            private boolean enabled = true;
            /** 小于该体积（KB）的图片不压缩，省一次编解码 */
            private long minSizeKb = 20;
            /** 展示图最大边长（像素），超出按比例缩放，不拉伸 */
            private int maxWidth = 1920;
            private int maxHeight = 1920;
            /** 输出质量 0~1，JPEG 才有意义 */
            private double quality = 0.82;
            /** 输出格式：jpg（WebP 需要额外编码器，JDK 默认不支持，这里不走捷径） */
            private String format = "jpg";
            /** 不参与压缩的扩展名：GIF 可能带动画，压缩只会留下第一帧 */
            private String skipExtensions = "gif,svg,ico";
        }

        /** MinIO */
        @Data
        public static class Minio {
            /** 服务地址，如 http://127.0.0.1:9000 */
            private String endpoint;
            private String accessKey;
            private String secretKey;
            private String bucket = "base";
        }

        /** 阿里云 OSS */
        @Data
        public static class Oss {
            /** Endpoint（地域节点），如 oss-cn-beijing.aliyuncs.com */
            private String endpoint;
            private String accessKeyId;
            private String accessKeySecret;
            private String bucketName = "base";
        }
    }

    /** 日志相关 */
    @Data
    public static class LogProps {
        /** 日志保留月数，超出后由定时任务清理 */
        private int retentionMonths = 6;
        /** 清理时每批删除的条数，避免长事务 */
        private int cleanBatchSize = 5000;
    }
}
