<template>
  <div class="not-found">
    <h1>404</h1>
    <p>页面不存在，或你没有访问权限</p>
    <el-button type="primary" @click="router.replace('/')">返回首页</el-button>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<style scoped lang="scss">
.not-found {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;

  h1 {
    margin: 0;
    font-size: 72px;
    color: var(--el-color-primary);
  }

  p {
    margin: 8px 0 20px;
    color: var(--el-text-color-secondary);
  }
}
</style>
