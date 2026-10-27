<template>
  <view class="container">
    <!-- 回源加载提示：本地缓存缺失时从后端拉详情，期间其余卡片因 record 为空对象自然隐藏 -->
    <view v-if="loadingDetail" class="card">
      <view class="chat-pending">正在从云端加载记录…</view>
    </view>
    <!-- 评分 -->
    <view v-if="record.scores" class="card">
      <view class="section-title">语气诊断</view>
      <view class="score-row">
        <view class="score-label">礼貌度</view>
        <progress :percent="record.scores.politeness" stroke-width="10" activeColor="#4f7cff" class="score-bar" />
        <view class="score-num">{{ record.scores.politeness }}</view>
        <view class="tag" :class="scoreClass(record.scores.politeness)">{{ scoreLabel(record.scores.politeness) }}</view>
      </view>
      <view class="score-row">
        <view class="score-label">专业度</view>
        <progress :percent="record.scores.professionalism" stroke-width="10" activeColor="#3cc97f" class="score-bar" />
        <view class="score-num">{{ record.scores.professionalism }}</view>
        <view class="tag" :class="scoreClass(record.scores.professionalism)">{{ scoreLabel(record.scores.professionalism) }}</view>
      </view>
      <view class="score-row">
        <view class="score-label">情感倾向</view>
        <progress
          :percent="emotionPercent"
          stroke-width="10"
          :activeColor="record.scores.emotion < 0 ? '#ff6b4d' : '#ffb84d'"
          class="score-bar"
        />
        <view class="score-num">{{ record.scores.emotion }}</view>
        <view class="tag" :class="emotionClass(record.scores.emotion)">{{ emotionLabel(record.scores.emotion) }}</view>
      </view>
    </view>

    <!-- 问题列表 -->
    <view v-if="record.issues && record.issues.length" class="card">
      <view class="section-title">发现问题（{{ record.issues.length }}）</view>
      <view v-for="(issue, i) in record.issues" :key="i" class="issue">
        <view class="issue-head">
          <view class="tag" :class="levelClass(issue.level)">{{ levelText(issue.level) }}</view>
          <text class="issue-quote">「{{ issue.quote }}」</text>
        </view>
        <view class="issue-suggestion">{{ issue.suggestion }}</view>
      </view>
    </view>

    <!-- 最新版本（微调后出现）：这就是用户要拿去用的那一版 -->
    <view v-if="record.finalText" class="card version-latest">
      <view class="section-title">
        最新版本
        <text class="latest-badge">微调后</text>
      </view>
      <view class="version-text">{{ record.finalText }}</view>
      <button class="btn-primary btn-copy-latest" @tap="copyText(record.finalText)">复制最新版本</button>
    </view>

    <!-- 改写版本卡片：回源未完成时 versions 未定义，整体隐藏 -->
    <view v-if="record.versions" class="card">
      <view class="section-title">改写版本（点击复制，微调后可作为回退来源）</view>
      <view
        v-for="(v, i) in record.versions"
        :key="i"
        class="version"
        :class="{ selected: selected === i }"
        @tap="selected = i"
      >
        <view class="version-head">
          <view class="version-style">{{ v.style }}</view>
          <view class="btn-copy" @tap.stop="copyText(v.text)">复制</view>
        </view>
        <view class="version-text">{{ v.text }}</view>
      </view>
    </view>

    <!-- 微调对话 -->
    <view class="card">
      <view class="section-title">不满意？继续微调</view>
      <view v-for="(msg, i) in refineMessages" :key="'m' + i" class="chat-row" :class="msg.role">
        <view class="chat-bubble">
          <view>{{ msg.content }}</view>
          <!-- Agent 回复：可复制，也可"采用此版"（回退到这个版本作为最终文本） -->
          <view v-if="msg.role === 'assistant'" class="chat-actions">
            <view class="chat-action" @tap="copyText(msg.content)">复制</view>
            <view
              v-if="msg.content !== record.finalText"
              class="chat-action"
              @tap="adoptVersion(msg.content)"
            >
              采用此版
            </view>
            <view v-else class="chat-action chat-action-current">当前版本</view>
          </view>
        </view>
      </view>
      <!-- 逐字渲染气泡：delta 事件实时追加，result 事件后由正式气泡接管 -->
      <view v-if="refining" class="chat-row user">
        <view class="chat-bubble chat-pending">{{ liveText || "正在生成…" }}<text class="cursor">|</text></view>
      </view>

      <view class="chips">
        <view v-for="c in quickChips" :key="c" class="chip" @tap="instruction = c">{{ c }}</view>
      </view>

      <view class="refine-input-row">
        <input
          v-model="instruction"
          class="refine-input"
          placeholder="例：再委婉一点 / 再正式一点"
          confirm-type="send"
          @confirm="doRefine"
        />
        <button class="btn-primary btn-refine" :disabled="refining" @tap="doRefine">发送</button>
      </view>
    </view>

    <!--
      分享裂变：个人号唯一的免费获客渠道。
      分享内容刻意不带用户原文（隐私优先——卡片会被朋友看到），只推工具本身；
      open-type=share 让页内按钮直接触发转发，与右上角菜单等效。
    -->
    <view class="card share-card">
      <button class="btn-share" open-type="share">
        推荐给朋友一起用
      </button>
    </view>

    <!-- 合规：AI 生成内容声明（小程序 AI 类目审核要求） -->
    <view class="ai-disclaimer">内容由 AI 生成，仅供参考</view>

    <view style="height: 40rpx" />
  </view>
</template>

<script>
import { agentRefineStream, agentRestore, agentRecord } from '@/utils/api'
import { getHistory, saveHistory } from '@/utils/history'

export default {
  data() {
    return {
      record: {},
      selected: -1,
      instruction: '',
      refining: false,
      // 详情回源加载中：期间模板依赖 record 字段做 v-if 保护，避免 undefined 报错
      loadingDetail: false,
      /** 微调流式输出正在逐字渲染的文本：delta 事件追加，result 事件后清空由正式气泡接管 */
      liveText: "",
      refineMessages: [],
      quickChips: ['再委婉一点', '再正式一点', '简短一些', '加个表情'],
      /** SSE 任务句柄：离开页面时 abort，避免后端为已离开的用户继续跑模型调用 */
      refineTask: null
    }
  },
  onUnload() {
    if (this.refineTask) {
      this.refineTask.abort()
      this.refineTask = null
    }
  },
  /**
   * 分享：标题不带用户原文（卡片会被朋友看到，隐私优先），
   * path 直接落首页——新用户进来自己输入，转化路径反而更短
   */
  onShareAppMessage() {
    return {
      title: '措辞师 · 我把一条"冲"消息改得体了',
      path: '/pages/index/index'
    }
  },
  onShareTimeline() {
    return {
      title: '措辞师 · 我把一条"冲"消息改得体了'
    }
  },
  computed: {
    /** 情感分 -100~100 映射到 0~100 的进度条 */
    emotionPercent() {
      if (!this.record.scores) {
        return 0
      }
      return Math.round((this.record.scores.emotion + 100) / 2)
    }
  },
  onLoad(options) {
    const recordId = Number(options.recordId)
    const list = getHistory()
    const found = list.find((item) => item.recordId === recordId)
    if (found) {
      this.record = found
      // 恢复微调对话历史，离开页面再进来不丢上下文
      this.refineMessages = Array.isArray(found.messages) ? found.messages : []
    } else {
      // 本地缓存没有这条记录（换设备/清缓存/切 appid 都会发生）：从后端回源并写回本地，
      // 之后微调/复制/再次进入都走正常本地路径
      this.loadingDetail = true
      agentRecord(recordId)
        .then((detail) => {
          this.record = {
            recordId: detail.record.recordId,
            scene: detail.record.scene,
            originalText: detail.record.originalText,
            scores: detail.record.scores,
            issues: detail.record.issues,
            versions: detail.record.versions,
            finalText: detail.record.finalText,
            time: detail.record.time,
            messages: (detail.messages || []).map((m) => ({ role: m.role, content: m.content }))
          }
          this.refineMessages = this.record.messages
          this.persistRecord()
        })
        .catch(() => {
          uni.showToast({ title: '记录不存在', icon: 'none' })
        })
        .finally(() => {
          this.loadingDetail = false
        })
    }
  },
  methods: {
    /** 0-100 分的参照系 */
    scoreLabel(v) {
      if (v >= 80) return '优秀'
      if (v >= 60) return '良好'
      if (v >= 40) return '一般'
      return '待改进'
    },
    scoreClass(v) {
      if (v >= 80) return 'tag-low'
      if (v >= 60) return 'tag-mid'
      return 'tag-high'
    },
    emotionLabel(v) {
      if (v <= -30) return '偏负面'
      if (v >= 30) return '积极'
      return '中性'
    },
    emotionClass(v) {
      if (v <= -30) return 'tag-high'
      if (v >= 30) return 'tag-low'
      return 'tag-mid'
    },
    levelText(level) {
      return level === 'high' ? '严重' : level === 'mid' ? '中等' : '轻微'
    },
    levelClass(level) {
      return level === 'high' ? 'tag-high' : level === 'mid' ? 'tag-mid' : 'tag-low'
    },
    copyText(text) {
      uni.setClipboardData({
        data: text,
        success: () => uni.showToast({ title: '已复制', icon: 'success' })
      })
    },
    persistRecord() {
      // 把微调对话一并写入本地历史，重新进入页面可完整还原
      saveHistory({ ...this.record, messages: this.refineMessages })
    },
    doRefine() {
      const instruction = this.instruction.trim()
      if (!instruction || this.refining) {
        return
      }
      this.refining = true
      this.refineMessages.push({ role: 'user', content: instruction })
      /**
       * 流式微调：delta 事件把模型逐 token 的输出实时追加到 liveText（前端逐字渲染）；
       * result 事件里的 text 才是服务端解析完 JSON 外壳、持久化过的最终文本——
       * 以它为准回填正式气泡。整个过程配额不受影响（微调不消耗次数）。
       */
      this.refineTask = agentRefineStream({
        data: { recordId: this.record.recordId, instruction },
        onEvent: (name, payload) => {
          if (name === 'delta') {
            this.liveText += payload.text
          } else if (name === 'result') {
            this.refineMessages.push({ role: 'assistant', content: payload.text })
            this.instruction = ''
            this.record.finalText = payload.text
            this.persistRecord()
            uni.showToast({ title: '已更新最新版本', icon: 'none' })
          } else if (name === 'error') {
            uni.showToast({ title: payload.message || '微调失败', icon: 'none' })
          }
        },
        onDone: () => {
          this.refining = false
          this.liveText = ''
          this.refineTask = null
        },
        onFail: (err) => {
          this.refining = false
          this.liveText = ''
          this.refineTask = null
          uni.showToast({ title: (err && err.message) || '微调失败', icon: 'none' })
        }
      })
    },
    /** 采用历史版本：服务端与本地同步回退，后续微调以该版本为上下文 */
    adoptVersion(text) {
      agentRestore(this.record.recordId, text)
        .then(() => {
          this.record.finalText = text
          this.refineMessages.push({ role: 'user', content: '（采用历史版本）' })
          this.refineMessages.push({ role: 'assistant', content: text })
          this.persistRecord()
          uni.showToast({ title: '已切回该版本', icon: 'none' })
        })
        .catch((err) => {
          uni.showToast({ title: (err && err.message) || '切换失败', icon: 'none' })
        })
    }
  }
}
</script>

<style scoped>
.section-title {
  margin-bottom: 20rpx;
  font-size: 30rpx;
  font-weight: 700;
}

.latest-badge {
  margin-left: 10rpx;
  padding: 2rpx 14rpx;
  font-size: 20rpx;
  font-weight: 400;
  color: #ffffff;
  background: #4f7cff;
  border-radius: 18rpx;
  vertical-align: middle;
}

.score-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 12rpx 0;
}

.score-label {
  width: 130rpx;
  font-size: 26rpx;
  color: #606266;
}

.score-bar {
  flex: 1;
}

.score-num {
  width: 70rpx;
  text-align: right;
  font-size: 26rpx;
  font-weight: 700;
  color: #4f7cff;
}

.issue {
  padding: 14rpx 0;
  border-bottom: 1rpx solid #f0f1f5;
}

.issue:last-child {
  border-bottom: none;
}

.issue-head {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.tag-high {
  background: #ffefeb;
  color: #ff6b4d;
}

.tag-mid {
  background: #fff7e8;
  color: #ff9d4d;
}

.tag-low {
  background: #eef7f0;
  color: #3cc97f;
}

.issue-quote {
  font-size: 26rpx;
  color: #ff6b4d;
}

.issue-suggestion {
  margin-top: 8rpx;
  font-size: 25rpx;
  line-height: 1.6;
  color: #606266;
}

.version-latest {
  border: 2rpx solid #4f7cff;
  background: #f7f9ff;
}

.btn-copy-latest {
  margin-top: 20rpx;
  height: 80rpx;
  line-height: 80rpx;
}

.version {
  padding: 20rpx;
  margin-bottom: 16rpx;
  background: #f6f7fb;
  border-radius: 14rpx;
  border: 2rpx solid transparent;
}

.version.selected {
  background: #eef2ff;
  border-color: #4f7cff;
}

.version-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10rpx;
}

.version-style {
  font-size: 26rpx;
  font-weight: 700;
  color: #4f7cff;
}

.btn-copy {
  padding: 6rpx 22rpx;
  font-size: 22rpx;
  background: #ffffff;
  color: #4f7cff;
  border: 1rpx solid #4f7cff;
  border-radius: 26rpx;
}

.version-text {
  font-size: 26rpx;
  line-height: 1.7;
  color: #303133;
}

.chat-row {
  display: flex;
  margin-bottom: 14rpx;
}

.chat-row.user {
  justify-content: flex-start;
}

.chat-bubble {
  max-width: 90%;
  padding: 14rpx 20rpx;
  background: #f6f7fb;
  border-radius: 14rpx;
  font-size: 25rpx;
  line-height: 1.6;
  white-space: pre-wrap;
}

.chat-pending {
  color: #909399;
}

.cursor {
  margin-left: 4rpx;
  color: #4f7cff;
  animation: blink 0.9s steps(2) infinite;
}

.share-card {
  padding: 20rpx 28rpx;
}

.btn-share {
  width: 100%;
  height: 80rpx;
  line-height: 80rpx;
  font-size: 28rpx;
  color: #4f7cff;
  background: #eef2ff;
  border-radius: 40rpx;
  padding: 0;
  margin: 0;
}

.btn-share::after {
  border: none;
}

.ai-disclaimer {
  text-align: center;
  font-size: 22rpx;
  color: #b0b3b8;
  padding: 0 0 20rpx;
}

@keyframes blink {
  to {
    opacity: 0;
  }
}

.chat-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 10rpx;
  border-top: 1rpx solid #eceef2;
  padding-top: 8rpx;
}

.chat-action {
  font-size: 22rpx;
  color: #4f7cff;
}

.chat-action-current {
  color: #909399;
}

.chips {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
  margin: 10rpx 0 20rpx;
}

.chip {
  padding: 8rpx 24rpx;
  font-size: 24rpx;
  background: #eef2ff;
  color: #4f7cff;
  border-radius: 26rpx;
}

.refine-input-row {
  display: flex;
  gap: 16rpx;
  align-items: center;
}

.refine-input {
  flex: 1;
  height: 72rpx;
  padding: 0 24rpx;
  background: #f6f7fb;
  border-radius: 36rpx;
  font-size: 26rpx;
}

.btn-refine {
  width: 140rpx;
  height: 72rpx;
  line-height: 72rpx;
  font-size: 26rpx;
  margin: 0;
}
</style>
