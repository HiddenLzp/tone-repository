package com.lizp.tone.dataperm;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限注解：标注在 Service 方法上，方法内执行的所有 SQL 会自动拼上行级过滤条件。
 *
 * <pre>{@code
 * // 只过滤主表 u，条件拼在 u.dept_id 上
 * @DataScope(alias = "u")
 * public PageResult<UserVO> page(UserQuery query) { ... }
 *
 * // 手写 XML 里已经带了条件，跳过自动过滤
 * @DataScope(ignore = true)
 * public List<XxxVO> customQuery() { ... }
 * }</pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /**
     * 需要被过滤的表别名。联表查询时必填，否则会出现
     * {@code Column 'dept_id' in where clause is ambiguous}。
     */
    String alias() default "";

    /** 部门字段名（不含别名前缀） */
    String deptField() default "dept_id";

    /** 创建人字段名（不含别名前缀），用于「仅本人」 */
    String userField() default "create_by";

    /** 是否跳过自动拼接（手写 XML 已自带条件时使用） */
    boolean ignore() default false;
}
