package com.lizp.tone.security;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lizp.tone.common.Constants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 登录用户主体：放进 SecurityContext，Filter / AOP / 审计填充都从这里取当前用户。
 *
 * <p>为什么自己实现而不直接用 MyBatis 实体：
 * 实体里有 password、salt 这类敏感字段，而且字段会随业务变化；
 * 主体应该是「这次会话需要的最小信息集」，保持稳定。
 *
 * <p>序列化注意：本对象会整存进 Redis，三个坑都踩过：
 * <ol>
 *   <li>必须有无参构造，否则 Jackson 反序列化直接报 "no Creators"；</li>
 *   <li>只按字段序列化（getter/isGetter 全部关掉）。因为 UserDetails 的
 *       {@code getAuthorities()}、{@code isEnabled()} 都是派生方法、没有对应字段，
 *      放开 getter 会在反序列化时被当成未知属性报错；而且 {@code isSuperAdmin()}
 *       与 Lombok 生成的 {@code getSuperAdmin()} 会被认成同名属性直接冲突；</li>
 *   <li>用 {@code @JsonProperty} 显式标注字段，防止后续有人加了 {@code @JsonIgnore}
 *       把整个属性（含字段）一起忽略掉，导致从 Redis 读回来后字段变 null。</li>
 * </ol>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE,
        creatorVisibility = JsonAutoDetect.Visibility.NONE)
public class LoginUser implements UserDetails {

    /** 用户ID */
    private Long userId;
    /** 登录账号 */
    private String username;
    /** 密码（BCrypt 密文），仅用于框架内部校验 */
    private String password;
    /** 昵称 */
    private String nickname;
    /** 部门ID，数据权限要用 */
    private Long deptId;
    /** 状态：0正常 1停用 */
    private Integer status;
    /** 是否超级管理员 */
    @JsonProperty("superAdmin")
    private Boolean superAdmin;
    /** 角色编码集合 */
    private List<String> roles;
    /** 权限标识集合 */
    private Set<String> perms;
    /** 可见菜单ID集合，用于生成动态路由 */
    private Set<Long> menuIds;
    /** 数据范围：1全部 2自定义 3本部门 4本部门及以下 5仅本人 */
    private Integer dataScope;
    /** 自定义数据范围时的部门ID集合 */
    private Set<Long> customDeptIds;
    /** 令牌版本号，用于全端强制失效 */
    @JsonProperty("tokenVersion")
    private Integer tokenVersion;
    /** 本次会话 refreshToken 的 jti */
    private String jti;
    /** 是否需要强制修改初始密码 */
    private Boolean mustChangePassword;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (perms == null || perms.isEmpty()) {
            return Collections.emptyList();
        }
        return perms.stream().map(SimpleGrantedAuthority::new).toList();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    /** 账号是否未过期：这里不做密码有效期，永远返回 true */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /** 凭据是否未过期：token 过期由 Filter 单独判断，这里返回 true */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        // 用 equals 而不是 ==：两边都是 Integer，== 比的是引用，超出 -128~127 就会误判
        return Constants.STATUS_NORMAL.equals(status);
    }

    /** 是否拥有某个权限标识 */
    public boolean hasPerm(String perm) {
        return perms != null && perms.contains(perm);
    }

    /** 是否超级管理员（方法名保持 is 前缀，序列化已整体关掉 getter，不会与 Lombok 的 getter 冲突） */
    public boolean isSuperAdmin() {
        return Boolean.TRUE.equals(superAdmin);
    }
}
