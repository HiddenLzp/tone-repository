package com.lizp.tone.common;

/** 全局常量。集中放置，避免魔法字符串散落各处。 */
public final class Constants {

    private Constants() {
    }

    /** 超级管理员角色编码：拥有全部权限，且不受数据权限约束 */
    public static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    /** 超级管理员用户ID */
    public static final Long SUPER_ADMIN_USER_ID = 1L;

    /** 顶级父级ID */
    public static final Long TOP_PARENT_ID = 0L;

    /**
     * 状态：正常。
     * 用包装类型而不是 int，是为了能直接写 {@code Constants.STATUS_NORMAL.equals(entity.getStatus())}；
     * 实体里的这些字段都是包装类型（可能为空），用 == 比较会踩到拆箱陷阱。
     */
    public static final Integer STATUS_NORMAL = 0;
    /** 状态：停用 */
    public static final Integer STATUS_DISABLED = 1;

    /** 逻辑删除：存在 */
    public static final Integer DEL_FLAG_NORMAL = 0;
    /** 逻辑删除：已删除 */
    public static final Integer DEL_FLAG_DELETED = 2;

    /** 菜单类型：目录 */
    public static final Integer MENU_TYPE_DIR = 1;
    /** 菜单类型：菜单 */
    public static final Integer MENU_TYPE_MENU = 2;
    /** 菜单类型：按钮/接口 */
    public static final Integer MENU_TYPE_BUTTON = 3;

    /** ==================== Redis Key 前缀 ==================== */
    public static final String KEY_CAPTCHA = "captcha:";
    public static final String KEY_TOKEN_VERSION = "auth:user:%d:version";
    public static final String KEY_LOGIN_USER = "auth:user:%d:login";
    public static final String KEY_REFRESH = "auth:refresh:%s";
    public static final String KEY_REFRESH_GRACE = "auth:refresh:grace:%s";
    /**
     * 会话吊销名单（key 后缀 = 会话ID，即该会话 refreshToken 的 jti）。
     * accessToken 是无状态的，登出 / 强制下线想让它「立刻」失效，要么改用户级令牌版本
     * （会波及该用户所有设备），要么记一条会话级名单（只影响这一台）。这里用后者。
     */
    public static final String KEY_SESSION_REVOKED = "auth:session:revoked:%s";
    public static final String KEY_REFRESH_LOCK = "lock:refresh:%d";
    public static final String KEY_LOGIN_FAIL = "auth:login:fail:%s";
    public static final String KEY_JOB_LOCK = "lock:job:%s";
    public static final String KEY_RATE_LIMIT = "rate:limit:%s";
    public static final String KEY_DICT = "sys:dict:%s";

    /** 默认字符集 */
    public static final String UTF8 = "UTF-8";
}
