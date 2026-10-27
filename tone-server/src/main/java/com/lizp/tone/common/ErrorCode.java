package com.lizp.tone.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;

/**
 * 业务错误码。
 *
 * <p>分段规则：
 * <ul>
 *   <li>0        成功</li>
 *   <li>100xx    系统级（未知异常、参数校验、请求方式、文件、限流）</li>
 *   <li>200xx    认证授权（未登录、过期、无权限、锁定、验证码…）</li>
 *   <li>300xx    业务级（不存在、已存在、状态非法…）</li>
 *   <li>400xx    第三方服务</li>
 * </ul>
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(0, "操作成功"),

    // ============ 100xx 系统级 ============
    ERROR(10000, "操作失败"),
    UNKNOWN(10001, "系统异常，请联系管理员"),
    PARAM_INVALID(10002, "参数校验失败"),
    METHOD_NOT_ALLOWED(10003, "请求方式不支持"),
    NOT_FOUND(10004, "资源不存在"),
    FILE_UPLOAD_ERROR(10005, "文件上传失败"),
    FILE_TYPE_NOT_ALLOWED(10006, "文件类型不允许"),
    FILE_TOO_LARGE(10007, "文件超过大小限制"),
    RATE_LIMIT(10008, "操作过于频繁，请稍后再试"),
    TOO_MANY_REQUEST(10009, "请求过于频繁，已触发限流"),

    // ============ 200xx 认证授权 ============
    UNAUTHORIZED(20001, "未登录或登录状态已失效"),
    TOKEN_EXPIRED(20002, "accessToken 已过期"),
    TOKEN_INVALID(20003, "token 非法"),
    FORBIDDEN(20004, "没有访问权限"),
    KICKED_OUT(20005, "你已被强制下线，请重新登录"),
    CAPTCHA_ERROR(20006, "验证码错误或已过期"),
    PASSWORD_ERROR(20007, "账号或密码错误"),
    ACCOUNT_LOCKED(20008, "账号已锁定，请稍后再试"),
    REFRESH_TOKEN_INVALID(20009, "refreshToken 失效，请重新登录"),
    PASSWORD_MUST_CHANGE(20010, "请先修改初始密码"),
    ACCOUNT_DISABLED(20011, "账号已停用，请联系管理员"),
    OLD_PASSWORD_ERROR(20012, "原密码不正确"),
    NEW_PASSWORD_SAME(20013, "新密码不能与原密码相同"),
    PASSWORD_WEAK(20014, "密码强度不足，至少 6 位"),
    USER_NOT_FOUND(20015, "用户不存在"),

    // ============ 300xx 业务级 ============
    DATA_NOT_FOUND(30001, "数据不存在"),
    DATA_EXIST(30002, "数据已存在"),
    STATUS_INVALID(30003, "状态非法"),
    DEPT_HAS_CHILD(30004, "存在下级部门，不允许删除"),
    DEPT_HAS_USER(30005, "部门下存在用户，不允许删除"),
    ROLE_HAS_USER(30006, "角色下存在用户，不允许删除"),
    MENU_HAS_CHILD(30007, "存在子菜单，不允许删除"),
    CANNOT_DELETE_SELF(30008, "不允许删除当前登录用户"),
    CANNOT_DISABLE_SELF(30009, "不允许停用当前登录用户"),
    SUPER_ADMIN_PROTECTED(30010, "超级管理员不允许修改"),
    DICT_HAS_DATA(30011, "字典下存在数据，不允许删除"),
    JOB_TARGET_NOT_FOUND(30012, "定时任务调用目标不存在"),
    JOB_CRON_INVALID(30013, "cron 表达式不合法"),

    // ============ 400xx 第三方 ============
    THIRD_PARTY_ERROR(40001, "第三方服务调用失败"),

    // ============ 31xxx 语气 Agent 业务 ============
    TEXT_EMPTY(31001, "请输入要检查的文字"),
    TEXT_TOO_LONG(31002, "文字超出长度限制"),
    SCENE_INVALID(31003, "场景不合法"),
    SENSITIVE_BLOCKED(31004, "内容包含敏感词，无法处理"),
    QUOTA_EXHAUSTED(31005, "今日免费次数已用完，观看视频可再获得 1 次机会"),
    REWARD_LIMIT_REACHED(31006, "今日观看奖励已达上限"),
    AGENT_ROUNDS_EXCEEDED(31007, "处理轮次超限，请稍后重试"),
    LLM_RESPONSE_INVALID(31008, "模型返回异常，请重试"),
    RECORD_NOT_FOUND(31009, "检查记录不存在"),
    REFINE_EMPTY(31010, "请输入微调要求"),

    // ============ 32xxx 微信小程序 ============
    WX_LOGIN_FAILED(32001, "微信登录失败"),
    WX_TOKEN_INVALID(32002, "登录状态已失效，请重新登录"),
    WX_API_ERROR(32003, "微信接口调用失败"),
    ;

    private final int code;
    private final String message;
}
