package com.lizp.tone.module.system.file;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lizp.tone.common.BaseException;
import com.lizp.tone.common.Constants;
import com.lizp.tone.common.ErrorCode;
import com.lizp.tone.common.PageResult;
import com.lizp.tone.config.BaseProperties;
import com.lizp.tone.module.system.file.compress.ImageCompressor;
import com.lizp.tone.module.system.file.entity.SysFile;
import com.lizp.tone.module.system.file.mapper.SysFileMapper;
import com.lizp.tone.module.system.file.storage.FileStorageFactory;
import com.lizp.tone.module.system.file.storage.FileStorageStrategy;
import com.lizp.tone.module.system.file.storage.StorageArea;
import com.lizp.tone.utils.FileSignUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.unit.DataSize;
import org.springframework.util.unit.DataUnit;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * 文件服务。
 *
 * <p><b>一条上传请求可能产出两个物件</b>：
 * <ol>
 *   <li><b>展示件</b>（{@link StorageArea#PUBLIC}）：图片先压一道，地址可直接暴露在页面上；</li>
 *   <li><b>原件</b>（{@link StorageArea#ORIGIN}）：只有存在真正的压缩收益时才单独存一份，
 *       放在不对外映射的区域 / 对象存储前缀下，经服务端签名才能取。</li>
 * </ol>
 * 二者用<b>各自独立的 UUID</b> 命名，互不推导——知道压缩图的名字推不出原图，
 * 更不会因为「去掉 _thumb 后缀就能拿到原图」被爬虫顺走。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysFileService {

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy/MM");

    /** 原图签名链接的路径模板 */
    private static final String ORIGIN_URL_TEMPLATE = "/system/file/origin/%d?exp=%d&sign=%s";

    /** max-size 解析失败时的兜底值（50MB） */
    private static final long DEFAULT_MAX_SIZE = 50L * 1024 * 1024;

    private final SysFileMapper fileMapper;
    private final BaseProperties properties;
    private final FileStorageFactory storageFactory;
    private final ImageCompressor imageCompressor;

    /**
     * 上传文件。
     *
     * <p>流程：校验扩展名与大小 → MD5 秒传 → 图片压缩 → 分别落盘 → 入库。
     */
    @Transactional(rollbackFor = Exception.class)
    public SysFile upload(MultipartFile file, String bizType, Long uploadBy) {
        if (file == null || file.isEmpty()) {
            throw new BaseException(ErrorCode.FILE_UPLOAD_ERROR, "文件为空");
        }
        String originalName = file.getOriginalFilename();
        String ext = StrUtil.isBlank(originalName) ? "" : FileUtil.extName(originalName).toLowerCase();
        checkExtAllowed(ext);
        checkSizeAllowed(file.getSize());

        /*
         * 整块读进内存：后续要连续用四次（算 MD5、压缩、写两份），流式处理得来回切换数据源，
         * 复杂度不划算。上限由 base.file.max-size 兜着，默认 50MB；
         * 如果要支持更大的文件，这里要改成先落临时文件再逐步处理。
         */
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new BaseException(ErrorCode.FILE_UPLOAD_ERROR, "读取文件失败");
        }

        String md5 = DigestUtil.md5Hex(data);
        // 秒传：同一份内容直接用已有记录，省带宽也省磁盘
        SysFile existing = fileMapper.selectOne(new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getMd5, md5)
                .eq(SysFile::getDelFlag, Constants.DEL_FLAG_NORMAL)
                .last("LIMIT 1"));
        if (existing != null) {
            fillOriginUrl(existing);
            return existing;
        }

        byte[] compressed = imageCompressor.compress(data, ext);
        boolean hasSeparateOrigin = compressed != null;

        String dateDir = YEAR_MONTH.format(LocalDate.now());
        // 展示件与原件各用一个 UUID：文件名之间不存在任何推导关系
        String displayExt = hasSeparateOrigin ? properties.getFile().getCompress().getFormat() : ext;
        String displayKey = dateDir + "/" + IdUtil.fastSimpleUUID() + (StrUtil.isBlank(displayExt) ? "" : "." + displayExt);
        String originKey = hasSeparateOrigin ? dateDir + "/" + IdUtil.fastSimpleUUID() + (StrUtil.isBlank(ext) ? "" : "." + ext) : null;

        FileStorageStrategy storage = storageFactory.resolve();
        byte[] displayBytes = hasSeparateOrigin ? compressed : data;
        storage.save(StorageArea.PUBLIC, displayKey, displayBytes, contentTypeOf(displayExt, file.getContentType()));
        if (hasSeparateOrigin) {
            storage.save(StorageArea.ORIGIN, originKey, data, file.getContentType());
        }

        SysFile entity = new SysFile();
        entity.setFileName(displayKey.substring(displayKey.lastIndexOf('/') + 1));
        entity.setOriginalName(originalName);
        entity.setFilePath(displayKey);
        entity.setFileUrl(properties.getFile().getUrlPrefix() + "/" + displayKey);
        entity.setFileSize((long) displayBytes.length);
        /*
         * ext 记的是「用户传上来的那个文件的类型」，不是展示件的类型。
         * 传 PNG 生成的是 JPG 压缩件，若把 ext 记成 jpg，列表上的类型列、按类型筛
         * 就会和用户看到的文件名对不上；展示件到底是什么格式，看 fileUrl 的后缀即可。
         */
        entity.setExt(ext);
        entity.setMimeType(file.getContentType());
        entity.setMd5(md5);
        entity.setStorageType(storageType());
        entity.setBizType(bizType);
        entity.setUploadBy(uploadBy);
        entity.setDelFlag(Constants.DEL_FLAG_NORMAL);
        entity.setOriginPath(hasSeparateOrigin ? originKey : null);
        entity.setOriginSize((long) data.length);
        entity.setCompressed(hasSeparateOrigin ? 1 : 0);
        fileMapper.insert(entity);
        fillOriginUrl(entity);
        return entity;
    }

    public PageResult<SysFile> page(long pageNum, long pageSize, String originalName, String bizType) {
        IPage<SysFile> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SysFile> wrapper = new LambdaQueryWrapper<SysFile>()
                .like(StrUtil.isNotBlank(originalName), SysFile::getOriginalName, originalName)
                .eq(StrUtil.isNotBlank(bizType), SysFile::getBizType, bizType)
                .eq(SysFile::getDelFlag, Constants.DEL_FLAG_NORMAL)
                .orderByDesc(SysFile::getCreateTime);
        PageResult<SysFile> result = PageResult.of(fileMapper.selectPage(page, wrapper));
        // 列表上每条都要给一次签名链接，否则前端点「查看原图」还得再发一个请求
        result.getRecords().forEach(this::fillOriginUrl);
        return result;
    }

    public SysFile getById(Long id) {
        SysFile entity = fileMapper.selectById(id);
        if (entity == null) {
            return null;
        }
        fillOriginUrl(entity);
        return entity;
    }

    /**
     * 读取原件字节，供「查看原图」用。
     *
     * @param id     文件 id
     * @param expire 签名到期时间戳（秒）
     * @param sign   签名
     * @throws BaseException 签名不匹配、已过期、或文件不存在
     */
    public OriginResource readOrigin(Long id, long expire, String sign) {
        String secret = properties.getSecurity().getJwtSecret();
        if (!FileSignUtils.verify(secret, id, expire, sign)) {
            throw new BaseException(ErrorCode.FORBIDDEN, "链接已失效，请回到页面重新打开原图");
        }
        SysFile entity = fileMapper.selectById(id);
        if (entity == null || Constants.DEL_FLAG_DELETED.equals(entity.getDelFlag())) {
            throw new BaseException(ErrorCode.DATA_NOT_FOUND, "文件不存在或已被删除");
        }
        FileStorageStrategy storage = storageFactory.resolve();
        /*
         * 没有单独存原件时（压了反而更大、或压不了），展示件就是原图本身。
         * 这时必须允许读取，否则会出现「明明有图但查看原图 404」。
         */
        boolean hasSeparate = StrUtil.isNotBlank(entity.getOriginPath());
        byte[] bytes = hasSeparate
                ? storage.read(StorageArea.ORIGIN, entity.getOriginPath())
                : storage.read(StorageArea.PUBLIC, entity.getFilePath());
        if (bytes == null) {
            throw new NoSuchElementException("文件内容为空：" + id);
        }
        String downloadName = StrUtil.isNotBlank(entity.getOriginalName()) ? entity.getOriginalName() : String.valueOf(id);
        return new OriginResource(bytes, entity.getMimeType(), downloadName);
    }

    /** 逻辑删除：物理删除风险太高，先软删，需要彻底清理时再跑运维脚本 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        fileMapper.deleteBatchIds(ids);
    }

    /** 给单个文件补上原图签名链接 */
    private void fillOriginUrl(SysFile entity) {
        entity.setOriginUrl(buildOriginUrl(entity.getId()));
    }

    /** 签发一条有时间限制的原图链接 */
    private String buildOriginUrl(Long id) {
        long expire = System.currentTimeMillis() / 1000 + Math.max(properties.getFile().getOriginUrlExpire(), 60);
        String sign = FileSignUtils.sign(properties.getSecurity().getJwtSecret(), id, expire);
        return String.format(ORIGIN_URL_TEMPLATE, id, expire, sign);
    }

    private Integer storageType() {
        return switch (properties.getFile().getStorageType()) {
            case "minio" -> 2;
            case "oss" -> 3;
            default -> 1;
        };
    }

    /** 压缩后统一输出 jpg，原样存储时沿用上传时的 contentType */
    private String contentTypeOf(String ext, String original) {
        if ("jpg".equalsIgnoreCase(ext) || "jpeg".equalsIgnoreCase(ext)) {
            return "image/jpeg";
        }
        return original;
    }

    private void checkExtAllowed(String ext) {
        String allow = properties.getFile().getAllowExtensions();
        if (StrUtil.isBlank(allow)) {
            return;
        }
        List<String> allowed = List.of(allow.split(","));
        if (!allowed.contains(ext.toLowerCase())) {
            throw new BaseException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "不允许上传 ." + ext + " 类型的文件");
        }
    }

    private void checkSizeAllowed(long size) {
        // 兜底：multipart 配置已在框架层拦了一道，这里再按业务配置拦一次
        long maxBytes = parseMaxSize();
        if (size > maxBytes) {
            throw new BaseException(ErrorCode.FILE_TOO_LARGE, "文件大小不能超过 " + properties.getFile().getMaxSize());
        }
    }

    /**
     * 解析 {@code base.file.max-size}（如 50MB）。
     *
     * <p>原来这里是写死的 50MB，配置项形同虚设——改了 yml 不生效，
     * 属于那种「配了但没用」的坑，顺手接上。解析失败就退回默认，不让配置笔误拖垮启动。
     */
    private long parseMaxSize() {
        String maxSize = properties.getFile().getMaxSize();
        if (StrUtil.isBlank(maxSize)) {
            return DEFAULT_MAX_SIZE;
        }
        try {
            return DataSize.parse(maxSize, DataUnit.BYTES).toBytes();
        } catch (Exception e) {
            log.warn("base.file.max-size 解析失败，回退到 50MB：{}", maxSize);
            return DEFAULT_MAX_SIZE;
        }
    }

    /** 原图读取结果 */
    @Getter
    public static class OriginResource {
        private final byte[] data;
        private final String contentType;
        private final String filename;

        public OriginResource(byte[] data, String contentType, String filename) {
            this.data = data;
            this.contentType = contentType;
            this.filename = filename;
        }
    }
}
