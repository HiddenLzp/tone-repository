<template>
  <view class="container">
    <view v-if="!list.length" class="card empty">还没有记录，先去检查一条消息吧～</view>

    <view
      v-for="item in list"
      :key="item.recordId"
      class="card record"
      @tap="openResult(item.recordId)"
    >
      <view class="record-head">
        <view class="tag">{{ sceneLabel(item.scene) }}</view>
        <view class="record-time">{{ formatTime(item.time) }}</view>
      </view>
      <view class="record-text">{{ item.originalText }}</view>
      <view class="record-scores">
        <text>礼貌 {{ item.scores ? item.scores.politeness : '-' }}</text>
        <text>专业 {{ item.scores ? item.scores.professionalism : '-' }}</text>
        <text>情感 {{ item.scores ? item.scores.emotion : '-' }}</text>
      </view>
    </view>

    <view v-if="list.length" class="clear-bar" @tap="clearAll">清空历史</view>
  </view>
</template>

<script>
import { getHistory, clearHistory } from '@/utils/history'

export default {
  data() {
    return {
      list: []
    }
  },
  onShow() {
    this.list = getHistory()
  },
  methods: {
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
    formatTime(ts) {
      const d = new Date(ts)
      const pad = (n) => `${n}`.padStart(2, '0')
      return `${d.getMonth() + 1}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
    },
    openResult(recordId) {
      uni.navigateTo({ url: `/pages/result/result?recordId=${recordId}` })
    },
    clearAll() {
      uni.showModal({
        title: '提示',
        content: '确定清空全部历史记录吗？（仅清除本机记录）',
        success: (res) => {
          if (res.confirm) {
            clearHistory()
            this.list = []
          }
        }
      })
    }
  }
}
</script>

<style scoped>
.record-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}

.record-time {
  font-size: 22rpx;
  color: #909399;
}

.record-text {
  font-size: 27rpx;
  line-height: 1.6;
  color: #303133;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.record-scores {
  display: flex;
  gap: 28rpx;
  margin-top: 14rpx;
  font-size: 22rpx;
  color: #4f7cff;
}

.empty {
  text-align: center;
  color: #909399;
  font-size: 26rpx;
}

.clear-bar {
  margin: 24rpx;
  text-align: center;
  color: #ff6b4d;
  font-size: 26rpx;
  padding: 20rpx 0;
}
</style>
