package com.lizp.tone.module.stats.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 看板统计查询：直接对业务表做聚合，数据量（个人小程序）下够用。
 */
@Mapper
public interface StatsMapper {

    /** 概览行：start/end 组成统计窗口（今日 = [0点, 明日0点)，昨天同理） */
    class OverviewRow {
        private Long activeUsers;
        private Long requests;
        private Long errorRequests;
        private Long promptTokens;
        private Long completionTokens;
        private Double avgRounds;

        public Long getActiveUsers() {
            return activeUsers;
        }

        public Long getRequests() {
            return requests;
        }

        public Long getErrorRequests() {
            return errorRequests;
        }

        public Long getPromptTokens() {
            return promptTokens;
        }

        public Long getCompletionTokens() {
            return completionTokens;
        }

        public Double getAvgRounds() {
            return avgRounds;
        }
    }

    /** 单日趋势行 */
    class TrendRow {
        private String date;
        private Long requests;
        private Long promptTokens;
        private Long completionTokens;

        public String getDate() {
            return date;
        }

        public Long getRequests() {
            return requests;
        }

        public Long getPromptTokens() {
            return promptTokens;
        }

        public Long getCompletionTokens() {
            return completionTokens;
        }
    }

    /** 场景分布行 */
    class SceneRow {
        private String scene;
        private Long requests;

        public String getScene() {
            return scene;
        }

        public Long getRequests() {
            return requests;
        }
    }

    /** 用户 Token 排行行 */
    class TopUserRow {
        private String openId;
        private String nickname;
        private Long requests;
        private Long totalTokens;

        public String getOpenId() {
            return openId;
        }

        public String getNickname() {
            return nickname;
        }

        public Long getRequests() {
            return requests;
        }

        public Long getTotalTokens() {
            return totalTokens;
        }
    }

    /** 配额事件行（type+detail 聚合，漏斗由控制器折算净值） */
    class QuotaEventRow {
        private String type;
        private String detail;
        private Long cnt;
        private Long users;

        public String getType() {
            return type;
        }

        public String getDetail() {
            return detail;
        }

        public Long getCnt() {
            return cnt;
        }

        public Long getUsers() {
            return users;
        }
    }

    @Select("""
            SELECT COUNT(DISTINCT open_id) AS active_users,
                   SUM(status = 'done')    AS requests,
                   SUM(status = 'error')   AS error_requests,
                   COALESCE(SUM(CASE WHEN status = 'done' THEN prompt_tokens END), 0)     AS prompt_tokens,
                   COALESCE(SUM(CASE WHEN status = 'done' THEN completion_tokens END), 0) AS completion_tokens,
                   AVG(CASE WHEN status = 'done' THEN rounds END)                          AS avg_rounds
            FROM app_check_record
            WHERE del_flag = 0 AND status IN ('done', 'error') AND create_time >= #{start} AND create_time < #{end}
            """)
    OverviewRow overview(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("""
            SELECT DATE(create_time)             AS date,
                   SUM(status = 'done')          AS requests,
                   COALESCE(SUM(CASE WHEN status = 'done' THEN prompt_tokens END), 0)     AS prompt_tokens,
                   COALESCE(SUM(CASE WHEN status = 'done' THEN completion_tokens END), 0) AS completion_tokens
            FROM app_check_record
            WHERE del_flag = 0 AND status IN ('done', 'error') AND create_time >= #{start} AND create_time < #{end}
            GROUP BY DATE(create_time)
            ORDER BY date
            """)
    List<TrendRow> trend(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Select("""
            SELECT scene, COUNT(*) AS requests
            FROM app_check_record
            WHERE del_flag = 0 AND status = 'done' AND create_time >= #{start}
            GROUP BY scene
            ORDER BY requests DESC
            """)
    List<SceneRow> sceneStats(@Param("start") LocalDateTime start);

    @Select("""
            SELECT r.open_id,
                   COALESCE(NULLIF(u.nickname, ''), r.open_id)             AS nickname,
                   COUNT(*)                                                AS requests,
                   COALESCE(SUM(r.prompt_tokens + r.completion_tokens), 0) AS total_tokens
            FROM app_check_record r
            LEFT JOIN app_wx_user u ON u.open_id = r.open_id AND u.del_flag = 0
            WHERE r.del_flag = 0 AND r.status = 'done'
            GROUP BY r.open_id, u.nickname
            ORDER BY total_tokens DESC
            LIMIT #{limit}
            """)
    List<TopUserRow> topUsers(@Param("limit") int limit);

    @Select("""
            SELECT COUNT(*) FROM app_ad_reward
            WHERE del_flag = 0 AND reward_date = #{date}
            """)
    long adViews(@Param("date") LocalDate date);

    @Select("""
            SELECT event_type            AS type,
                   COALESCE(detail, '')  AS detail,
                   COUNT(*)              AS cnt,
                   COUNT(DISTINCT open_id) AS users
            FROM app_quota_event
            WHERE del_flag = 0 AND event_date >= #{start}
            GROUP BY event_type, detail
            """)
    List<QuotaEventRow> quotaEvents(@Param("start") LocalDate start);

    @Select("""
            SELECT event_type AS type,
                   COUNT(DISTINCT open_id) AS users
            FROM app_quota_event
            WHERE del_flag = 0 AND event_date >= #{start}
            GROUP BY event_type
            """)
    List<QuotaEventRow> quotaEventUsers(@Param("start") LocalDate start);
}
