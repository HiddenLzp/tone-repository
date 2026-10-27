<template>
  <view class="container">
    <!--
      我的资料：微信官方「头像昵称填写能力」（个人主体可用）。
      - button open-type=chooseAvatar：用户点选头像（官方弹选择，拿到临时文件）
      - input type=nickname：键盘自动带微信昵称
      失焦/选择后自动保存，无需额外按钮；资料让管理台能认出"这条记录是谁"
    -->
    <view class="card profile-card">
      <button class="avatar-btn" open-type="chooseAvatar" @chooseavatar="onChooseAvatar">
        <image class="avatar-img" :src="profile.avatar || '/static/logo.png'" mode="aspectFill" />
        <view class="avatar-edit">改</view>
      </button>
      <view class="profile-info">
        <input
          type="nickname"
          v-model="profile.nickname"
          class="nickname-input"
          placeholder="点击填写昵称"
          @blur="onNicknameBlur"
        />
        <view class="profile-sub">资料仅用于展示 · 也可不填</view>
      </view>
    </view>

    <!-- 场景选择 -->
    <view class="card">
      <view class="section-title">选择沟通场景</view>
      <view class="scene-grid">
        <view
          v-for="s in scenes"
          :key="s.value"
          class="scene-item"
          :class="{ active: scene === s.value }"
          @tap="scene = s.value"
        >
          <view class="scene-icon">{{ s.icon }}</view>
          <view class="scene-label">{{ s.label }}</view>
        </view>
      </view>
    </view>

    <!-- 文本输入 -->
    <view class="card">
      <view class="section-title">输入要检查的文字</view>
      <textarea
        v-model="text"
        class="input-area"
        :maxlength="2000"
        placeholder="例：你到底什么时候给我答复，再拖下去谁都别想好过"
      />
      <view class="counter">{{ text.length }}/2000</view>
      <view class="examples">
        <view class="examples-title">没有头绪？试试示例：</view>
        <view class="chips">
          <view v-for="(ex, i) in examples" :key="i" class="chip" @tap="fillExample(ex)">
            {{ ex.label }}
          </view>
        </view>
      </view>
    </view>

    <!--
      检查进度卡片：贴在输入框下方（不用弹框遮罩，用户能看到自己的输入不被打断）。
      - 步骤条来自后端 SSE 的 stage 事件（工具实际被调用时才点亮，是真实进度）
      - 计时器帮助用户理解"慢"是模型在干活
      - loading 期间可点"收起"折叠成一行；完成后整体自动隐藏（正常会跳结果页）
      - 出错时错误信息留在卡片里，直到下一次检查开始
    -->
    <view v-if="loading || progressError" class="card progress-card">
      <view class="progress-head">
        <view class="progress-spinner" v-if="loading" />
        <view class="progress-title">{{ loading ? checkTip : '本次检查未成功' }}</view>
        <view v-if="loading" class="progress-elapsed">已用时 {{ elapsed }} 秒</view>
        <view class="progress-toggle" @tap="progressCollapsed = !progressCollapsed">
          {{ progressCollapsed ? '展开' : '收起' }}
        </view>
      </view>
      <view v-if="!progressCollapsed" class="progress-steps">
        <view
          v-for="s in stageSteps"
          :key="s.name"
          class="step"
          :class="{ done: s.done, active: s.active }"
        >
          {{ s.label }}
        </view>
      </view>
      <view v-if="progressError" class="progress-error">{{ progressError }}</view>
    </view>

    <!-- 配额信息 -->
    <view class="card quota">
      <view class="quota-text">
        今日剩余次数：
        <text class="quota-num">{{ remain }}</text>
        <text class="quota-total"> / 免费 {{ quota.freeTotal }} + 奖励 {{ rewardRemain }}</text>
      </view>
      <!-- 开发辅助：仅后端 dev 登录模式（未配微信 appid）时显示，调 /api/quota/reset 清空今日配额 -->
      <view v-if="devMode" class="quota-reset" @tap="resetQuota">重置次数</view>
      <view v-if="adEnabled && remain === 0" class="btn-ad" @tap="watchAd">看视频 +1 次</view>
    </view>

    <!-- 次数用尽提示（无广告位时） -->
    <view v-if="!adEnabled && remain === 0" class="card quota-empty">
      今日次数已用完，明天再来吧～（配置激励视频广告位后可看视频获得额外次数）
    </view>

    <!--
      最近记录：本地历史最近 3 条。设计取舍：
      - 只在确实有记录时渲染，新用户首页保持干净
      - 行点击进详情；有最终文本的行给"复制"快捷键（回访最高频操作是抄走上次改好的文案，
        不必进详情页翻），@tap.stop 避免触发行点击
      - 右上"全部 ›"进历史页
    -->
    <view v-if="recentRecords.length" class="card recent">
      <view class="recent-head">
        <view class="recent-title">最近记录</view>
        <view class="recent-all" @tap="goHistory">全部 ›</view>
      </view>
      <view
        v-for="item in recentRecords"
        :key="item.recordId"
        class="recent-item"
        @tap="openResult(item.recordId)"
      >
        <view class="recent-row1">
          <view class="tag">{{ sceneLabel(item.scene) }}</view>
          <view class="recent-time">{{ formatTime(item.time) }}</view>
        </view>
        <view class="recent-row2">
          <view class="recent-text">{{ item.originalText }}</view>
          <!-- 重跑：回填原文+场景并立即检查（显式动作，消耗一次配额） -->
          <view class="recent-rerun" @tap.stop="rerunRecord(item)">重跑</view>
          <view class="recent-copy" @tap.stop="copyLatest(item)">复制</view>
        </view>
      </view>
    </view>

    <!-- 主按钮 -->
    <view class="action-bar">
      <button class="btn-primary btn-check" :disabled="loading || remain === 0" @tap="doCheck">
        {{ loading ? '检查中…' : '一键检查' }}
      </button>
      <view class="history-entry" @tap="goHistory">历史记录</view>
    </view>
  </view>
</template>

<script>
import { agentCheckStream, agentRecent, getProfile, saveNickname, uploadAvatar, quotaCheck, quotaReward, quotaReset } from '@/utils/api'
import { saveHistory, getHistory } from '@/utils/history'

const CHECK_TIPS = ['正在连接模型…', '正在分析原文…', '马上就好…']

/**
 * 检查阶段的固定展示顺序（与后端 AgentStreamController 的 stage 事件一一对应）。
 * 后端按模型实际调用工具的顺序发事件，这里只负责"按预定顺序渲染步骤条"：
 * reached 里出现过的标为已完成，最后出现的标为进行中。
 */
const STAGE_ORDER = [
  { name: 'start', label: '开始分析' },
  { name: 'sensitive', label: '敏感词校验' },
  { name: 'analyze', label: '语气诊断' },
  { name: 'rules', label: '场景规则' },
  { name: 'rewrite', label: '生成版本' },
  { name: 'save', label: '保存结果' }
]

export default {
  data() {
    return {
      text: '',
      scene: 'workplace',
      loading: false,
      streamTask: null,
      checkTip: CHECK_TIPS[0],
      tipTimer: null,
      /** 我的资料（头像昵称填写能力），onShow 时从后端刷新 */
      profile: { nickname: '', avatar: '' },
      /** 进度卡片状态 */
      progressCollapsed: false,
      progressError: '',
      stageReached: [],
      elapsed: 0,
      elapsedTimer: null,
      scenes: [
        { value: 'workplace', label: '职场', icon: '💼' },
        { value: 'social', label: '社交', icon: '🍵' },
        { value: 'relationship', label: '亲密关系', icon: '💌' },
        { value: 'moments', label: '朋友圈', icon: '📸' },
        { value: 'service', label: '客服', icon: '🎧' }
      ],
      examples: [
        { label: '催同事进度', scene: 'workplace', text: '你到底什么时候给我答复，再拖下去谁都别想好过' },
        { label: '拒绝邀约', scene: 'social', text: '不去，没空，别再问了' },
        { label: '朋友圈文案', scene: 'moments', text: '今天去爬山了，累死我了但是风景还行吧' }
      ],
      quota: { freeUsed: 0, freeTotal: 3, rewardClaimed: 0, rewardUsed: 0 },
      adEnabled: false,
      adUnitId: '',
      // 后端是否处于 dev 登录模式（未配微信 appid）：为 true 时配额卡片显示开发重置按钮
      devMode: false,
      /** 首页"最近记录"：本地历史最近 3 条，onShow 时刷新 */
      recentRecords: []
    }
  },
  computed: {
    remain() {
      return Math.max(0, this.quota.freeTotal - this.quota.freeUsed) +
        Math.max(0, this.quota.rewardClaimed - this.quota.rewardUsed)
    },
    rewardRemain() {
      return Math.max(0, this.quota.rewardClaimed - this.quota.rewardUsed)
    },
    /** 步骤条渲染数据：按固定顺序 + 到达状态 */
    stageSteps() {
      const lastReached = this.stageReached[this.stageReached.length - 1]
      return STAGE_ORDER.map((s) => ({
        ...s,
        done: this.stageReached.includes(s.name) && s.name !== lastReached,
        active: s.name === lastReached
      }))
    }
  },
  onShow() {
    this.refreshQuota()
    this.adEnabled = !!uni.getStorageSync('tone_ad_enabled')
    this.adUnitId = uni.getStorageSync('tone_ad_unit_id') || ''
    this.devMode = !!uni.getStorageSync('tone_dev_mode')
    // 从结果页/历史页返回时也会触发 onShow：最近记录随之刷新
    this.loadRecent()
    this.loadProfile()
  },
  /**
   * 分享裂变：个人号唯一的免费获客渠道，右上角菜单 + 朋友圈。
   * 分享内容刻意不带用户原文（隐私优先），只推工具本身。
   */
  onShareAppMessage() {
    return {
      title: '措辞师 · 把每句话都说得漂亮',
      path: '/pages/index/index'
    }
  },
  onShareTimeline() {
    return {
      title: '措辞师 · 把每句话都说得漂亮'
    }
  },
  onHide() {
    this.stopTipTimer()
    this.stopElapsed()
  },
  onUnload() {
    this.stopTipTimer()
    // 离开页面时断开 SSE 连接，避免后端继续为已离开的用户白白跑完 60 秒的模型调用
    if (this.streamTask) {
      this.streamTask.abort()
      this.streamTask = null
    }
  },
  methods: {
    refreshQuota() {
      quotaCheck()
        .then((q) => {
          this.quota = q
        })
        .catch(() => {})
    },
    /** 资料加载：失败静默（未登录/后端不可达时头像昵称显示默认值即可） */
    loadProfile() {
      getProfile()
        .then((vo) => {
          this.profile.nickname = vo.nickname || ''
          this.profile.avatar = vo.avatar || ''
        })
        .catch(() => {})
    },
    /** chooseAvatar 回调：拿临时文件 → 上传换可访问 URL */
    onChooseAvatar(e) {
      const tempPath = e.detail.avatarUrl
      if (!tempPath) return
      uploadAvatar(tempPath)
        .then((vo) => {
          this.profile.avatar = vo.avatar
          uni.showToast({ title: '头像已更新', icon: 'success' })
        })
        .catch((err) => {
          uni.showToast({ title: (err && err.message) || '头像上传失败', icon: 'none' })
        })
    },
    /** 昵称失焦保存：有变化才发请求（input type=nickname 自带微信昵称建议） */
    onNicknameBlur() {
      const nickname = (this.profile.nickname || '').trim()
      if (!nickname) return
      saveNickname(nickname)
        .then(() => {
          this.profile.nickname = nickname
          uni.showToast({ title: '昵称已保存', icon: 'success' })
        })
        .catch((err) => {
          uni.showToast({ title: (err && err.message) || '昵称保存失败', icon: 'none' })
        })
    },
    fillExample(ex) {
      this.text = ex.text
      this.scene = ex.scene
    },
    startTipTimer() {
      let idx = 0
      this.checkTip = CHECK_TIPS[0]
      this.stopTipTimer()
      this.tipTimer = setInterval(() => {
        idx = (idx + 1) % CHECK_TIPS.length
        this.checkTip = CHECK_TIPS[idx]
      }, 3000)
    },
    stopTipTimer() {
      if (this.tipTimer) {
        clearInterval(this.tipTimer)
        this.tipTimer = null
      }
    },
    /** 进度卡片用计时器：让"慢"可见（真实模型单次 60 秒级） */
    startElapsed() {
      this.elapsed = 0
      this.stopElapsed()
      this.elapsedTimer = setInterval(() => {
        this.elapsed += 1
      }, 1000)
    },
    stopElapsed() {
      if (this.elapsedTimer) {
        clearInterval(this.elapsedTimer)
        this.elapsedTimer = null
      }
    },
    /** 后端 stage 事件驱动：记录到达阶段、更新当前提示；首个真实事件到达后停掉兜底轮播 */
    markStage(name, message) {
      this.stopTipTimer()
      if (!this.stageReached.includes(name)) {
        this.stageReached.push(name)
      }
      this.checkTip = message
    },
    /** 回到初始态：下一次检查开始时调用 */
    resetProgress() {
      this.progressCollapsed = false
      this.progressError = ''
      this.stageReached = []
      this.elapsed = 0
      this.checkTip = CHECK_TIPS[0]
    },
    doCheck() {
      if (!this.text.trim()) {
        uni.showToast({ title: '请输入要检查的文字', icon: 'none' })
        return
      }
      if (this.loading) {
        return
      }
      this.loading = true
      this.resetProgress()
      this.startTipTimer()
      this.startElapsed()
      /**
       * 流式检查：进度提示来自后端 SSE 的 stage 事件（工具实际被调用时才发），
       * 收到第一个 stage 后停掉本地轮播提示，此后全部以真实阶段为准。
       * 相比同步接口的两点收益：65 秒级真实模型不再撞 60 秒超时；提示是事实不是猜测。
       */
      this.streamTask = agentCheckStream({
        data: { text: this.text.trim(), scene: this.scene },
        onEvent: (name, payload) => {
          if (name === 'stage') {
            this.markStage(payload.name, payload.message)
          } else if (name === 'result') {
            this.onCheckResult(payload)
          } else if (name === 'error') {
            // 配额用尽需要弹窗让用户做"看广告"决策；其余错误留在进度卡片里（可收起）
            if (payload.code === 31005) {
              this.handleCheckError(payload)
            } else {
              this.loading = false
              this.stopTipTimer()
              this.stopElapsed()
              this.progressError = payload.message || '检查失败'
            }
          }
        },
        onDone: () => {
          this.loading = false
          this.stopTipTimer()
          this.stopElapsed()
          this.refreshQuota()
          this.streamTask = null
        },
        onFail: (err) => {
          // fail 与 success 互斥，这里负责收尾，避免 loading 卡死
          this.loading = false
          this.stopTipTimer()
          this.stopElapsed()
          this.streamTask = null
          this.progressError = (err && err.message) || '网络异常，请稍后重试'
        }
      })
    },
    onCheckResult(result) {
      // 成功即收起进度卡片（隐藏），跳结果页
      this.loading = false
      this.stopTipTimer()
      this.stopElapsed()
      if (result.blocked) {
        // 敏感词拦截不算成功：保留原文，方便用户改写措辞后重试
        uni.showModal({
          title: '无法处理',
          content: `内容包含敏感词：${result.hits.join('、')}`,
          showCancel: false
        })
        return
      }
      saveHistory({
        recordId: result.recordId,
        scene: result.scene,
        originalText: this.text.trim(),
        scores: result.scores,
        issues: result.issues,
        versions: result.versions,
        finalText: '',
        messages: []
      })
      // 成功才清空输入框（结果已存历史与详情页）；失败/拦截一律保留原文
      this.text = ''
      uni.navigateTo({ url: `/pages/result/result?recordId=${result.recordId}` })
    },
    handleCheckError(err) {
      const code = err && err.code
      if (code === 31005) {
        uni.showModal({
          title: '次数用完啦',
          content: this.adEnabled
            ? '今日免费次数已用完，观看一段视频可再获得 1 次机会'
            : '今日免费次数已用完，明天再来吧',
          showCancel: !!this.adEnabled,
          confirmText: this.adEnabled ? '看视频+1' : '知道了',
          success: (res) => {
            if (res.confirm && this.adEnabled) {
              this.watchAd()
            }
          }
        })
      } else if (code === 31007 || code === 31008) {
        // Agent 轮次超限 / 模型返回异常：配额已在后端退还
        uni.showModal({
          title: '服务开小差了',
          content: '本次未消耗次数，请重试',
          showCancel: false,
          confirmText: '知道了'
        })
      } else {
        uni.showToast({ title: (err && err.message) || '检查失败，请稍后重试', icon: 'none' })
      }
    },
    /** 激励视频：必须完整看完才发奖；看完自动开始本次检查 */
    watchAd() {
      if (!this.adUnitId) {
        uni.showToast({ title: '广告位未配置', icon: 'none' })
        return
      }
      const ad = uni.createRewardedVideoAd({ adunitId: this.adUnitId })
      const onClose = (res) => {
        ad.offClose(onClose)
        if (res && res.isEnded === true) {
          quotaReward()
            .then(() => {
              uni.showToast({ title: '获得 1 次机会！', icon: 'success' })
              this.refreshQuota()
              // 看广告就是为了这次检查：拿到奖励后自动开始，不让用户再点一遍
              setTimeout(() => this.doCheck(), 600)
            })
            .catch((err) => {
              uni.showToast({ title: err.message || '奖励领取失败', icon: 'none' })
            })
        } else {
          uni.showToast({ title: '需完整观看视频才能获得奖励', icon: 'none' })
        }
      }
      ad.onError(() => {
        uni.showToast({ title: '广告加载失败', icon: 'none' })
        ad.offClose(onClose)
      })
      ad.onClose(onClose)
      ad.show().catch(() => {
        // 首次 show 失败时手动加载后重试一次
        ad.load().then(() => ad.show()).catch(() => uni.showToast({ title: '广告拉取失败', icon: 'none' }))
      })
    },
    /** 开发辅助：一键重置今日配额（免费+奖励），重置后立即刷新显示 */
    resetQuota() {
      quotaReset()
        .then((snapshot) => {
          this.quota = snapshot
          uni.showToast({ title: '已重置', icon: 'success' })
        })
        .catch((err) => {
          uni.showToast({ title: (err && err.message) || '重置失败', icon: 'none' })
        })
    },
    /**
     * 最近记录相关：以后端为事实源（换设备/清缓存/切 appid 都不丢——评审实测踩过
     * "换了真实 appid 后本地历史清零、首页卡片消失"的坑）；接口失败才退回本地缓存。
     * 从结果页返回时 onShow 会再次触发，新记录自动出现在最上面。
     */
    loadRecent() {
      agentRecent(3)
        .then((rows) => {
          this.recentRecords = rows
        })
        .catch(() => {
          // 后端不可达时退回本地缓存（老版本的纯本地行为）
          this.recentRecords = getHistory().slice(0, 3)
        })
    },
    openResult(recordId) {
      uni.navigateTo({ url: `/pages/result/result?recordId=${recordId}` })
    },
    sceneLabel(scene) {
      const map = {
        workplace: '职场',
        social: '社交',
        relationship: '亲密关系',
        moments: '朋友圈',
        service: '客服'
      }
      return map[scene] || scene
    },
    /** 当天只显示时分，跨天带日期——用户一眼能分清"刚发的"和"前几天的" */
    formatTime(ts) {
      if (!ts) return ''
      const d = new Date(ts)
      const pad = (n) => `${n}`.padStart(2, '0')
      const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`
      return d.toDateString() === new Date().toDateString()
        ? hm
        : `${d.getMonth() + 1}-${pad(d.getDate())} ${hm}`
    },
    /** 首页快捷复制：直接抄走该条记录的最终文本（没微调过就复制最后一个改写版本） */
    copyLatest(item) {
      const text = item.finalText || (item.versions && item.versions.length
        ? item.versions[item.versions.length - 1].text
        : '')
      if (!text) {
        uni.showToast({ title: '该记录没有可用文本', icon: 'none' })
        return
      }
      uni.setClipboardData({
        data: text,
        success: () => uni.showToast({ title: '已复制最终版', icon: 'success' })
      })
    },
    /**
     * 快捷重跑：回填原文+场景后立即发起检查。
     * 用户点了"重跑"即视为对消耗一次配额的明确授权；次数不足时走既有的
     * 配额弹窗（有广告位时可看广告续命），与主按钮行为一致。
     */
    rerunRecord(item) {
      if (this.loading) {
        return
      }
      if (this.remain <= 0) {
        this.handleCheckError({ code: 31005 })
        return
      }
      this.text = item.originalText || ''
      this.scene = item.scene || this.scene
      this.doCheck()
    },
    goHistory() {
      uni.navigateTo({ url: '/pages/history/history' })
    }
  }
}
</script>

<style scoped>
/* 底部按钮是 fixed 定位，给内容留出高度，避免"最近记录"最后一行被按钮盖住 */
.container {
  padding-bottom: 180rpx;
}

/* 我的资料卡片：头像按钮内嵌官方 chooseAvatar 能力 */
.profile-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx 28rpx;
}

.avatar-btn {
  position: relative;
  width: 96rpx;
  height: 96rpx;
  padding: 0;
  margin: 0;
  border-radius: 50%;
  background: transparent;
  line-height: 1;
}

.avatar-btn::after {
  border: none;
}

.avatar-img {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: #f6f7fb;
}

.avatar-edit {
  position: absolute;
  right: -4rpx;
  bottom: -4rpx;
  width: 36rpx;
  height: 36rpx;
  line-height: 36rpx;
  text-align: center;
  font-size: 18rpx;
  color: #ffffff;
  background: #4f7cff;
  border-radius: 50%;
}

.profile-info {
  flex: 1;
}

.nickname-input {
  height: 56rpx;
  font-size: 30rpx;
  font-weight: 600;
}

.profile-sub {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: #909399;
}

.scene-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.scene-item {
  width: 200rpx;
  padding: 20rpx 0;
  text-align: center;
  background: #f6f7fb;
  border-radius: 16rpx;
  border: 2rpx solid transparent;
}

.scene-item.active {
  background: #eef2ff;
  border-color: #4f7cff;
}

.scene-icon {
  font-size: 44rpx;
}

.scene-label {
  margin-top: 6rpx;
  font-size: 26rpx;
}

.input-area {
  width: 100%;
  height: 260rpx;
  margin-top: 8rpx;
  padding: 20rpx;
  box-sizing: border-box;
  font-size: 28rpx;
  background: #f6f7fb;
  border-radius: 14rpx;
}

.counter {
  margin-top: 8rpx;
  text-align: right;
  font-size: 22rpx;
  color: #909399;
}

.examples {
  margin-top: 16rpx;
}

.examples-title {
  font-size: 24rpx;
  color: #909399;
  margin-bottom: 10rpx;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}

.chip {
  padding: 8rpx 24rpx;
  font-size: 24rpx;
  background: #eef2ff;
  color: #4f7cff;
  border-radius: 26rpx;
}

.quota {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24rpx 28rpx;
}

.quota-text {
  font-size: 26rpx;
  color: #606266;
}

.quota-num {
  font-size: 36rpx;
  font-weight: 700;
  color: #4f7cff;
}

.quota-total {
  font-size: 22rpx;
  color: #909399;
}

.btn-ad {
  padding: 12rpx 28rpx;
  background: linear-gradient(90deg, #ff9d4d, #ff6b4d);
  border-radius: 32rpx;
  color: #fff;
  font-size: 26rpx;
}

.quota-empty {
  font-size: 24rpx;
  color: #909399;
}

/* 开发辅助按钮：小号描边样式，与广告按钮区分 */
.quota-reset {
  padding: 8rpx 22rpx;
  font-size: 22rpx;
  color: #909399;
  border: 1rpx dashed #c0c4cc;
  border-radius: 26rpx;
}

.action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 20rpx 40rpx calc(20rpx + env(safe-area-inset-bottom));
  background: rgba(246, 247, 251, 0.96);
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.btn-check {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
}

.history-entry {
  font-size: 26rpx;
  color: #4f7cff;
}

/* 进度卡片：内嵌在页面里（非弹框）， loading 期间显示，完成后随 loading 消失 */
.progress-card {
  border: 2rpx solid #dfe6ff;
}

.progress-head {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.progress-spinner {
  width: 32rpx;
  height: 32rpx;
  border: 4rpx solid #eef2ff;
  border-top-color: #4f7cff;
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
  flex-shrink: 0;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.progress-title {
  flex: 1;
  font-size: 27rpx;
  font-weight: 600;
  color: #303133;
}

.progress-elapsed {
  font-size: 22rpx;
  color: #909399;
}

.progress-toggle {
  font-size: 24rpx;
  color: #4f7cff;
}

.progress-steps {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 18rpx;
}

.step {
  padding: 6rpx 18rpx;
  border-radius: 22rpx;
  font-size: 22rpx;
  background: #f6f7fb;
  color: #909399;
}

/* 已走过的阶段 */
.step.done {
  background: #eef7f0;
  color: #3cc97f;
}

/* 当前进行中的阶段 */
.step.active {
  background: #eef2ff;
  color: #4f7cff;
  font-weight: 600;
}

.progress-error {
  margin-top: 14rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: #ff6b4d;
}

/* 最近记录卡片 */
.recent-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8rpx;
}

.recent-title {
  font-size: 30rpx;
  font-weight: 700;
}

.recent-all {
  font-size: 24rpx;
  color: #4f7cff;
}

.recent-item {
  padding: 16rpx 0;
  border-bottom: 1rpx solid #f0f1f5;
}

.recent-item:last-child {
  border-bottom: none;
  padding-bottom: 4rpx;
}

.recent-row1 {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.recent-time {
  font-size: 22rpx;
  color: #909399;
}

.recent-row2 {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 8rpx;
}

.recent-text {
  flex: 1;
  font-size: 26rpx;
  color: #303133;
  /* 单行截断：首页卡片只做预览，全文在详情页 */
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 重跑按钮：与复制同排，主题色描边区分 */
.recent-rerun {
  flex-shrink: 0;
  padding: 4rpx 18rpx;
  margin-right: 12rpx;
  font-size: 22rpx;
  color: #3cc97f;
  border: 1rpx solid #3cc97f;
  border-radius: 22rpx;
}

.recent-copy {
  flex-shrink: 0;
  padding: 4rpx 18rpx;
  font-size: 22rpx;
  color: #4f7cff;
  border: 1rpx solid #4f7cff;
  border-radius: 22rpx;
}
</style>
