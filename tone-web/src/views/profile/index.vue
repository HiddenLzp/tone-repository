<template>
  <!--
    外层必须只有一个根：内容区是用 <transition> 包 router-view 的，
    多根组件会让 transition 找不到可动画的元素（控制台一条警告，动画也失效）。
  -->
  <div class="profile">
    <el-row :gutter="12">
      <el-col :span="8">
        <el-card shadow="never">
          <template #header>个人信息</template>
          <div class="profile__avatar">
            <!--
              头像上传走自定义 http-request：el-upload 自带的 action 是独立 xhr，
              拿不到拦截器里的 Authorization 头，请求会被 401 拦掉。
            -->
            <el-upload
              class="profile__upload"
              :http-request="uploadAvatarRequest"
              :before-upload="beforeAvatarUpload"
              :show-file-list="false"
              accept="image/*"
            >
              <div class="profile__avatar-wrap">
                <el-avatar :size="72" :src="userStore.avatar || undefined">
                  {{ userStore.nickname.charAt(0) }}
                </el-avatar>
                <div class="profile__avatar-mask">
                  <span v-if="!avatarUploading">更换</span>
                  <el-icon v-else class="is-loading"><Loading /></el-icon>
                </div>
              </div>
            </el-upload>
            <div class="profile__name">{{ userStore.nickname }}</div>
            <div class="profile__account">账号：{{ userStore.username }}</div>
          </div>
          <el-button type="primary" plain style="width: 100%" @click="router.push('/change-password')">
            修改密码
          </el-button>
        </el-card>
      </el-col>

      <el-col :span="16">
        <el-card shadow="never">
          <template #header>
            <div class="card-header">
              <span>基本资料</span>
              <el-button :icon="Edit" size="small" @click="openEdit">编辑资料</el-button>
            </div>
          </template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="用户ID">{{ userStore.userId }}</el-descriptions-item>
            <el-descriptions-item label="登录账号">{{ userStore.username }}</el-descriptions-item>
            <el-descriptions-item label="昵称">{{ userStore.nickname }}</el-descriptions-item>
            <el-descriptions-item label="真实姓名">
              {{ userStore.userInfo?.realName || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="性别">{{ sexText(userStore.userInfo?.sex) }}</el-descriptions-item>
            <el-descriptions-item label="邮箱">{{ userStore.userInfo?.email || '-' }}</el-descriptions-item>
            <el-descriptions-item label="手机号">{{ userStore.userInfo?.phone || '-' }}</el-descriptions-item>
            <el-descriptions-item label="所属部门">{{ userStore.userInfo?.deptName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="是否超管">
              <el-tag :type="userStore.isSuperAdmin ? 'danger' : 'info'" size="small">
                {{ userStore.isSuperAdmin ? '是' : '否' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="角色">
              <el-tag v-for="r in userStore.roles" :key="r" size="small" style="margin-right: 4px">
                {{ r }}
              </el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <el-card shadow="never" style="margin-top: 12px">
          <template #header>权限标识（{{ userStore.perms.length }}）</template>
          <el-tag v-for="p in userStore.perms" :key="p" size="small" type="info" style="margin: 0 4px 4px 0">
            {{ p }}
          </el-tag>
        </el-card>

        <el-card shadow="never" style="margin-top: 12px">
          <template #header>
            <div class="card-header">
              <span>登录设备（{{ sessions.length }}）</span>
              <el-button :icon="Refresh" circle size="small" @click="loadSessions" />
            </div>
          </template>
          <el-table v-loading="sessionLoading" :data="sessions" size="small" border>
            <el-table-column label="设备" min-width="190">
              <template #default="{ row }">
                <span>{{ row.browser || '未知浏览器' }} · {{ row.os || '未知系统' }}</span>
                <el-tag v-if="row.current" type="success" size="small" style="margin-left: 6px">
                  当前
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="ip" label="IP" min-width="120" />
            <el-table-column prop="location" label="归属地" min-width="110" show-overflow-tooltip />
            <el-table-column prop="loginTime" label="登录时间" min-width="160" />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button v-if="!row.current" link type="danger" @click="handleKick(row)">
                  下线
                </el-button>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
          </el-table>
          <div class="profile__tip">
            下线只会踢掉那一台设备，不影响你当前的登录状态。
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 编辑资料 -->
    <el-dialog v-model="editVisible" title="编辑资料" width="520px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="80px">
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="editForm.nickname" maxlength="30" show-word-limit />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="editForm.realName" maxlength="50" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="editForm.phone" placeholder="选填" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="editForm.email" placeholder="选填" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="editForm.sex">
            <el-radio :value="0">男</el-radio>
            <el-radio :value="1">女</el-radio>
            <el-radio :value="2">未知</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="base-drawer-footer">
          <el-button @click="editVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitEdit">确定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadRequestOptions } from 'element-plus'
import { Refresh, Edit, Loading } from '@element-plus/icons-vue'
import { mySessions, kickMySession } from '@/api/system'
import { updateProfile, uploadAvatar } from '@/api/auth'
import type { OnlineSession, ProfileUpdateRequest } from '@/types/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const sessions = ref<OnlineSession[]>([])
const sessionLoading = ref(false)
const avatarUploading = ref(false)
const submitting = ref(false)

// ==================== 编辑资料 ====================

const editVisible = ref(false)
const editFormRef = ref<FormInstance>()
const editForm = reactive<ProfileUpdateRequest>({
  nickname: '',
  realName: '',
  phone: '',
  email: '',
  sex: 2
})

const editRules: FormRules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 30, message: '昵称不能超过 30 位', trigger: 'blur' }
  ],
  realName: [{ max: 50, message: '真实姓名不能超过 50 位', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  phone: [{ pattern: /^$|^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }]
}

function openEdit(): void {
  Object.assign(editForm, {
    nickname: userStore.nickname,
    realName: userStore.userInfo?.realName || '',
    phone: userStore.userInfo?.phone || '',
    email: userStore.userInfo?.email || '',
    sex: userStore.userInfo?.sex ?? 2
  })
  editVisible.value = true
}

async function submitEdit(): Promise<void> {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    // 后端返回更新后的完整用户信息，顺手刷一下 store：
    // 省掉一次 /auth/info，导航栏上的昵称也立刻跟着变
    const info = await updateProfile({
      nickname: editForm.nickname.trim(),
      realName: editForm.realName?.trim() || undefined,
      email: editForm.email?.trim() || undefined,
      phone: editForm.phone?.trim() || undefined,
      sex: editForm.sex
    })
    userStore.setUserInfo(info)
    ElMessage.success('资料已更新')
    editVisible.value = false
  } finally {
    submitting.value = false
  }
}

// ==================== 头像 ====================

/** 前端先拦一道；后端 AuthServiceImpl.updateAvatar 还会再拦一次（请求体不可信） */
function beforeAvatarUpload(file: File): boolean {
  if (!file.type.startsWith('image/')) {
    ElMessage.error('头像只能是图片文件')
    return false
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('头像大小不能超过 2MB')
    return false
  }
  return true
}

async function uploadAvatarRequest(options: UploadRequestOptions): Promise<void> {
  avatarUploading.value = true
  try {
    const url = await uploadAvatar(options.file)
    userStore.setAvatar(url)
    ElMessage.success('头像已更新')
    options.onSuccess({})
  } catch (e) {
    // 不回调 onError：show-file-list=false 时没有文件条目要置失败态，
    // 回调了反而会在控制台留一条未处理的 UploadAjaxError
    ElMessage.error((e as Error)?.message || '上传失败')
  } finally {
    avatarUploading.value = false
  }
}

// ==================== 登录设备 ====================

async function loadSessions(): Promise<void> {
  sessionLoading.value = true
  try {
    sessions.value = await mySessions()
  } finally {
    sessionLoading.value = false
  }
}

async function handleKick(row: OnlineSession): Promise<void> {
  const res = await ElMessageBox.confirm(
    `确定下线这台设备吗？${row.browser || ''}（${row.ip || '未知 IP'}）`,
    '提示',
    { type: 'warning' }
  ).catch(() => null)
  if (!res) return
  await kickMySession(row.id)
  ElMessage.success('已下线该设备')
  loadSessions()
}

function sexText(sex?: number): string {
  if (sex === 0) return '男'
  if (sex === 1) return '女'
  return '未知'
}

onMounted(loadSessions)
</script>

<style scoped lang="scss">
.profile {
  &__avatar {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    margin-bottom: 16px;
  }

  &__upload {
    line-height: 0;
  }

  &__avatar-wrap {
    position: relative;
    display: inline-block;
    line-height: 0;
    border-radius: 50%;
    overflow: hidden;
    cursor: pointer;
  }

  // 悬停才显示"更换"提示：平时保持干净，鼠标移上去才知道这里能点
  &__avatar-mask {
    position: absolute;
    inset: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 13px;
    color: #fff;
    background: rgb(0 0 0 / 55%);
    opacity: 0;
    transition: opacity 0.2s;

    .profile__avatar-wrap:hover & {
      opacity: 1;
    }
  }

  &__name {
    font-size: 16px;
    font-weight: 600;
  }

  &__account {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }

  &__tip {
    margin-top: 8px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.muted {
  color: var(--el-text-color-placeholder);
}
</style>
