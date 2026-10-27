<script>
import { ensureLogin } from './utils/request'
import { adConfig } from './utils/api'

export default {
  onLaunch() {
    // 启动即静默登录（dev 模式由后端生成 mock openId，无需真实 appid）
    ensureLogin()
    // 拉取广告位配置，供检查页使用；devMode 一并下发（= mock-login 开关），
    // 首页"重置次数"按钮靠它显示。放这里而不是只放登录回调：老版本登录的本地存储里
    // 没有 devMode 标志且旧 token 一直有效、登录流程不会重跑，这里每次启动都能刷新
    adConfig()
      .then((config) => {
        uni.setStorageSync('tone_ad_unit_id', config.unitId || '')
        uni.setStorageSync('tone_ad_enabled', !!config.enabled)
        uni.setStorageSync('tone_dev_mode', !!config.devMode)
      })
      .catch(() => {})
  }
}
</script>

<style>
page {
  background-color: #f6f7fb;
  font-size: 28rpx;
  color: #303133;
}

.card {
  margin: 24rpx;
  padding: 28rpx;
  background: #ffffff;
  border-radius: 20rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.btn-primary {
  background: #4f7cff;
  color: #ffffff;
  border-radius: 44rpx;
  font-size: 30rpx;
}

.btn-primary[disabled] {
  background: #b8c5ff;
  color: #ffffff;
}

.tag {
  display: inline-block;
  padding: 4rpx 16rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
  background: #eef2ff;
  color: #4f7cff;
}
</style>
