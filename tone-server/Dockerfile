# ============================================================
# account-server 运行镜像 —— 「服务器本地构建」模式
#
# 镜像里**没有** Maven/Gradle、没有源码、没有 JDK 编译器：
# jar 由本地 `mvn clean package` 打好，和本文件一起传到服务器，
# 在服务器上 `docker build` 出镜像。好处是服务器不用装 JDK、
# 不用访问 Maven 仓库、不用把源码传到生产机上。
#
# 构建上下文里只要有这三样（同一个目录）：
#     Dockerfile
#     .dockerignore
#     account-server.jar      ← 名字对不上就用 --build-arg JAR_FILE=xxx.jar
#
#   cd /opt/account-build && docker build -t account-server:1.0.0 .
#
# ------------------------------------------------------------
# 参数怎么覆盖（三档，后者优先）
#   1) 默认值：写在下面的 ARG 上
#   2) 构建期：docker build --build-arg DB_HOST=10.0.0.5 ...
#      ARG 会落到同名的 ENV 里，**烙进镜像**，之后每次 run 都带着。
#      ⚠️ --build-arg 传的密码会出现在 `docker history` / `docker inspect` 的输出里，
#         所以密码这类东西请用第 3 种。
#   3) 运行期：docker run -e DB_PASSWORD=xxx ... （或 compose 的 environment）
#      只影响这一次启动，改配置不用重新构建镜像。**推荐方式**。
#
# 它们最终怎么进到应用里（见文件末尾「参数映射」一节）：
#   DB_* / REDIS_*           → application.yml 里的 ${...} 占位符，Spring 从环境变量取值
#   SERVER_PORT / profile    → 以启动参数 --server.port= / --spring.profiles.active= 传入
#   REDIS_USERNAME           → 以启动参数 --spring.data.redis.username= 传入
#                              （yml 里没有这一项，靠 Spring 的环境变量宽松绑定是绑不上的）
#   JAVA_OPTS                → 拼在 java 命令里
# ============================================================

FROM eclipse-temurin:17-jre-jammy

# ==================== 可配置参数 ====================
# 每个 ARG 都会在同名的 ENV 里落一次，这样 docker run -e 才能覆盖它。

# ---------------- 应用 ----------------
# 要拷进镜像的 jar 文件名，相对**构建上下文根目录**。
# 服务器上是「jar 与 Dockerfile 平铺」，所以默认就是裸文件名；
# 在仓库里构建时 jar 在 target/ 下，用 --build-arg JAR_FILE=target/account-server.jar
ARG JAR_FILE=tone-server.jar
# 容器内 jar 的固定路径：启动命令写死它，JAR_FILE 怎么变都不影响 CMD
ARG APP_HOME=/app
# 服务端口。会以 --server.port= 传给 Spring，也用于健康检查
ARG SERVER_PORT=10002
# Spring profile：prod / dev / test
ARG SPRING_PROFILES_ACTIVE=prod
# JVM 参数。MaxRAMPercentage 让堆跟着容器内存上限走（Spring Boot 3 已能读 cgroup 限制，
# 这里再压一道，别把整机内存吃满）；-Dfile.encoding 保证中文与 CSV 导入不乱码
ARG JAVA_OPTS="-XX:MaxRAMPercentage=70 -Dfile.encoding=UTF-8"

# ---------------- MySQL ----------------
# 应用侧对应 spring.datasource.url 的 ${DB_HOST} / ${DB_PORT} / ${DB_NAME}
# 以及 username/password（见 application-prod.yml）
ARG DB_HOST=DB_HOST
ARG DB_PORT=DB_PORT
ARG DB_NAME=DB_NAME
ARG DB_USERNAME=DB_USERNAME
ARG DB_PASSWORD=DB_PASSWORD
# 连接池：默认沿用 application-prod.yml 里的值，只在需要压并发时才动
ARG DB_POOL_MAX=50

# ---------------- Redis ----------------
# 应用侧对应 spring.data.redis.host / port / password / database
ARG REDIS_HOST=REDIS_HOST
ARG REDIS_PORT=REDIS_PORT
ARG REDIS_PASSWORD=REDIS_PASSWORD
# Redis 6+ 的 ACL 用户名；老版本 Redis（无 ACL）留空即可，留空不会向服务端发 AUTH user
ARG REDIS_USERNAME=
ARG REDIS_DB=1

# ---------------- 安全 / 初始化 ----------------
# JWT 签名密钥，生产必须换：openssl rand -base64 48
# 留空则回落到 application.yml 里的弱默认值，并会在启动日志里 WARN
ARG JWT_SECRET=toneAgentDefaultJwtSecretKeyPleaseChangeInProductionEnv1234567890
# full.sql 里 admin 的密码是占位符，真正的 BCrypt 由启动自检生成，用的就是这个值。
# 自检同时会把「首次登录标记」置 1，所以首次登录会被要求改密码
ARG ADMIN_INIT_PASSWORD=admin123
# 登录验证码开关：本地联调想省事就传 false
ARG CAPTCHA_ENABLED=true


# ---------------- 大模型配置 ----------------
ARG TONE_LLM_BASE_URL=TONE_LLM_BASE_URL
ARG TONE_LLM_API_KEY=TONE_LLM_API_KEY
ARG TONE_LLM_MODEL=TONE_LLM_MODEL
ARG TONE_LLM_PROVIDER=dashscope

# ---------------- 微信小程序配置 ----------------
ARG WX_APP_ID=WX_APP_ID
ARG WX_APP_SECRET=WX_APP_SECRET

# ==================== 落到 ENV ====================
# ARG 只在构建期可见；必须转成 ENV 才能被容器里的进程读到、才能被 docker run -e 覆盖。
# 一行一个，避免 JAVA_OPTS 里的空格被当成参数分隔符。
# 时区必须显式给：MySQL 的 datetime 与账单日期都按东八区算，
# 容器默认 UTC 会让「今天」在晚上 8 点之后变成明天。
ENV TZ=Asia/Shanghai
# APP_HOME 也要落成 ENV：ENTRYPOINT 是 exec 形式，Docker 不做变量展开，
# 里面的 ${APP_HOME} 是容器启动时由 shell 从环境变量里取的
ENV APP_HOME=${APP_HOME}
ENV SERVER_PORT=${SERVER_PORT}
ENV SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}
ENV JAVA_OPTS=${JAVA_OPTS}
ENV DB_HOST=${DB_HOST}
ENV DB_PORT=${DB_PORT}
ENV DB_NAME=${DB_NAME}
ENV DB_USERNAME=${DB_USERNAME}
ENV DB_PASSWORD=${DB_PASSWORD}
ENV DB_POOL_MAX=${DB_POOL_MAX}
ENV REDIS_HOST=${REDIS_HOST}
ENV REDIS_PORT=${REDIS_PORT}
ENV REDIS_PASSWORD=${REDIS_PASSWORD}
ENV REDIS_USERNAME=${REDIS_USERNAME}
ENV REDIS_DB=${REDIS_DB}
# 下面三个是 account.* 自定义配置，走 Spring 的宽松绑定：
# 环境变量名里的 _ 对应配置名里的 - 与 .，例如
# ACCOUNT_SECURITY_JWT_SECRET -> account.security.jwt-secret
ENV ACCOUNT_SECURITY_JWT_SECRET=${JWT_SECRET}
ENV ACCOUNT_USER_INIT_PASSWORD=${ADMIN_INIT_PASSWORD}
ENV ACCOUNT_SECURITY_CAPTCHA_ENABLED=${CAPTCHA_ENABLED}

ENV TONE_LLM_BASE_URL=${TONE_LLM_BASE_URL}
ENV TONE_LLM_API_KEY=${TONE_LLM_API_KEY}
ENV TONE_LLM_MODEL=${TONE_LLM_MODEL}
ENV TONE_LLM_PROVIDER=${TONE_LLM_PROVIDER}

ENV WX_APP_ID=${WX_APP_ID}
ENV WX_APP_SECRET=${WX_APP_SECRET}

WORKDIR ${APP_HOME}

# jar 就这一步进镜像。名字对不上会在这里报 "not found"，是预期的报错
COPY ${JAR_FILE} ${APP_HOME}/app.jar

# 上传区 / 账单原件区 / 日志目录。
# 路径与 application.yml 的 account.file.local-path: ./upload 一致（相对 WORKDIR），
# 生产请用 volume 挂出来持久化，否则重建容器后文件全丢
RUN mkdir -p ${APP_HOME}/upload ${APP_HOME}/upload-origin ${APP_HOME}/logs

# 只是声明，真正的端口由 SERVER_PORT 决定；这里是给人看的文档
EXPOSE 10001

# 纯 TCP 探测：jre 镜像里没有 curl / wget，也不值得为一个健康检查装它们。
# 用 bash 的 /dev/tcp，端口连不上就返回非 0
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=5 \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/$SERVER_PORT'

COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

# 用 sh -c 包一层：
#   1) $JAVA_OPTS / $SERVER_PORT 在容器启动时才展开，所以 docker run -e 能覆盖它们
#   2) ${REDIS_USERNAME:+...} 是 POSIX 参数展开——留空就不传这个参数，
#      免得给老版本 Redis 发一个空用户名的 AUTH
# 注意：JSON 数组（exec 形式）里 Docker 不做变量展开，所以下面这些 ${...}
# 全是**容器启动时**由 sh 展开的 —— 这正是 -e 能覆盖它们的原因。
# ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS --server.port=${SERVER_PORT} --spring.profiles.active=${SPRING_PROFILES_ACTIVE} --spring.datasource.hikari.maximum-pool-size=${DB_POOL_MAX} ${REDIS_USERNAME:+--spring.data.redis.username=$REDIS_USERNAME} -jar ${APP_HOME}/app.jar"]
ENTRYPOINT ["docker-entrypoint.sh"]

# ============================================================
# 参数映射速查
#
#  ARG / ENV                        → 应用实际配置项
#  ------------------------------------------------------------------
#  DB_HOST / DB_PORT / DB_NAME      → spring.datasource.url 里的 ${DB_HOST}:${DB_PORT}/${DB_NAME}
#  DB_USERNAME / DB_PASSWORD        → spring.datasource.username / password
#  DB_POOL_MAX                      → 启动参数 --spring.datasource.hikari.maximum-pool-size
#  REDIS_HOST / REDIS_PORT          → spring.data.redis.host / port
#  REDIS_PASSWORD / REDIS_DB        → spring.data.redis.password / database
#  REDIS_USERNAME                   → 启动参数 --spring.data.redis.username（留空则不传）
#  SERVER_PORT                      → 启动参数 --server.port
#  SPRING_PROFILES_ACTIVE           → 启动参数 --spring.profiles.active
#  JAVA_OPTS                        → java 命令行
#  JWT_SECRET                       → ACCOUNT_SECURITY_JWT_SECRET → account.security.jwt-secret
#  ADMIN_INIT_PASSWORD              → ACCOUNT_USER_INIT_PASSWORD → account.user.init-password
#  CAPTCHA_ENABLED                  → ACCOUNT_SECURITY_CAPTCHA_ENABLED → account.security.captcha-enabled
#
# 三种传入方式的优先级：命令行参数 > 环境变量 > application-*.yml
# 所以上面凡是走启动参数的项（-e 改了环境变量）依然生效——
# shell 是在容器启动时才展开 ${...} 的。
# ============================================================
