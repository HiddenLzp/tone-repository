package com.lizp.tone.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 统一分页响应体。
 *
 * <p>提供 {@link #of(IPage, Function)} 便于把 MP 的 IPage&lt;Entity&gt; 转成 IPage&lt;VO&gt;。
 */
@Data
@NoArgsConstructor
@Schema(description = "分页响应体")
public class PageResult<T> implements Serializable {

    @Schema(description = "数据列表")
    private List<T> records = Collections.emptyList();

    @Schema(description = "总条数")
    private long total = 0;

    @Schema(description = "当前页码，从 1 开始")
    private long pageNum = 1;

    @Schema(description = "每页条数")
    private long pageSize = 10;

    @Schema(description = "总页数")
    private long pages = 0;

    public PageResult(List<T> records, long total, long pageNum, long pageSize) {
        this.records = records == null ? Collections.emptyList() : records;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.pages = pageSize <= 0 ? 0 : (total + pageSize - 1) / pageSize;
    }

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 实体分页转 VO 分页，避免把实体字段暴露给前端 */
    public static <E, V> PageResult<V> of(IPage<E> page, Function<E, V> converter) {
        List<V> list = page.getRecords().stream().map(converter).toList();
        return new PageResult<>(list, page.getTotal(), page.getCurrent(), page.getSize());
    }

    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return new PageResult<>(Collections.emptyList(), 0, pageNum, pageSize);
    }
}
