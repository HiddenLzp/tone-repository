package com.lizp.tone.dataperm;

import cn.hutool.core.collection.CollUtil;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据权限 SQL 拼接器：把「当前用户能看到哪些部门的数据」翻译成 WHERE 条件。
 *
 * <p>实现 {@link MultiDataPermissionHandler} 而不是单表版本的
 * {@link com.baomidou.mybatisplus.extension.plugins.handler.DataPermissionHandler}，
 * 是因为联表时能拿到 {@link Table} 信息，可以按别名精确只过滤主表，
 * 避免给每个表都拼一遍条件导致字段歧义。
 */
@Slf4j
public class DataScopeHandler implements MultiDataPermissionHandler {

    /** 数据范围常量 */
    public static final int SCOPE_ALL = 1;
    public static final int SCOPE_CUSTOM = 2;
    public static final int SCOPE_DEPT = 3;
    public static final int SCOPE_DEPT_AND_CHILD = 4;
    public static final int SCOPE_SELF = 5;

    /**
     * 关键点：<b>不需要过滤时必须返回 null，不能返回原 where</b>。
     * MP 会把返回值当成"要追加的条件"，用 AND 拼到原 where 上；
     * 返回原 where 就等于把条件重复拼了一遍，SQL 会变成
     * {@code WHERE a = 1 AND a = 1}，同时因为参数个数对不上直接报
     * "No value specified for parameter"。
     */
    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        DataScopeContext.ScopeInfo info = DataScopeContext.get();
        // 没有上下文说明该方法没加 @DataScope，不做任何过滤
        if (info == null) {
            return null;
        }
        // 只处理指定的表（或别名），其余表原样放过
        String tableName = table.getAlias() != null ? table.getAlias().getName() : table.getName();
        if (info.tableAlias() != null && !info.tableAlias().isEmpty()
                && !tableName.equalsIgnoreCase(info.tableAlias())) {
            return null;
        }

        Expression condition = buildCondition(info);
        if (condition == null) {
            return null;
        }
        return where == null ? condition : new AndExpression(where, condition);
    }

    /** 单表版本的兜底：没有表信息时不过滤，避免误伤 */
    @Override
    public Expression getSqlSegment(Expression where, String mappedStatementId) {
        return null;
    }

    private Expression buildCondition(DataScopeContext.ScopeInfo info) {
        Integer scope = info.scopeType();
        if (scope == null || scope == SCOPE_ALL) {
            return null;
        }
        return switch (scope) {
            case SCOPE_CUSTOM, SCOPE_DEPT_AND_CHILD -> buildInCondition(info);
            case SCOPE_DEPT -> buildEqualsCondition(info.deptColumn(), info.deptId());
            case SCOPE_SELF -> buildEqualsCondition(info.userColumn(), info.userId());
            default -> null;
        };
    }

    /** dept_id IN (id1, id2, ...) */
    private Expression buildInCondition(DataScopeContext.ScopeInfo info) {
        Set<Long> deptIds = info.deptIds();
        if (CollUtil.isEmpty(deptIds)) {
            // 一个部门都没分配：用 1=2 保证查不到任何数据，而不是放行全部
            return parse("1 = 2");
        }
        String ids = deptIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        return parse(info.deptColumn() + " IN (" + ids + ")");
    }

    /** 字段 = 值 */
    private Expression buildEqualsCondition(String column, Long value) {
        if (column == null || value == null) {
            return null;
        }
        // 用 AST 构造而不是拼字符串，避免手滑拼出错，也更直观
        EqualsTo equalsTo = new EqualsTo();
        equalsTo.setLeftExpression(new Column(column));
        equalsTo.setRightExpression(new StringValue(String.valueOf(value)));
        return equalsTo;
    }

    /**
     * 复杂条件直接交给解析器：手写 IN(...) 比一层层搭 AST 更省事，
     * 解析失败时返回 null（不过滤）并记日志，绝不让 SQL 报错把请求打挂。
     */
    private Expression parse(String sqlSegment) {
        try {
            return CCJSqlParserUtil.parseCondExpression(sqlSegment);
        } catch (Exception e) {
            log.error("数据权限条件解析失败 segment={}", sqlSegment, e);
            return null;
        }
    }
}
