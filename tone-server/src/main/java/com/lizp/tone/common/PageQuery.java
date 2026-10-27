package com.lizp.tone.common;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** 分页查询公共参数。所有分页接口的查询对象继承它即可。 */
@Data
public class PageQuery {

    @Schema(description = "页码，从 1 开始", example = "1")
    @Min(value = 1, message = "页码不能小于 1")
    private long pageNum = 1;

    /** 上限 500：防止前端传个 1000000 把库拉垮 */
    @Schema(description = "每页条数", example = "10")
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 500, message = "每页条数不能超过 500")
    private long pageSize = 10;
}
