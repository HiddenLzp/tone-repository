#!/bin/sh
set -e

# 调试时可临时打开
# echo "==> Starting account-server"
# echo "    SERVER_PORT=${SERVER_PORT}"
# echo "    SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE}"

eval "exec java ${JAVA_OPTS} \
  -jar ${APP_HOME}/app.jar \
  --server.port=${SERVER_PORT} \
  --spring.profiles.active=${SPRING_PROFILES_ACTIVE} \
  --spring.datasource.hikari.maximum-pool-size=${DB_POOL_MAX} \
  --tone.llm.base-url=${TONE_LLM_BASE_URL} \
  --tone.llm.api-key=${TONE_LLM_API_KEY} \
  --tone.llm.model=${TONE_LLM_MODEL} \
  --tone.llm.provider=${TONE_LLM_PROVIDER} \
  --tone.wx.appid=${WX_APP_ID} \
  --tone.wx.secret=${WX_APP_SECRET} \
  ${REDIS_USERNAME:+\"--spring.data.redis.username=${REDIS_USERNAME}\"}"