package com.lizp.tone.utils;

import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;

import java.io.InputStream;

/**
 * IP 归属地查询（离线库 ip2region）。
 *
 * <p>为什么用离线库而不是在线 API：
 * <ul>
 *   <li>不受外网可用性影响，内网部署也能用；</li>
 *   <li>单次查询微秒级，不像 HTTP 调用那样会拖慢登录请求；</li>
 *   <li>不存在调用量限制与隐私问题。</li>
 * </ul>
 *
 * <p>离线库文件 {@code ip2region.xdb} 放在 resources 下；文件缺失时降级返回"未知"，
 * 不会阻断应用启动。
 */
@Slf4j
public final class AddressUtils {

    private static final String DB_PATH = "ip2region.xdb";
    private static final String UNKNOWN = "未知";
    private static final String INTERNAL = "内网IP";

    private static volatile Searcher searcher;
    private static volatile boolean initialized = false;

    private AddressUtils() {
    }

    /** 查询归属地，失败或查不到时返回"未知" */
    public static String getAddress(String ip) {
        if (StrUtil.isBlank(ip)) {
            return UNKNOWN;
        }
        if (IpUtils.isInternalIp(ip)) {
            return INTERNAL;
        }
        Searcher s = getSearcher();
        if (s == null) {
            return UNKNOWN;
        }
        try {
            // 返回格式：国家|区域|省份|城市|运营商
            String region = s.search(ip);
            return format(region);
        } catch (Exception e) {
            log.warn("查询 IP 归属地失败 ip={}", ip, e);
            return UNKNOWN;
        }
    }

    /** 只取「省 + 市」，比完整串更适合在列表里展示 */
    private static String format(String region) {
        if (StrUtil.isBlank(region)) {
            return UNKNOWN;
        }
        String[] parts = region.split("\\|");
        StringBuilder sb = new StringBuilder();
        // parts: [国家, 区域, 省份, 城市, 运营商]
        String province = parts.length > 2 && !"0".equals(parts[2]) ? parts[2] : "";
        String city = parts.length > 3 && !"0".equals(parts[3]) ? parts[3] : "";
        if (StrUtil.isNotBlank(province)) {
            sb.append(province);
        }
        if (StrUtil.isNotBlank(city) && !city.equals(province)) {
            sb.append(city);
        }
        String result = sb.toString();
        return StrUtil.isBlank(result) ? (parts.length > 0 ? parts[0] : UNKNOWN) : result;
    }

    private static Searcher getSearcher() {
        if (initialized) {
            return searcher;
        }
        synchronized (AddressUtils.class) {
            if (initialized) {
                return searcher;
            }
            try {
                ClassPathResource resource = new ClassPathResource(DB_PATH);
                if (!resource.getUrl().getPath().isEmpty()) {
                    try (InputStream in = resource.getStream()) {
                        byte[] content = in.readAllBytes();
                        searcher = Searcher.newWithBuffer(content);
                        log.info("ip2region 离线库加载成功，共 {} 字节", content.length);
                    }
                }
            } catch (Exception e) {
                // 文件缺失或格式异常都不阻断启动，只是归属地查不出来
                log.warn("ip2region 离线库未加载，IP 归属地功能降级为「{}」（可将 ip2region.xdb 放到 resources 下启用）", UNKNOWN);
                searcher = null;
            }
            initialized = true;
            return searcher;
        }
    }
}
