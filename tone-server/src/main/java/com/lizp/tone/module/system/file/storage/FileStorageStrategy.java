package com.lizp.tone.module.system.file.storage;

/**
 * 文件存储策略。
 *
 * <p>把「文件写到哪里」从业务里抽出来：上传流程只关心产出两份物件，
 * 至于落到磁盘、MinIO 还是阿里云 OSS，由当前生效的实现决定。
 *
 * <p><b>为什么要有 {@link StorageArea} 这个参数</b>：
 * 同一份文件的展示件和原件必须能被区分对待——前者要能公开访问，
 * 后者绝不能被静态映射出去。交给实现方自行决定「area 怎么落到物理位置」，
 * 本地实现是两个不同根目录，对象存储实现是两个不同 key 前缀。
 *
 * <p><b>为什么这里没有 buildUrl</b>：拼 URL 的规则对三种实现完全一样（{@code urlPrefix + "/" + key}），
 * 差异只在 {@code urlPrefix} 的取值（本地是相对路径 /upload，对象存储是 bucket 域名），
 * 已经收敛到 {@code base.file.url-prefix} 一个配置项上，没必要让每个实现再写一遍。
 */
public interface FileStorageStrategy {

    /**
     * 类型标识，与 {@code base.file.storage-type} 对应，小写。
     *
     * @return local / minio / oss
     */
    String type();

    /**
     * 写入文件。
     *
     * @param area        存储区域
     * @param relativeKey 相对路径，形如 {@code 2026/10/uuid.jpg}；原件区由实现自行加前缀
     * @param data        文件字节
     * @param contentType MIME 类型，可为 null
     */
    void save(StorageArea area, String relativeKey, byte[] data, String contentType);

    /**
     * 读取文件。
     *
     * <p>返回 {@code byte[]} 而非流：调用方需要统一转成响应体，
     * 且文件上限由 {@code base.file.max-size} 兜着（默认 50MB），整块读是可控的。
     *
     * @throws java.util.NoSuchElementException 文件不存在时由各实现抛出，调用方统一转成业务异常
     */
    byte[] read(StorageArea area, String relativeKey);

    /** 删除文件，不存在时应静默成功（幂等，便于重试与补偿） */
    void delete(StorageArea area, String relativeKey);

    /** 该 key 在指定区域是否存在 */
    default boolean exists(StorageArea area, String relativeKey) {
        try {
            read(area, relativeKey);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
