<template>
  <div class="dashboard">
    <!-- Token 成本告警横幅：阈值在「运营设置」配置，超限时全页顶部红色提醒 -->
    <el-alert
      v-if="overview.tokenAlertExceeded"
      class="dashboard__alert"
      type="error"
      :closable="false"
      show-icon
      title="Token 成本告警"
      :description="`今日 Token 消耗已达告警阈值（${overview.tokenAlertThreshold}），请注意模型成本，可在运营设置中调整阈值`"
    />
    <!-- 今日概览 -->
    <el-row :gutter="12">
      <el-col v-for="card in cards" :key="card.label" :xs="12" :sm="6">
        <el-card shadow="never" class="dashboard__stat">
          <div class="dashboard__stat-label">{{ card.label }}</div>
          <div class="dashboard__stat-value">{{ card.value }}</div>
          <div class="dashboard__stat-sub" :class="{ warn: card.warn }">{{ card.sub }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势图 -->
    <el-card shadow="never" class="dashboard__block">
      <template #header>
        <div class="dashboard__card-header">
          <span>
            近 7 天请求量与 Token 消耗
            <span v-if="lastUpdated" class="dashboard__updated">更新于 {{ lastUpdated }}</span>
          </span>
          <div class="dashboard__header-tools">
            <el-switch v-model="autoRefresh" active-text="30s 自动刷新" />
            <el-button :icon="Refresh" circle size="small" @click="loadAll" />
          </div>
        </div>
      </template>
      <div ref="trendRef" class="dashboard__chart" />
    </el-card>

    <el-row :gutter="12" class="dashboard__block">
      <!-- 场景分布 -->
      <el-col :xs="24" :sm="8">
        <el-card shadow="never" class="dashboard__half">
          <template #header>
            <span>场景分布（近 7 天）</span>
          </template>
          <div ref="sceneRef" class="dashboard__chart dashboard__chart--pie" />
        </el-card>
      </el-col>
      <!-- 配额漏斗 -->
      <el-col :xs="24" :sm="16">
        <el-card shadow="never" class="dashboard__half">
          <template #header>
            <span>配额漏斗（近 {{ funnel.sinceDays }} 天 · 消耗为失败退还后净值）</span>
          </template>
          <div ref="funnelRef" class="dashboard__chart dashboard__chart--funnel" />
        </el-card>
      </el-col>
    </el-row>

    <!-- Token 排行 -->
    <el-card shadow="never" class="dashboard__block">
      <template #header>
        <span>Token 消耗用户 Top 20</span>
      </template>
      <div ref="topRef" class="dashboard__chart dashboard__chart--bar" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, onMounted, ref} from 'vue'
import * as echarts from 'echarts'
import { Refresh } from '@element-plus/icons-vue'
import { getOverview, getTrend, getSceneStats, getTopUsers, getQuotaFunnel } from '@/api/agent'
import type { Overview, TrendRow, SceneRow, TopUserRow, QuotaFunnel } from '@/api/agent'

const sceneTexts: Record<string, string> = {
  workplace: '职场',
  social: '社交',
  relationship: '亲密关系',
  moments: '朋友圈',
  service: '客服'
}

const overview = ref<Overview>({
  activeUsers: 0,
  requests: 0,
  errorRequests: 0,
  successRate: null,
  promptTokens: 0,
  completionTokens: 0,
  totalTokens: 0,
  avgRounds: null,
  adViews: 0,
  yesterdayRequests: 0,
  yesterdayActiveUsers: 0,
  tokenAlertThreshold: 0,
  tokenAlertExceeded: false
})
const trend = ref<TrendRow[]>([])
const scenes = ref<SceneRow[]>([])
const topUsers = ref<TopUserRow[]>([])
const funnel = ref<QuotaFunnel>({
  freeConsumed: 0,
  rewardConsumed: 0,
  rejected: 0,
  rejectedUsers: 0,
  rewardClaimed: 0,
  claimedUsers: 0,
  sinceDays: 7
})
const lastUpdated = ref('')

const cards = computed(() => [
  {
    label: '今日活跃用户',
    value: overview.value.activeUsers,
    sub: `昨日 ${overview.value.yesterdayActiveUsers}`,
    warn: false
  },
  {
    label: '今日请求量（成功）',
    value: overview.value.requests,
    sub:
      `失败 ${overview.value.errorRequests}` +
      (overview.value.successRate === null ? '' : ` · 成功率 ${overview.value.successRate}%`),
    warn: (overview.value.errorRequests ?? 0) > 0
  },
  {
    label: '今日 Token 消耗',
    value: overview.value.totalTokens,
    sub: overview.value.avgRounds === null ? '' : `平均 ${overview.value.avgRounds} 轮/次`,
    warn: false
  },
  {
    label: '今日广告观看',
    value: overview.value.adViews,
    sub: '',
    warn: false
  }
])

/** ECharts 实例与容器 */
const trendRef = ref<HTMLDivElement>()
const sceneRef = ref<HTMLDivElement>()
const funnelRef = ref<HTMLDivElement>()
const topRef = ref<HTMLDivElement>()
let trendChart: echarts.ECharts | null = null
let sceneChart: echarts.ECharts | null = null
let funnelChart: echarts.ECharts | null = null
let topChart: echarts.ECharts | null = null

const autoRefresh = ref(true)
let timer: number | null = null

/** 后端 GROUP BY 只返回有数据的日期，画折线前补零，避免折线跨天误导 */
function fillTrendDays(rows: TrendRow[], days = 7): TrendRow[] {
  const filled: TrendRow[] = []
  const today = new Date()
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(today)
    d.setDate(d.getDate() - i)
    const key = `${d.getFullYear()}-${`${d.getMonth() + 1}`.padStart(2, '0')}-${`${d.getDate()}`.padStart(2, '0')}`
    const found = rows.find((r) => r.date === key)
    filled.push(found ?? { date: key, requests: 0, promptTokens: 0, completionTokens: 0 })
  }
  return filled
}

function renderTrend(): void {
  if (!trendRef.value) {
    return
  }
  trendChart ??= echarts.init(trendRef.value)
  const rows = fillTrendDays(trend.value)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['请求量', 'prompt tokens', 'completion tokens'] },
    grid: { left: 48, right: 24, top: 40, bottom: 28 },
    xAxis: { type: 'category', data: rows.map((t) => t.date) },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '请求量', type: 'line', smooth: true, data: rows.map((t) => t.requests) },
      { name: 'prompt tokens', type: 'line', smooth: true, data: rows.map((t) => t.promptTokens) },
      { name: 'completion tokens', type: 'line', smooth: true, data: rows.map((t) => t.completionTokens) }
    ]
  })
}

function renderScene(): void {
  if (!sceneRef.value) {
    return
  }
  sceneChart ??= echarts.init(sceneRef.value)
  sceneChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}：{c} 次（{d}%）' },
    legend: { bottom: 0, icon: 'circle' },
    series: [
      {
        type: 'pie',
        radius: ['38%', '62%'],
        center: ['50%', '44%'],
        label: { formatter: '{b}\n{c}' },
        data: scenes.value.map((s) => ({ name: sceneTexts[s.scene] ?? s.scene, value: s.requests }))
      }
    ]
  })
}

function renderFunnel(): void {
  if (!funnelRef.value) {
    return
  }
  funnelChart ??= echarts.init(funnelRef.value)
  const f = funnel.value
  const items = [
    { name: '免费额度消耗', value: f.freeConsumed, extra: '' },
    { name: '用尽被拒', value: f.rejected, extra: ` · ${f.rejectedUsers} 人` },
    { name: '广告奖励领取', value: f.rewardClaimed, extra: ` · ${f.claimedUsers} 人` },
    { name: '奖励额度消耗', value: f.rewardConsumed, extra: '' }
  ]
  funnelChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: Array<{ name: string; value: number }>) => {
        const item = items.find((i) => i.name === params[0]?.name)
        return `${params[0]?.name ?? ''}：${params[0]?.value ?? 0} 次${item?.extra ?? ''}`
      }
    },
    grid: { left: 120, right: 48, top: 24, bottom: 24 },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: {
      type: 'category',
      data: items.map((i) => i.name),
      axisLabel: { width: 100 }
    },
    series: [
      {
        type: 'bar',
        barWidth: 26,
        data: items.map((i, idx) => ({
          value: i.value,
          // 拒绝标红提醒，其余主题蓝
          itemStyle: { borderRadius: [0, 4, 4, 0], color: idx === 1 ? '#ee6666' : '#5470c6' }
        })),
        label: { show: true, position: 'right' }
      }
    ]
  })
}

function renderTop(): void {
  if (!topRef.value) {
    return
  }
  topChart ??= echarts.init(topRef.value)
  // 倒序让最大值显示在顶部
  const sorted = [...topUsers.value].reverse()
  topChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: Array<{ name: string; value: number }>) =>
        `${params[0]?.name ?? ''}<br/>累计 Token：${params[0]?.value ?? 0}`
    },
    grid: { left: 200, right: 40, top: 16, bottom: 28 },
    xAxis: { type: 'value', minInterval: 1 },
    yAxis: {
      type: 'category',
      data: sorted.map((t) => t.nickname || t.openId),
      axisLabel: { width: 170, overflow: 'truncate' }
    },
    series: [
      {
        name: '累计 Token',
        type: 'bar',
        data: sorted.map((t) => t.totalTokens),
        itemStyle: { borderRadius: [0, 4, 4, 0] }
      }
    ]
  })
}

async function loadAll(): Promise<void> {
  const [ov, tr, sc, tp, fn] = await Promise.all([
    getOverview(),
    getTrend(7),
    getSceneStats(),
    getTopUsers(20),
    getQuotaFunnel()
  ])
  overview.value = ov
  trend.value = tr
  scenes.value = sc
  topUsers.value = tp
  funnel.value = fn
  lastUpdated.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  await nextTick(() => {
    renderTrend()
    renderScene()
    renderFunnel()
    renderTop()
  })
}

function startTimer(): void {
  stopTimer()
  timer = window.setInterval(loadAll, 30_000)
}

function stopTimer(): void {
  if (timer !== null) {
    window.clearInterval(timer)
    timer = null
  }
}

function onResize(): void {
  trendChart?.resize()
  sceneChart?.resize()
  funnelChart?.resize()
  topChart?.resize()
}

onMounted(async () => {
  await loadAll()
  window.addEventListener('resize', onResize)
  startTimer()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  stopTimer()
  trendChart?.dispose()
  sceneChart?.dispose()
  funnelChart?.dispose()
  topChart?.dispose()
  trendChart = null
  sceneChart = null
  funnelChart = null
  topChart = null
})
</script>

<style scoped lang="scss">
.dashboard {
  &__alert {
    margin-bottom: 12px;
  }

  &__stat {
    :deep(.el-card__body) {
      padding: 16px 20px;
    }
  }

  &__stat-label {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }

  &__stat-value {
    margin-top: 6px;
    font-size: 26px;
    font-weight: 600;
  }

  &__stat-sub {
    margin-top: 4px;
    min-height: 18px;
    font-size: 12px;
    color: var(--el-text-color-secondary);

    &.warn {
      color: var(--el-color-danger);
    }
  }

  &__block {
    margin-top: 12px;
  }

  &__card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__header-tools {
    display: flex;
    gap: 12px;
    align-items: center;
  }

  &__updated {
    margin-left: 10px;
    font-size: 12px;
    font-weight: 400;
    color: var(--el-text-color-secondary);
  }

  &__half {
    height: 100%;
  }

  &__chart {
    width: 100%;
    height: 340px;

    &--pie {
      height: 420px;
    }

    &--funnel {
      height: 420px;
    }

    &--bar {
      height: 420px;
    }
  }
}
</style>
