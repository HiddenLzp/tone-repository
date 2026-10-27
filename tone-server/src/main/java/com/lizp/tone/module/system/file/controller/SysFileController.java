package com.lizp.tone.module.system.file.controller;

import com.lizp.tone.aspect.BusinessType;
import com.lizp.tone.aspect.Log;
import com.lizp.tone.common.PageResult;
import com.lizp.tone.common.R;
import com.lizp.tone.context.UserContextHolder;
import com.lizp.tone.module.system.file.SysFileService;
import com.lizp.tone.module.system.file.entity.SysFile;
import com.lizp.tone.security.authz.RequiresPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 文件管理 */
@Tag(name = "文件管理")
@RestController
@RequestMapping("/system/file")
@RequiredArgsConstructor
public class SysFileController {

    private final SysFileService fileService;

    @Operation(summary = "上传文件", description = "相同内容的文件会秒传，直接返回已有记录")
    @RequiresPermissions("system:file:upload")
    @Log(title = "文件管理", businessType = BusinessType.INSERT)
    @PostMapping("/upload")
    public R<SysFile> upload(@RequestParam("file") MultipartFile file,
                             @RequestParam(required = false) String bizType) {
        return R.ok(fileService.upload(file, bizType, UserContextHolder.getUserId()));
    }

    @Operation(summary = "文件分页")
    @RequiresPermissions("system:file:list")
    @GetMapping("/page")
    public R<PageResult<SysFile>> page(@RequestParam(defaultValue = "1") long pageNum,
                                       @RequestParam(defaultValue = "10") long pageSize,
                                       @RequestParam(required = false) String originalName,
                                       @RequestParam(required = false) String bizType) {
        return R.ok(fileService.page(pageNum, pageSize, originalName, bizType));
    }

    @Operation(summary = "文件详情")
    @RequiresPermissions("system:file:query")
    @GetMapping("/{id}")
    public R<SysFile> detail(@PathVariable Long id) {
        return R.ok(fileService.getById(id));
    }

    /**
     * 查看原图（签名链接）。
     *
     * <p>这里刻意<b>不加权限注解</b>，且路径已在 {@code base.security.white-list} 放行：
     * 图片是被 {@code <img>} 标签加载的，浏览器不会带 Authorization 头，
     * 鉴权完全依赖 query 上的签名 + 到期时间。
     *
     * <p>签名由文件列表 / 详情接口在返回时现签，拿到链接的人改不了 id（改了验签不过），
     * 也没法把 {@code exp} 往后挪（同样验签不过）。
     */
    @Operation(summary = "查看原图", description = "凭签名临时访问原始文件，链接到期即失效")
    @GetMapping("/origin/{id}")
    public ResponseEntity<byte[]> origin(@PathVariable Long id,
                                         @RequestParam("exp") long exp,
                                         @RequestParam("sign") String sign) {
        SysFileService.OriginResource resource = fileService.readOrigin(id, exp, sign);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                resource.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : resource.getContentType()));
        // inline：图片在浏览器里直接显示，不要变成下载
        headers.setContentDispositionFormData("inline", urlEncode(resource.getFilename()));
        // 签名链接不能进公共缓存：CDN 缓存下来之后，过期时间就管不住了
        headers.setCacheControl(CacheControl.noStore().mustRevalidate());
        headers.setPragma("no-cache");
        return new ResponseEntity<>(resource.getData(), headers, HttpStatus.OK);
    }

    /** 文件名含空格/中文时，直接放进 header 会破坏报文结构，必须编码 */
    private String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @Operation(summary = "删除文件", description = "软删除，数据库记录标记删除")
    @RequiresPermissions("system:file:remove")
    @Log(title = "文件管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public R<Void> delete(@PathVariable List<Long> ids) {
        fileService.delete(ids);
        return R.ok();
    }
}
