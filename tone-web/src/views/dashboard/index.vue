<template>
  <div class="dashboard">
    <el-row :gutter="12">
      <el-col :span="6">
        <el-card shadow="never" class="dashboard__card">
          <div class="dashboard__label">当前账号</div>
          <div class="dashboard__value">{{ userStore.username }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="dashboard__card">
          <div class="dashboard__label">所属部门</div>
          <div class="dashboard__value">{{ deptName }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="dashboard__card">
          <div class="dashboard__label">角色数量</div>
          <div class="dashboard__value">{{ userStore.roles.length }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="dashboard__card">
          <div class="dashboard__label">权限点</div>
          <div class="dashboard__value">{{ userStore.perms.length }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="dashboard__welcome">
      <template #header>欢迎使用</template>
      <p>
        这是一个可复用的后台管理脚手架：后端 Spring Boot 3 + Spring Security + MyBatis-Plus，
        前端 Vue 3 + TypeScript + Element Plus。登录、RBAC 权限、操作日志、数据权限、定时任务等
        通用能力都已内置，业务模块直接在 <code>module/</code> 下新增即可。
      </p>
      <p>
        当前登录人的权限标识共 {{ userStore.perms.length }} 个，按钮级控制通过
        <code>v-hasPerm="'xxx:yyy:add'"</code> 指令实现；后端接口用
        <code>@RequiresPermissions</code> 做真正的拦截。
      </p>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const deptName = computed(() => userStore.userInfo?.deptName || '-')
</script>

<style scoped lang="scss">
.dashboard {
  &__card {
    text-align: center;
  }

  &__label {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }

  &__value {
    margin-top: 8px;
    font-size: 20px;
    font-weight: 600;
    color: var(--el-color-primary);
  }

  &__welcome {
    margin-top: 12px;

    p {
      line-height: 1.8;
      color: var(--el-text-color-regular);
    }

    code {
      padding: 1px 4px;
      border-radius: 3px;
      background: var(--el-fill-color-light);
      color: var(--el-color-primary);
    }
  }
}
</style>
