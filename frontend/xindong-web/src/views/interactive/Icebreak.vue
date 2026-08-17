<template>
  <div class="page">
    <van-nav-bar title="破冰大转盘" left-arrow fixed placeholder @click-left="$router.back()">
      <template #right>
        <span class="coin-tag" style="margin-right:12px;" :title="`每日基础3次，完成任务每次+2，每日上限${maxLeft}次`">今日剩余 {{ left }}/{{ maxLeft }} 次</span>
      </template>
    </van-nav-bar>

    <div class="card" style="margin-top:20px;background:linear-gradient(135deg,#FFE0EC 0%,#FFF5F7 100%);">
      <div style="text-align:center;padding:16px 0;">
        <div style="font-weight:700;font-size:18px;">🎡 破冰任务大转盘</div>
        <div class="subtle" style="margin-top:4px;">转动命运，一起完成一次浪漫互动</div>
      </div>

      <div style="position:relative;margin:24px auto;width:280px;height:280px;">
        <div class="wheel" :style="{transform:`rotate(${rotate}deg)`}">
          <svg viewBox="0 0 200 200" width="100%" height="100%">
            <g v-for="(seg,i) in segments" :key="i">
              <path :d="pieSlice(100, 100, 95, i*40, (i+1)*40)" :fill="seg.color" />
              <text :transform="txtTrans(i*40, 62)" text-anchor="middle" style="font-size:12px;font-weight:800;fill:#fff;dominant-baseline:middle;letter-spacing:1px;user-select:none;text-shadow:0 1px 2px rgba(0,0,0,0.2);">
                {{ seg.label }}
              </text>
            </g>
            <circle cx="100" cy="100" r="18" fill="#fff" stroke="#FF6B9D" stroke-width="3.5"/>
            <text x="100" y="100" text-anchor="middle" dominant-baseline="middle" style="font-size:20px;pointer-events:none;">💖</text>
          </svg>
        </div>
        <div class="wheel-pointer"></div>
      </div>

      <div style="text-align:center;">
        <van-button
          :type="task ? 'warning' : 'primary'"
          :color="task ? '#FAAD14' : '#FF6B9D'"
          round
          size="large"
          :disabled="spinning || (!task && left <= 0)"
          :icon="task ? 'todo-list-o' : 'replay'"
          style="width:260px;"
          @click="task ? scrollToTask() : doSpin()">
          <template v-if="spinning">转动中...</template>
          <template v-else-if="task">📌 先完成当前任务 · {{ task.title }}</template>
          <template v-else-if="left > 0">🎰 抽一次破冰任务（剩 {{ left }}/{{ maxLeft }} 次）</template>
          <template v-else>今日次数用完 · 完成任务可回补 +2 次（上限{{maxLeft}}）</template>
        </van-button>
        <div v-if="task" class="subtle" style="margin-top:8px;font-size:12px;">
          完成后立即获得 <b>+2 次抽奖机会</b>（每日上限{{maxLeft}}次） + <b>+3 💰 内容金币</b>
        </div>
      </div>
    </div>

    <div id="task-card-anchor" v-if="task" class="card" style="background:linear-gradient(135deg,#D9F7E2 0%,#F5FFF7 100%);margin-top:16px;">
      <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:12px;">
        <div style="font-weight:700;font-size:16px;color:#237804;">📌 当前进行中的破冰任务</div>
        <span class="tag-pill" style="background:#FFF2CC;color:#B8860B;">完成 +2 次 + 3💰</span>
      </div>
      <div style="display:flex;align-items:flex-start;gap:16px;">
        <div style="width:60px;height:60px;border-radius:16px;background:#fff;display:flex;align-items:center;justify-content:center;font-size:36px;flex-shrink:0;">{{ task.emoji || '🎯' }}</div>
        <div style="flex:1;min-width:0;">
          <div style="font-weight:700;font-size:17px;">{{ task.title }}</div>
          <div style="color:#555;margin-top:6px;line-height:1.6;">{{ task.description }}</div>
          <div style="display:flex;gap:8px;margin-top:10px;flex-wrap:wrap;">
            <span class="tag-pill">难度 {{ '⭐'.repeat(Math.max(1, task.difficulty || 1)) }}</span>
            <span class="tag-pill" style="background:#FFF2CC;color:#B8860B;">+{{ task.bonusCoins || 3 }}💰</span>
            <span class="tag-pill" style="background:#E6F7FF;color:#1890FF;">约 {{ task.timeMin || 10 }} 分钟</span>
            <span v-if="task.category" class="tag-pill" style="background:#F9F0FF;color:#722ED1;">{{ task.category }}</span>
          </div>
        </div>
      </div>
      <van-field v-model="proofNote" label="完成感悟" placeholder="写下完成后的心情/证明（最多500字，选填）" type="textarea" rows="2" autosize maxlength="500" show-word-limit style="margin-top:16px;" />
      <div style="display:flex;gap:8px;margin-top:16px;">
        <van-button block plain color="#999" round icon="close" @click="skipTask">跳过 · 放弃奖励</van-button>
        <van-button block type="primary" color="#52C41A" round icon="passed" :loading="submitting" @click="submitDone">完成并领取奖励</van-button>
      </div>
    </div>

    <div class="card">
      <div class="card-title"><span>📜 最近完成记录</span></div>
      <div v-if="!history.length" class="subtle" style="text-align:center;padding:24px 0;">还没有完成记录，快转动转盘吧～</div>
      <div v-for="(h,i) in history" :key="i" class="row-line">
        <span style="font-size:22px;">{{ h.taskEmoji || '✅' }}</span>
        <div style="flex:1;margin:0 12px;min-width:0;">
          <div style="font-weight:600;">{{ h.taskTitle }}</div>
          <div v-if="h.note" class="subtle" style="margin-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">📝 {{ h.note }}</div>
          <div class="subtle">{{ fmt(h.createdAt) }}</div>
        </div>
        <div style="text-align:right;">
          <div v-if="h.status==='DONE'" class="green" style="font-weight:700;">+{{ h.bonusCoins }}💰</div>
          <div v-else class="subtle">{{ statusText(h.status) }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { showToast, showDialog } from 'vant'
import { icebreakApi, type SpinResult, type IcebreakTask, type IcebreakHistory } from '@/api'

const left = ref(3)
const maxLeft = ref(6)
const spinning = ref(false)
const submitting = ref(false)
const rotate = ref(0)
const task = ref<IcebreakTask | null>(null)
const sessionId = ref<string>('')
const proofNote = ref('')
const history = ref<any[]>([])

function safeNum(v: any): number {
  if (v === null || v === undefined || v === '') return 0
  const s = String(v).replace(/[\u0000-\u001F\u007F-\u009F\u2000-\u200F\u2028-\u202F\uFEFF]/g, '').replace(/[^\d.\-+]/g, '')
  const n = Number(s)
  return Number.isFinite(n) ? n : 0
}

const BAD_EMOJI = /[\u{1FA00}-\u{1FAFF}]/gu
const DIRTY_CHARS = /[\uFFFD\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F-\u009F\u2000-\u200F\u2028-\u202F\uFEFF\uFFF0-\uFFFF]/g
const SANITIZE_MAP: Record<string, string> = {
  '🪙': '💰', '🪄': '✨', '🪅': '🎊', '🪆': '🏆', '🪔': '🕯️', '🪒': '💈',
  '🪑': '🛋️', '🪓': '⚒️', '🪡': '🧵', '🪢': '🧶', '🪣': '🪣', '🪤': '🪤',
  '🪥': '🪥', '🪦': '🪦', '🪧': '🪧', '🪨': '🪨', '🪩': '🎉', '🪪': '🪪',
  '🪫': '🪫', '🪬': '🪬', '🪮': '🪮', '🪯': '🪯'
}
function sanitizeText(s: any): string {
  if (s === null || s === undefined) return ''
  let out = String(s)
  out = out.replace(/[\uD800-\uDBFF][\uDC00-\uDFFF]/g, (m) => {
    if (SANITIZE_MAP[m]) return SANITIZE_MAP[m]
    const cp = (m.charCodeAt(0) - 0xD800) * 0x400 + (m.charCodeAt(1) - 0xDC00) + 0x10000
    if (cp >= 0x1FA00 && cp <= 0x1FAFF) return '✨'
    return m
  })
  out = out.replace(/[\uD800-\uDBFF]|[\uDC00-\uDFFF]/g, '')
  out = out.replace(BAD_EMOJI, (ch) => SANITIZE_MAP[ch] || '✨')
  out = out.replace(DIRTY_CHARS, '')
  return out
}

function normalizeHistory(h: any): any {
  if (!h) return null
  const title = sanitizeText(
    h?.taskTitle ?? h?.taskName ?? h?.task_name ?? h?.title ?? h?.task?.title ?? h?.name ??
    h?.taskDesc ?? h?.description ?? h?.remark ?? ''
  ).trim()
  const emoji = sanitizeText(
    h?.taskEmoji ?? h?.task_emoji ?? h?.emoji ?? h?.task?.emoji ?? h?.icon ??
    (title.includes('约会') ? '💖' : title.includes('吻') ? '💋' : title.includes('旅行')||title.includes('散步')||title.includes('公园')||title.includes('出门') ? '🚶' :
     title.includes('拥抱')||title.includes('按摩')||title.includes('牵手') ? '🤗' : title.includes('电影')||title.includes('卡拉OK')||title.includes('游戏') ? '🎮' :
     title.includes('早餐')||title.includes('做饭')||title.includes('新菜')||title.includes('整理') ? '🍳' :
     title.includes('说')||title.includes('优点')||title.includes('信任') ? '💬' : title.includes('饭')||title.includes('吃') ? '🍜' :
     title.includes('礼物')||title.includes('惊喜')||title.includes('情书') ? '💌' : '✅')
  )
  const bonus = safeNum(h?.bonusCoins ?? h?.bonus ?? h?.coins ?? h?.reward ?? h?.award ?? 3)
  let status = String(h?.status ?? h?.state ?? '').toUpperCase()
  if (!status) {
    if (h?.doneAt || h?.done_at || h?.completedAt) status = 'DONE'
    else if (h?.skippedAt || h?.skip_reason || h?.skip) status = 'SKIPPED'
    else if (h?.finishedById != null || h?.reflection != null || h?.note != null) status = 'DONE'
    else status = 'SPUN'
  }
  const note = sanitizeText(h?.note ?? h?.remark ?? h?.content ?? h?.proof ?? h?.proofNote ?? h?.reflection ?? '').trim()
  const createdAt = h?.createdAt ?? h?.created_at ?? h?.date ?? h?.time ?? new Date().toISOString()
  return { ...h, taskTitle: title || '破冰任务', taskEmoji: emoji, status, bonusCoins: bonus, note, createdAt }
}

function normalizeTask(t: any): any {
  if (!t) return null
  const title = sanitizeText(t?.title ?? t?.taskTitle ?? t?.name ?? '').trim() || '破冰任务'
  const desc = sanitizeText(t?.description ?? t?.desc ?? t?.content ?? t?.intro ?? t?.detail ?? '')
  const emoji = sanitizeText(t?.emoji ?? t?.taskEmoji ?? t?.icon ?? (title.includes('散步') ? '🚶' : '🎯'))
  const category = sanitizeText(t?.category ?? t?.type ?? t?.seg ?? '')
  return {
    ...t,
    title,
    description: desc,
    emoji,
    category,
    difficulty: Math.max(1, safeNum(t?.difficulty ?? 1)),
    bonusCoins: safeNum(t?.bonusCoins ?? t?.bonus ?? t?.coins ?? t?.reward ?? 3),
    timeMin: safeNum(t?.timeMin ?? t?.minTime ?? t?.time ?? 10) || 10
  }
}

const segments = [
  { label: '浪漫', color: '#FF6B9D' }, { label: '日常', color: '#95DE64' },
  { label: '冒险', color: '#40A9FF' }, { label: '甜蜜', color: '#FF85C0' },
  { label: '游戏', color: '#B37FEB' }, { label: '旅行', color: '#FFC069' },
  { label: '感动', color: '#FF7875' }, { label: '挑战', color: '#36CFC9' },
  { label: '浪漫', color: '#FF6B9D' }
]

function pieSlice(cx: number, cy: number, r: number, startDeg: number, endDeg: number) {
  const a1 = startDeg * Math.PI / 180, a2 = endDeg * Math.PI / 180
  const x1 = cx + r * Math.sin(a1), y1 = cy - r * Math.cos(a1)
  const x2 = cx + r * Math.sin(a2), y2 = cy - r * Math.cos(a2)
  return `M ${cx} ${cy} L ${x1} ${y1} A ${r} ${r} 0 0 1 ${x2} ${y2} Z`
}
function txtTrans(deg: number, r: number) {
  const mid = deg + 20
  const a = mid * Math.PI / 180
  const tx = 100 + r * Math.sin(a)
  const ty = 100 - r * Math.cos(a)
  // 下半圆（mid > 180）翻转文字，避免倒置；上半圆正常放射
  const flip = mid > 180 ? 180 : 0
  return `translate(${tx}, ${ty}) rotate(${mid - 90 + flip})`
}

function scrollToTask() {
  const el = document.getElementById('task-card-anchor')
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

onMounted(async () => {
  try {
    const r = await icebreakApi.state()
    left.value = Math.min(maxLeft.value, safeNum(r.spinTodayLeft ?? r.spinsLeft ?? 3))
    sessionId.value = r.sessionId ?? ''
    if (r.task) {
      task.value = normalizeTask(r.task) as any
      await nextTick()
      setTimeout(scrollToTask, 300)
    }
  } catch (e: any) {
    console.warn('[破冰] /state 加载失败，使用默认3次兜底', e?.message)
  }
  try {
    const r: any = await icebreakApi.history({ size: 10 })
    const rawList: any[] = Array.isArray(r) ? r : (r?.list || r?.rows || r?.records || r?.data || [])
    history.value = rawList.map(normalizeHistory).filter(Boolean)
  } catch (e) {}
})

async function doSpin() {
  if (spinning.value || left.value <= 0 || task.value) return
  spinning.value = true
  // 🚩用户点下就立即扣1次，UI立刻显示5/6——避免等接口2秒期间用户觉得"没扣"
  const before = left.value
  left.value = Math.min(maxLeft.value, Math.max(0, left.value - 1))
  let spinRes: SpinResult | null = null
  try {
    spinRes = await icebreakApi.spin()
    if ((spinRes as any)?.maxDaily) maxLeft.value = safeNum(spinRes.maxDaily)
    sessionId.value = spinRes.sessionId
    task.value = normalizeTask(spinRes.task) as any
    // 后端返回的扣减值做最终校准（不允许比刚才UI扣的还多，防负数）
    const apiLeft = safeNum(spinRes.spinTodayLeft ?? spinRes.spinsLeft ?? (spinRes as any).spinsLeftAfter)
    if (!isNaN(apiLeft)) left.value = Math.min(maxLeft.value, Math.max(0, apiLeft))

    const seg = spinRes.segment ?? Math.floor(Math.random() * 9)
    const turns = 5 + Math.floor(Math.random() * 3)
    rotate.value = rotate.value + turns * 360 + seg * 40 + 20
  } catch (e: any) {
    // 🚩接口失败把刚才扣的1次加回来（回滚）
    left.value = Math.min(maxLeft.value, Math.max(0, before))
    spinning.value = false
    const code: string = e?.code ?? e?.response?.data?.code
    const data = e?.data ?? e?.response?.data?.data
    if ((data as any)?.maxDaily) maxLeft.value = safeNum(data.maxDaily)
    if (code === '21103' && data && data.task) {
      task.value = normalizeTask(data.task) as any
      sessionId.value = data.sessionId ?? sessionId.value
      const apiLeft = safeNum(data.spinTodayLeft ?? data.spinsLeft)
      if (!isNaN(apiLeft)) left.value = Math.min(maxLeft.value, Math.max(0, apiLeft))
      else left.value = Math.min(maxLeft.value, Math.max(0, before))
      showToast({ type: 'warn' as const, message: '已帮你恢复当前任务啦，在下方完成哦～' })
      await nextTick()
      setTimeout(scrollToTask, 300)
      return
    }
    if (code === '21102') {
      showToast({ type: 'fail', message: `今天基础次数用完啦，完成任务可回补+2次哦（每日上限${maxLeft.value}次）` })
      left.value = 0
      return
    }
    showToast({ type: 'fail', message: e?.message || '转动失败，请重试' })
    return
  }
  setTimeout(async () => {
    spinning.value = false
    await nextTick()
    setTimeout(scrollToTask, 200)
  }, 2200)
}

async function submitDone() {
  if (!task.value) return
  submitting.value = true
  try {
    const r = await icebreakApi.submit(task.value.id, { sessionId: sessionId.value || undefined, note: proofNote.value || undefined })
    showToast({ type: 'success', message: `🎉 完成！+${r.bonus || r.bonusCoins || 3}💰 · 抽奖次数 +2（每日上限${maxLeft.value}次）` })
    if (r.record) {
      history.value.unshift(normalizeHistory(r.record) as any)
    } else {
      history.value.unshift(normalizeHistory({
        id: Date.now(), coupleId: 0, taskId: task.value.id,
        taskTitle: task.value.title, taskEmoji: task.value.emoji,
        status: 'DONE', bonusCoins: r.bonus || 3, note: proofNote.value || undefined,
        createdAt: new Date().toISOString(), doneAt: new Date().toISOString()
      }) as any)
    }
    left.value = Math.min(maxLeft.value, safeNum(r.spinsLeft ?? Math.max(0, left.value + 2)))
    task.value = null
    proofNote.value = ''
  } catch (e: any) {
    showToast({ type: 'fail', message: e?.message || '提交失败' })
  } finally {
    submitting.value = false
  }
}

async function skipTask() {
  if (!task.value) return
  try {
    await showDialog({
      title: '确定跳过这次？',
      message: `跳过后不会获得抽奖次数 +2 回补（每日上限${maxLeft.value}次）和 3💰 奖励哦，要不再想想？`,
      confirmButtonText: '狠心跳过',
      cancelButtonText: '再挑战一下',
      showCancelButton: true,
      confirmButtonColor: '#999'
    })
  } catch (_) {
    return
  }
  history.value.unshift(normalizeHistory({
    id: Date.now(), coupleId: 0, taskId: task.value.id,
    taskTitle: task.value.title, taskEmoji: task.value.emoji,
    status: 'SKIPPED', createdAt: new Date().toISOString()
  }) as any)
  task.value = null
  showToast('已跳过，下次加油～')
}

function statusText(s: string) {
  return ({ SPUN: '进行中', DONE: '已完成', FAILED: '未完成', SKIPPED: '已跳过' } as any)[s] || s
}
function fmt(t?: string) {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getMonth()+1}/${d.getDate()} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
}
</script>

<style scoped>
.wheel {
  transition: transform 2.1s cubic-bezier(.17,.67,.2,1.01);
  filter: drop-shadow(0 8px 20px rgba(255,107,157,0.25));
}
.wheel-pointer {
  position: absolute;
  top: -10px; left: 50%;
  transform: translateX(-50%);
  width: 0; height: 0;
  border-left: 16px solid transparent;
  border-right: 16px solid transparent;
  border-top: 28px solid #FF3D7F;
  filter: drop-shadow(0 2px 4px rgba(0,0,0,0.15));
  z-index: 2;
}
</style>
