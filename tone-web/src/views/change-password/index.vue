<template>
  <div class="change-password">
    <el-card class="change-password__card" shadow="never">
      <template #header>
        <span>{{ isForced ? '首次登录，请先修改密码' : '修改密码' }}</span>
      </template>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="90px"
        style="max-width: 420px"
      >
        <!--
          强制改密时用户刚用初始密码登录成功，再要求输一遍原密码没有意义。
          用 v-if 而非 v-show：v-show 只是 display:none，form-item 仍注册在 el-form
          的字段表里，required 规则照样会把空原密码拦下来。
        -->
        <el-form-item v-if="!isForced" label="原密码" prop="oldPassword">
          <el-input v-model="form.oldPassword" type="password" show-password autocomplete="off" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="form.newPassword" type="password" show-password autocomplete="off" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="off" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="submit">提交</el-button>
          <el-button v-if="!isForced" @click="router.back()">返回</el-button>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="isForced"
        type="info"
        :closable="false"
        description="出于安全考虑，初始密码必须由本人修改后才能使用系统，无需输入原密码。修改成功后需要重新登录。"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { changePassword } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { clearSession } from '@/utils/session'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const isForced = computed(() => userStore.mustChangePassword)

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 强制态下原密码字段被 v-if 卸载，规则也一并撤掉：
// 万一 v-if 因条件抖动未生效，也不至于留下一条永远校验不过的隐藏规则
const rules = computed<FormRules>(() => ({
  oldPassword: isForced.value ? [] : [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 20, message: '长度需在 6 到 20 个字符之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}))

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    // 强制态不传原密码：豁免由后端读库判定，前端传不传都不影响结果
    await changePassword(
      isForced.value
        ? { newPassword: form.newPassword }
        : { oldPassword: form.oldPassword, newPassword: form.newPassword }
    )
  } catch (e) {
    // 不补这个 catch，服务端返回业务错误时表现为"点了没反应"：转圈结束、无提示、不跳转
    ElMessage.error(e instanceof Error ? e.message : '修改密码失败，请稍后重试')
    return
  } finally {
    loading.value = false
  }

  ElMessage.success('密码修改成功，请重新登录')
  // 后端已把令牌版本 +1，这里必须重新登录拿新 token
  await userStore.logout()
  await clearSession()
  router.replace('/login')
}
</script>

<style scoped lang="scss">
.change-password {
  display: flex;
  justify-content: center;
  padding: 40px 16px;
  height: 100%;
  overflow: auto;

  &__card {
    width: 100%;
    max-width: 560px;
    height: fit-content;
  }
}
</style>
