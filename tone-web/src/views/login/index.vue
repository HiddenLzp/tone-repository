<template>
  <div class="login">
    <div class="login__panel">
      <h2 class="login__title">{{ title }}</h2>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入账号" :prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
            clearable
          />
        </el-form-item>

        <el-form-item v-if="captchaEnabled" prop="captchaCode">
          <div class="login__captcha">
            <el-input v-model="form.captchaCode" placeholder="验证码" :prefix-icon="Key" clearable />
            <img
              v-if="captcha.imageBase64"
              :src="captcha.imageBase64"
              class="login__captcha-img"
              alt="验证码"
              @click="loadCaptcha"
            />
          </div>
        </el-form-item>

        <el-button type="primary" class="login__submit" :loading="loading" @click="handleLogin">
          登 录
        </el-button>
      </el-form>

    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { User, Lock, Key } from '@element-plus/icons-vue'
import { getCaptcha } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import type { CaptchaVO } from '@/types/api'
import {resetAuthExpired} from "@/utils/request.ts";

const title = import.meta.env.VITE_APP_TITLE || '言值管理台'
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const captchaEnabled = ref(false)
const captcha = reactive<{ imageBase64: string; captchaKey: string }>({
  imageBase64: '',
  captchaKey: ''
})

const form = reactive({
  username: '',
  password: '',
  captchaCode: ''
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captchaCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

async function loadCaptcha(): Promise<void> {
  const data: CaptchaVO = await getCaptcha()
  captchaEnabled.value = data.enabled
  captcha.imageBase64 = data.imageBase64
  captcha.captchaKey = data.captchaKey
  form.captchaCode = ''
}

async function handleLogin(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login({
      username: form.username,
      password: form.password,
      captchaKey: captcha.captchaKey,
      captchaCode: form.captchaCode
    })
    // 只需要拉用户信息：动态路由由 router 守卫统一生成，
    // 登录页自己不 addRoute，避免和守卫重复注册（路径相同会被注册两遍）
    await userStore.loadUserInfo()

    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    // 用 replace 而不是 push：登录成功后不该能"返回"到登录页
    await router.replace(redirect)
  } catch (e) {
    const message = e instanceof Error ? e.message : '登录失败'
    ElMessage.error(message)
    if (captchaEnabled.value) loadCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  
  resetAuthExpired()
  loadCaptcha().catch(() => {
    // 验证码接口不可用时降级为无验证码登录，不阻断主流程
    captchaEnabled.value = false
  })
})
</script>

<style scoped lang="scss">
.login {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: linear-gradient(135deg, var(--el-color-primary-light-5), var(--el-color-primary));

  &__panel {
    width: 380px;
    padding: 32px 28px 24px;
    border-radius: 8px;
    background: var(--el-bg-color);
    box-shadow: 0 8px 24px rgb(0 0 0 / 12%);
  }

  &__title {
    margin: 0 0 24px;
    text-align: center;
    font-size: 20px;
    color: var(--el-text-color-primary);
  }

  &__captcha {
    display: flex;
    gap: 8px;
    width: 100%;
  }

  &__captcha-img {
    height: 40px;
    border-radius: 4px;
    cursor: pointer;
    border: 1px solid var(--el-border-color);
  }

  &__submit {
    width: 100%;
  }

  &__tip {
    margin: 16px 0 0;
    text-align: center;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}
</style>
