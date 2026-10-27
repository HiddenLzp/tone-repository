package com.lizp.tone.aspect;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 操作日志业务类型 */
@Getter
@AllArgsConstructor
public enum BusinessType {

    OTHER(0, "其它"),
    INSERT(1, "新增"),
    UPDATE(2, "修改"),
    DELETE(3, "删除"),
    EXPORT(4, "导出"),
    IMPORT(5, "导入"),
    GRANT(6, "授权"),
    FORCE_LOGOUT(7, "强退"),
    ;

    private final int value;
    private final String description;

    public static BusinessType of(int value) {
        for (BusinessType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return OTHER;
    }
}
