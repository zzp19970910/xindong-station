<template>
  <div class="page">
    <van-nav-bar title="金币中心" left-arrow fixed placeholder @click-left="$router.back()" />

    <div class="head-card">
      <div>
        <div class="subtle" style="color:rgba(255,255,255,0.85);">💰 金币总览</div>
        <div style="font-size:44px;font-weight:900;margin-top:8px;">{{ safeNum(ov.total) }}</div>
        <div class="subtle" style="color:rgba(255,255,255,0.85);margin-top:4px;">
          累计收入 +{{ safeNum(ov.earned) }} · 已支出 -{{ safeNum(ov.spent) }}
        </div>
      </div>
      <div style="width:72px;height:72px;border-radius:50%;background:rgba(255,255,255,0.2);display:flex;align-items:center;justify-content:center;font-size:40px;">💰</div>
    </div>

    <van-tabs v-model:active="tab" sticky offset-top="46px" line-width="24px" color="#B8860B">
      <van-tab title="明细" name="log">
        <div style="padding:12px 16px;">
          <div class="filter-bar">
            <div v-for="f in filters" :key="f.k"
                 :class="['filter-chip', { active: filter===f.k }]"
                 @click="onFilter(f.k)">
              {{ f.l }}
            </div>
          </div>
        </div>

        <div class="card">
          <div v-if="!logsFiltered.length" class="subtle" style="text-align:center;padding:40px 0;">暂无交易记录</div>
          <div v-for="l in logsFiltered" :key="l.id" class="row-line">
            <div style="width:40px;height:40px;border-radius:12px;background:safeNum(l.delta)>0?'#FFF2CC':'#FFE4E4';display:flex;align-items:center;justify-content:center;font-size:20px;">
              {{ iconOf(reasonOf(l), safeNum(l.delta)) }}
            </div>
            <div style="flex:1;margin:0 12px;min-width:0;">
              <div style="font-weight:600;">{{ reasonLabelOf(l) }}</div>
              <div class="subtle" style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">
                {{ fmt(l.createdAt) }} · 余额 {{ safeNum((l as any).balanceAfter) }}💰
              </div>
            </div>
            <div style="text-align:right;">
              <div :style="{color:safeNum(l.delta)>0?'#B8860B':'#FF4D4F',fontWeight:800,fontSize:'16px'}">
                {{ safeNum(l.delta)>0?'+':'' }}{{ safeNum(l.delta) }}
              </div>
            </div>
          </div>
        </div>
      </van-tab>

      <van-tab title="统计" name="pie">
        <div class="card">
          <div class="card-title"><span>📊 近 7 天收支曲线</span></div>
          <div v-if="ov.last7?.length" style="height:180px;position:relative;">
            <svg viewBox="0 0 300 160" width="100%" height="100%" preserveAspectRatio="none" style="display:block;">
              <defs>
                <linearGradient id="g1" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stop-color="#FF6B9D" stop-opacity="0.4"/>
                  <stop offset="100%" stop-color="#FF6B9D" stop-opacity="0"/>
                </linearGradient>
              </defs>
              <polyline :points="linePoints" fill="none" stroke="#FF6B9D" stroke-width="2" />
              <polygon :points="`${linePoints} ${chartMaxX},160 0,160`" fill="url(#g1)" />
              <circle v-for="(p,i) in pointArr" :key="i" :cx="p.x" :cy="p.y" r="3" fill="#FF6B9D" />
            </svg>
            <div style="display:flex;justify-content:space-between;margin-top:4px;" class="subtle">
              <span v-for="(d,i) in ov.last7" :key="i">{{ d.date?.slice(5) || '' }}</span>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-title"><span>🥧 近 30 天支出分类</span></div>
          <div v-if="!ov.reasonsPie?.length" class="subtle" style="text-align:center;padding:20px 0;">暂无足够数据</div>
          <div v-for="(p,i) in ov.reasonsPie" :key="i" style="margin:10px 0;">
            <div style="display:flex;justify-content:space-between;margin-bottom:4px;">
              <span style="font-weight:600;">{{ p.reason }} · {{ safeNum(p.count) }}笔</span>
              <span style="color:#FF4D4F;">-{{ safeNum(p.total) }}</span>
            </div>
            <van-progress :percentage="piePct(p.total)" :color="pieColor(i)" stroke-width="6" />
          </div>
        </div>
      </van-tab>

      <van-tab title="获取" name="how">
        <div class="card">
          <div class="card-title"><span>💰 金币获取方式</span></div>
          <div v-for="(t,i) in earnList" :key="i"
               class="row-line earn-row"
               :class="{ 'clickable': !!t.path }"
               @click="onEarn(t)">
            <div style="width:44px;height:44px;border-radius:12px;background:#FFF2CC;display:flex;align-items:center;justify-content:center;font-size:22px;">{{ t.e }}</div>
            <div style="flex:1;margin:0 12px;min-width:0;">
              <div style="font-weight:600;">{{ t.t }}</div>
              <div class="subtle">{{ t.d }}</div>
            </div>
            <span class="tag-pill" style="background:#FFF2CC;color:#B8860B;">+{{ t.c }}💰</span>
            <span v-if="t.path" class="go-arrow">›</span>
          </div>
        </div>

        <div class="card" style="background:linear-gradient(135deg,#FFFBF0 0%,#FFF7FA 100%);">
          <div class="card-title"><span>💡 提示</span></div>
          <div class="subtle" style="line-height:1.8;">
            · 每日完成全部任务可获得约 <b>30+</b> 金币<br/>
            · 每完成 10 件清单 → 额外里程碑奖励<br/>
            · 新的一周会刷新任务和答题奖励<br/>
            · 金币可在心愿商城兑换彼此的小心愿 🎁
          </div>
        </div>
      </van-tab>
    </van-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { coinApi, type CoinLog, type CoinOverview } from '@/api'
import { useAuthStore } from '@/stores/auth.store'

const auth = useAuthStore()
const router = useRouter()
const tab = ref('log')
const filter = ref('all')
const ov = reactive<CoinOverview>({ total: 0, earned: 0, spent: 0, last7: [], reasonsPie: [] })
const logs = ref<CoinLog[]>([])

const filters = [
  { k: 'all',  l: '全部' },
  { k: 'in',   l: '仅收入' },
  { k: 'out',  l: '仅支出' },
  { k: 'wish', l: '心愿' },
  { k: 'quiz', l: '默契' },
  { k: 'check',l: '清单' }
]

interface EarnItem { e: string; t: string; d: string; c: any; path?: string }
const earnList: EarnItem[] = [
  { e: '😊', t: '心情打卡',     d: '每日一次，记录今天的心情',          c: 5,      path: '/app/record?tab=mood' },
  { e: '❓', t: '每日默契题',   d: '答完3道双方都答才算',              c: 10,     path: '/app/interactive/quiz' },
  { e: '🎡', t: '破冰任务',     d: '完成抽中的任务，越难奖励越多',      c: '10~50',path: '/app/interactive/icebreak' },
  { e: '✅', t: '完成清单',     d: '每完成一件清单中的事',              c: 5,      path: '/app/record?tab=check' },
  { e: '📖', t: '写日记',       d: '发布一篇日记',                      c: 8,      path: '/app/record?tab=diary' },
  { e: '💌', t: '写情书',       d: '给TA发一封信',                      c: 15,     path: '/app/letters/write' },
  { e: '🧩', t: '默契游戏',     d: '完成一整轮默契PK',                  c: 20,     path: '/app/interactive/tacit' },
  { e: '🏆', t: '里程碑奖励',   d: '累计完成10/30/60/100件清单',        c: '50~300' }
]

function onFilter(k: string) {
  filter.value = k
}

function reasonOf(l: any): string {
  return String(l?.reason || l?.reasonCode || l?.reasonLabel || l?.reasonText || '')
}
function reasonLabelOf(l: any): string {
  return String(l?.reasonText || l?.reasonLabel || l?.reason || '')
}
function reasonMatch(l: any, re: RegExp): boolean {
  const hay = [
    l?.reason, l?.reasonCode, l?.reasonLabel, l?.reasonText, l?.desc, l?.remark
  ].map(x => String(x || '')).join(' | ')
  return re.test(hay)
}

const logsFiltered = computed(() => {
  let arr = logs.value
  if (filter.value === 'in')    arr = arr.filter(x => safeNum((x as any).delta) > 0)
  else if (filter.value === 'out') arr = arr.filter(x => safeNum((x as any).delta) < 0)
  else if (filter.value === 'wish')  arr = arr.filter(x => reasonMatch(x, /WISH|wish|心愿|🎁/))
  else if (filter.value === 'quiz')  arr = arr.filter(x => reasonMatch(x, /QUIZ|quiz|DAILY|daily|TACIT|tacit|默契|答题/))
  else if (filter.value === 'check') arr = arr.filter(x => reasonMatch(x, /CHECK|check|LIST|list|BUCKET|bucket|MILESTONE|milestone|清单|里程碑/))
  return arr
})

function onEarn(t: EarnItem) {
  if (!t.path) return
  try {
    router.push(t.path)
  } catch (e: any) {}
}

async function load() {
  if (!auth.coupleId) return
  try {
    Object.assign(ov, await coinApi.overview(auth.coupleId))
    const r: any = await coinApi.logs(auth.coupleId, { size: 200 })
    logs.value = r?.list || r?.records || r || []
  } catch (e) {}
}

onMounted(load)
watch(filter, () => { /* computed 会自动更新 */ })

function safeNum(v: any): number {
  if (v === null || v === undefined || v === '') return 0
  const s = String(v).replace(/[\u0000-\u001F\u007F-\u009F\u2000-\u200F\u2028-\u202F\uFEFF]/g, '').replace(/[^\d.\-+]/g, '')
  const n = Number(s)
  return Number.isFinite(n) ? n : 0
}

function iconOf(reason: string, delta: number) {
  const r = String(reason || '').toUpperCase()
  if (delta > 0) {
    if (r.includes('WISH') || reason.includes('心愿') || reason.includes('🎁')) return '🎁'
    if (r.includes('QUIZ') || r.includes('TACIT') || r.includes('DAILY') || reason.includes('默契') || reason.includes('答题')) return '❓'
    if (r.includes('CHECK') || r.includes('LIST') || r.includes('BUCKET') || r.includes('MILESTONE') || reason.includes('清单') || reason.includes('里程碑')) return '✅'
    if (r.includes('MOOD') || r.includes('PUNCH') || reason.includes('心情') || reason.includes('打卡')) return '😊'
    if (r.includes('DIARY') || reason.includes('日记')) return '📖'
    if (r.includes('LETTER') || reason.includes('情')) return '💌'
    if (r.includes('ICE') || r.includes('BREAK') || r.includes('WHEEL') || reason.includes('破冰') || reason.includes('转盘')) return '🎯'
    if (reason.includes('🏆') || r.includes('MILESTONE')) return '🏆'
    return '💰'
  } else {
    if (r.includes('WISH') || reason.includes('心愿')) return '🎁'
    return '💸'
  }
}

const chartMax = computed(() => {
  const vals = (ov.last7 || []).map(x => safeNum(x.delta))
  const mx = Math.max(...vals, 1)
  return Math.ceil(mx / 5) * 5
})
const chartMaxX = 300
const linePoints = computed(() =>
  (ov.last7 || []).map((d, i) => {
    const x = (i / Math.max(1, (ov.last7?.length || 1) - 1)) * chartMaxX
    const y = 150 - ((safeNum(d.delta) / chartMax.value) * 130)
    return `${x},${y}`
  }).join(' ')
)
const pointArr = computed(() =>
  (ov.last7 || []).map((d, i) => ({
    x: (i / Math.max(1, (ov.last7?.length || 1) - 1)) * chartMaxX,
    y: 150 - ((safeNum(d.delta) / chartMax.value) * 130)
  }))
)

const pieTotal = computed(() => (ov.reasonsPie || []).reduce((s, p) => s + Math.abs(safeNum(p.total)), 0) || 1)
function piePct(v: number) { return Math.round(Math.abs(safeNum(v)) * 100 / pieTotal.value) }
const pieColors = ['#FF6B9D', '#FA8C16', '#722ED1', '#1890FF', '#52C41A', '#13C2C2']
function pieColor(i: number) { return pieColors[i % pieColors.length] }

function fmt(t?: string) {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getMonth()+1}/${d.getDate()} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
}
</script>

<style scoped>
.head-card {
  margin: 16px;
  padding: 24px;
  border-radius: 20px;
  background: linear-gradient(135deg, #FA8C16 0%, #FFC069 50%, #FFE4A8 100%);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 6px 20px rgba(250, 140, 22, 0.2);
}
.row-line {
  display: flex;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px dashed #FCE4EF;
}
.row-line:last-child { border-bottom: 0; }
.earn-row.clickable {
  cursor: pointer !important;
  touch-action: manipulation;
  transition: background 0.15s;
  border-radius: 10px;
  margin: 2px -6px;
  padding: 12px 6px;
}
.earn-row.clickable:active { background: #FFF6FB; }
.go-arrow {
  margin-left: 6px;
  color: #ccc;
  font-size: 26px;
  line-height: 1;
  font-weight: 300;
}
.filter-bar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.filter-chip {
  padding: 6px 14px;
  border-radius: 999px;
  background: #fff;
  border: 1px solid #F0D9E4;
  color: #8A5C73;
  font-size: 13px;
  cursor: pointer;
  touch-action: manipulation;
  white-space: nowrap;
}
.filter-chip.active {
  background: linear-gradient(135deg, #FF6B9D 0%, #FF3D7F 100%);
  color: #fff;
  border-color: transparent;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(255, 61, 127, 0.25);
}
</style>