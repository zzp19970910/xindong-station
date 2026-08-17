<template>
  <div class="page">
    <van-nav-bar title="记录" fixed placeholder :border="false" />
    <van-tabs v-model:active="tab" sticky offset-top="46px" line-width="24px" color="#FF3D7F" @change="onTab">
      <van-tab title="心情" name="mood">
        <div class="mood-head card" style="margin-top:6px;">
          <van-calendar v-model:show="calShow" :color="'#FF6B9D'" @confirm="onPickDay" />
          <div style="display:flex;align-items:center;justify-content:space-between;">
            <div>
              <div style="font-weight:700;font-size:18px;">{{ ymLabel }}</div>
              <div class="subtle">共打卡 {{ moodMonth.length }} 次</div>
            </div>
            <van-button type="primary" color="#FF6B9D" round size="small" @click="checkin">今日打卡</van-button>
          </div>
          <div class="mood-grid" style="margin-top:16px;">
            <div v-for="w in weekLabels" :key="w" class="mg-head subtle">{{ w }}</div>
            <div v-for="(c, idx) in cells" :key="idx" class="mg-cell" :class="{ disabled: !c.date, today: c.isToday, selected: c.isPick }"
                 @click="c.date && pickCell(c)">
              <span v-if="c.date" class="mg-num">{{ c.dayNum }}</span>
              <span v-if="c.mood" class="mg-emoji">{{ c.mood }}</span>
            </div>
          </div>
        </div>
        <div class="card">
          <div class="card-title">
            <span>最近打卡</span>
            <span class="subtle">共 {{ moods.length }} 条</span>
          </div>
          <div v-if="!moods.length" class="subtle" style="text-align:center;padding:24px 0;">还没心情打卡，记录今天的感受吧～</div>
          <div v-for="m in moods" :key="m.id" class="row-line">
            <span style="font-size:30px;">{{ m.emoji }}</span>
            <div style="flex:1;margin:0 12px;">
              <div style="font-weight:600;">{{ m.dateStr }}</div>
              <div v-if="m.note" class="subtle" style="margin-top:2px;">{{ m.note }}</div>
            </div>
            <div style="text-align:right;">
              <div class="pink" style="font-weight:700;">{{ m.score }}分</div>
              <div class="subtle">P{{ m.partnerIdx || (m.userId===auth.userId?1:2) }}</div>
            </div>
          </div>
        </div>
      </van-tab>

      <van-tab title="纪念日" name="anniv">
        <div style="padding:12px 16px 4px;">
          <van-button type="primary" block color="#FF6B9D" round icon="plus" @click="editAnniv()">+ 添加纪念日</van-button>
        </div>
        <div class="card">
          <div v-if="!annivs.length" class="subtle" style="text-align:center;padding:24px 0;">暂无纪念日，快去添加属于你们的第一个吧💐</div>
          <div v-for="a in sortedAnnivs" :key="a.id" class="anniv-row" @click="editAnniv(a)">
            <div class="anniv-icon" :style="{background:colorOf(a.type)}">{{ a.emoji || '🎂' }}</div>
            <div style="flex:1;margin:0 12px;">
              <div style="font-weight:600;">{{ a.title }} <span v-if="a.isTop" class="tag-pill">置顶</span></div>
              <div class="subtle">{{ a.targetDate }} · {{ typeText(a.type) }}</div>
            </div>
            <div style="text-align:right;">
              <div class="pink" style="font-weight:700;font-size:18px;">{{ daysLeft(a.targetDate,a.displayMode) }}</div>
              <div class="subtle">{{ displayOf(a.displayMode) }}</div>
            </div>
          </div>
        </div>
      </van-tab>

      <van-tab title="日记" name="diary">
        <div style="padding:12px 16px 4px;">
          <van-button type="primary" block color="#FF6B9D" round icon="edit" @click="$router.push('/app/record/diary/edit')">+ 写日记</van-button>
        </div>
        <div v-if="!diaries.length" class="card"><div class="subtle" style="text-align:center;padding:24px 0;">还没有日记，去记录今天的心动瞬间✨</div></div>
        <div v-for="d in diaries" :key="d.id" class="card diary-card" @click="$router.push(`/app/record/diary/${d.id}`)">
          <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:8px;">
            <div style="display:flex;align-items:center;gap:8px;">
              <span style="font-size:20px;">{{ d.moodEmoji || '🌈' }}</span>
              <b>{{ d.title || '未命名' }}</b>
            </div>
            <span class="subtle">{{ fmtDate(d.createdAt) }}</span>
          </div>
          <div class="subtle" style="line-height:1.6;">{{ strip(d.content).slice(0, 80) }}{{ d.content.length > 80 ? '...' : '' }}</div>
          <div v-if="d.images?.length" style="display:grid;grid-template-columns:repeat(3,1fr);gap:6px;margin-top:8px;">
            <div v-for="(im,i) in d.images.slice(0,3)" :key="i" style="aspect-ratio:1;background:#FFE0EC;border-radius:8px;display:flex;align-items:center;justify-content:center;">🖼️</div>
          </div>
          <div style="display:flex;justify-content:space-between;margin-top:10px;color:#aaa;font-size:12px;">
            <span>💬 {{ d.commentsCount || 0 }} 评论</span>
            <span>来自 P{{ d.partnerIdx || 1 }}</span>
          </div>
        </div>
        <van-empty v-if="hasMore" description="加载中..." />
      </van-tab>

      <van-tab title="清单" name="check">
        <div style="padding:8px 16px 4px;">
          <van-radio-group v-model="catFilter" direction="horizontal" style="display:flex;gap:8px;flex-wrap:wrap;">
            <van-radio-button name="" style="flex-shrink:0;">全部</van-radio-button>
            <van-radio-button name="romantic" style="flex-shrink:0;">浪漫</van-radio-button>
            <van-radio-button name="daily" style="flex-shrink:0;">日常</van-radio-button>
            <van-radio-button name="travel" style="flex-shrink:0;">旅行</van-radio-button>
            <van-radio-button name="growth" style="flex-shrink:0;">成长</van-radio-button>
            <van-radio-button name="custom" style="flex-shrink:0;">自定义</van-radio-button>
          </van-radio-group>
        </div>
        <div style="padding:12px 16px 4px;display:flex;gap:8px;align-items:center;">
          <van-button type="primary" color="#FF6B9D" round size="small" icon="plus" @click="editCheck()">+ 自定义</van-button>
          <van-tag v-if="prog" round type="primary" color="#FFF2CC" text-color="#B8860B" size="medium">
            进度 {{ prog.done }}/{{ prog.total }} · {{ prog.progressPct }}%
          </van-tag>
        </div>
        <div class="card">
          <div v-for="c in listFiltered" :key="c.id" class="row-line" style="padding:12px 0;cursor:pointer;" @click.self="(e:any) => toggleByRow(c, e)">
            <van-checkbox
              :model-value="c.isDone"
              shape="square"
              :icon-size="22"
              :checked-color="'#FF6B9D'"
              @click.stop
              @update:model-value="(v:any) => onToggle(c, v)" />
            <div style="flex:1;margin:0 12px;" @click.stop="toggleByRow(c)">
              <div style="display:flex;align-items:center;gap:6px;">
                <span>{{ c.emoji || '💖' }}</span>
                <b :style="{textDecoration: c.isDone ? 'line-through' : 'none', color: c.isDone ? '#aaa' : '#222'}">
                  {{ c.title }}
                </b>
                <span v-if="c.milestoneBonus" class="tag-pill">阶段+{{ c.milestoneBonus }}💰</span>
              </div>
              <div v-if="c.description" class="subtle" style="margin-top:4px;">{{ c.description }}</div>
            </div>
            <van-icon v-if="!c.isPreset" name="delete-o" color="#FF4D4F" size="18" @click.stop="rmCheck(c)" />
          </div>
          <div v-if="!listFiltered.length" class="subtle" style="text-align:center;padding:24px 0;">暂无清单</div>
        </div>
      </van-tab>
    </van-tabs>

    <!-- 心情打卡浮层 -->
    <van-popup v-model:show="moodPop" round position="bottom" :style="{ height: '60%' }">
      <div style="padding:24px;">
        <h3 style="margin:0 0 8px;">今日心情</h3>
        <div class="subtle" style="margin-bottom:20px;">{{ pickDateLabel }}</div>
        <div style="display:flex;gap:8px;justify-content:space-around;flex-wrap:wrap;">
          <div v-for="e in emos" :key="e.k" @click="pEmoji=e.k; pScore=e.s"
               :style="{background:pEmoji===e.k?'#FFF0F5':'transparent',borderRadius:'50%'}"
               style="width:52px;height:52px;display:flex;align-items:center;justify-content:center;font-size:32px;">
            {{ e.k }}
          </div>
        </div>
        <van-slider v-model="pScore" :min="1" :max="10" style="margin:24px 8px;" bar-height="4" />
        <van-field v-model="pNote" rows="2" autosize type="textarea" placeholder="写点什么？" />
        <van-button block type="primary" color="#FF6B9D" round style="margin-top:20px;" @click="submitMood">
          提交 +5💰
        </van-button>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { useAuthStore } from '@/stores/auth.store'
import {
  moodApi, anniversaryApi, diaryApi, checklistApi,
  type MoodItem, type AnniversaryItem, type DiaryItem, type ChecklistItem
} from '@/api'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const tab = ref<'mood' | 'anniv' | 'diary' | 'check'>('mood')
const onTab = () => {
  if (tab.value === 'mood') loadMoods()
  else if (tab.value === 'anniv') loadAnnivs()
  else if (tab.value === 'diary') loadDiaries()
  else if (tab.value === 'check') loadCheck()
}

// ------------------ 心情 ------------------
const calShow = ref(false)
const ym = reactive({ y: new Date().getFullYear(), m: new Date().getMonth() + 1 })
const moods = ref<MoodItem[]>([])
const moodMonth = ref<MoodItem[]>([])
const weekLabels = ['一', '二', '三', '四', '五', '六', '日']
const emos = [
  { k: '😍', s: 10 }, { k: '🥰', s: 9 }, { k: '😊', s: 8 }, { k: '🙂', s: 7 },
  { k: '😐', s: 5 }, { k: '😟', s: 4 }, { k: '😢', s: 3 }, { k: '😠', s: 2 }
]
const moodPop = ref(false)
const pEmoji = ref('😊')
const pScore = ref(8)
const pNote = ref('')
const pickDate = ref<string>(todayStr())

const ymLabel = computed(() => `${ym.y} 年 ${ym.m} 月`)

interface Cell {
  date?: string; dayNum?: number; isToday?: boolean; isPick?: boolean; mood?: string
}
const cells = computed<Cell[]>(() => {
  const first = new Date(ym.y, ym.m - 1, 1)
  const start = ((first.getDay() + 6) % 7)
  const days = new Date(ym.y, ym.m, 0).getDate()
  const today = todayStr()
  const map: Record<string, string> = {}
  for (const mm of moodMonth.value) {
    map[mm.dateStr] = mm.emoji
  }
  const arr: Cell[] = []
  for (let i = 0; i < start; i++) arr.push({})
  for (let d = 1; d <= days; d++) {
    const iso = `${ym.y}-${String(ym.m).padStart(2, '0')}-${String(d).padStart(2, '0')}`
    arr.push({
      date: iso,
      dayNum: d,
      isToday: iso === today,
      isPick: iso === pickDate.value,
      mood: map[iso]
    })
  }
  return arr
})

function pickCell(c: Cell) {
  if (!c.date) return
  pickDate.value = c.date
  checkin()
}
function onPickDay(d: Date[]) {
  ym.y = d[0].getFullYear(); ym.m = d[0].getMonth() + 1
  calShow.value = false
}

const pickDateLabel = computed(() => pickDate.value)

// ------------------ 心情 通用兜底（避免前后端字段错位导致空显示） ------------------
const MOOD_EMOJI_BY_TYPE: Record<number, string> = {
  1: '😊', 2: '🥰', 3: '😐', 4: '😢', 5: '😠', 6: '💔'
}
const MOOD_SCORE_BY_TYPE: Record<number, number> = {
  1: 10, 2: 8, 3: 6, 4: 4, 5: 2, 6: 1
}
function normalizeMood(raw: any): MoodItem {
  const m: any = raw || {}
  const mt = Number(m.moodType || m.type || 0)
  const dateStr = String(m.dateStr || m.date || '')
  const score = Number.isFinite(+m.score) ? +m.score
    : (mt ? MOOD_SCORE_BY_TYPE[mt] || 8 : 8)
  const emoji = String(m.emoji || '') || (mt ? MOOD_EMOJI_BY_TYPE[mt] : '') || '😊'
  let partnerIdx = Number(m.partnerIdx) || 0
  const userId = Number(m.userId) || 0
  if (!partnerIdx) {
    // 如果只有date没partnerIdx/partner1字段 → 天对象。按userId=auth.userId推断=P1
    partnerIdx = (userId && userId === auth.userId) ? 1 : 2
  }
  return {
    id: Number(m.id) || Math.random(),
    coupleId: Number(m.coupleId) || auth.coupleId || 0,
    userId,
    partnerIdx,
    dateStr,
    emoji,
    score,
    note: String(m.note || ''),
    createdAt: String(m.createdAt || '')
  } as MoodItem
}
function flattenMoodsResp(r: any): MoodItem[] {
  if (!r) return []
  if (Array.isArray(r)) {
    // 两种情况：扁平数组 / 按天分组数组（有partner1/partner2字段）
    const first = r.find((x: any) => x)
    if (first && ('partner1' in first || 'partner2' in first)) {
      const out: MoodItem[] = []
      for (const day of r) {
        if (day?.partner1) out.push(normalizeMood(day.partner1))
        if (day?.partner2) out.push(normalizeMood(day.partner2))
      }
      return out
    }
    return r.map(normalizeMood)
  }
  if (Array.isArray(r.items)) return flattenMoodsResp(r.items)
  if (Array.isArray(r.list)) return flattenMoodsResp(r.list)
  if (Array.isArray(r.groups)) return flattenMoodsResp(r.groups)
  if (Array.isArray(r.data)) return flattenMoodsResp(r.data)
  return []
}

async function loadMoods() {
  if (!auth.coupleId) return
  try {
    const r: any = await moodApi.list(auth.coupleId)
    moods.value = flattenMoodsResp(r)
    const ymPrefix = `${ym.y}-${String(ym.m).padStart(2, '0')}`
    moodMonth.value = moods.value.filter(m => String(m.dateStr || '').startsWith(ymPrefix))
  } catch (e: any) {
    showToast(e?.message || '加载心情失败')
  }
}
function checkin() { moodPop.value = true }
async function submitMood() {
  try {
    await moodApi.checkin({ emoji: pEmoji.value, score: pScore.value, note: pNote.value })
    showToast({ type: 'success', message: '打卡成功 +5💰' })
    moodPop.value = false
    loadMoods()
  } catch (e: any) {
    showToast(e?.message || '打卡失败，请稍后重试')
  }
}

// ------------------ 纪念日 ------------------
const annivs = ref<AnniversaryItem[]>([])
const sortedAnnivs = computed(() => {
  const now = new Date()
  return [...annivs.value].sort((a, b) => {
    if (!!b.isTop !== !!a.isTop) return (b.isTop ? 1 : 0) - (a.isTop ? 1 : 0)
    return Math.abs(dayDiff(a.targetDate, a.displayMode)) - Math.abs(dayDiff(b.targetDate, b.displayMode))
  })
})

function colorOf(t: string) {
  return ({ love: '#FFD6E7', travel: '#CDEBFF', birthday: '#FFE6B3', anniversary: '#FFCCE5', other: '#E8F0FF' } as any)[t] || '#F5F5F5'
}
function typeText(t: string) {
  return ({ love: '恋爱', travel: '旅行', birthday: '生日', anniversary: '周年', other: '其他' } as any)[t] || t
}
function displayOf(m?: string) { return m === 'countup' ? '已过' : '倒计时' }

function dayDiff(date: string, mode?: string): number {
  const target = new Date(date)
  const y = new Date().getFullYear()
  if (mode !== 'countup') {
    // try this year's annual date
    try {
      const md = new Date(y, target.getMonth(), target.getDate())
      if (md.getTime() < Date.now()) md.setFullYear(y + 1)
      return Math.ceil((md.getTime() - Date.now()) / 86400000)
    } catch (e) {}
  }
  return Math.ceil((target.getTime() - Date.now()) / 86400000)
}
function daysLeft(date: string, mode?: string): string {
  const d = dayDiff(date, mode)
  if (d === 0) return '今天'
  return d > 0 ? `D-${d}` : `D+${-d}`
}
function editAnniv(a?: AnniversaryItem) {
  try {
    sessionStorage.setItem('anniv.edit', JSON.stringify(a || {}))
    router.push('/app/record/anniv/edit')
  } catch (e: any) {
    showToast(e?.message || '打开失败')
  }
}
function flattenAnnivResp(r: any): AnniversaryItem[] {
  if (!r) return []
  if (Array.isArray(r)) return r
  if (Array.isArray(r.items)) return flattenAnnivResp(r.items)
  if (Array.isArray(r.list)) return flattenAnnivResp(r.list)
  if (Array.isArray(r.data)) return flattenAnnivResp(r.data)
  return []
}
async function loadAnnivs() {
  try {
    const r: any = await anniversaryApi.list()
    annivs.value = flattenAnnivResp(r)
  } catch (e: any) {
    showToast(e?.message || '加载纪念日失败')
  }
}

// ------------------ 日记 ------------------
const diaries = ref<DiaryItem[]>([])
const diaryPage = ref(1)
const hasMore = ref(false)
async function loadDiaries(reset = true) {
  if (reset) { diaryPage.value = 1; diaries.value = [] }
  try {
    const r: any = await diaryApi.list({ page: diaryPage.value, size: 20 })
    diaries.value.push(...((r?.list || r?.items || r?.data || (Array.isArray(r) ? r : [])) as any[]))
    const total = r?.total || diaries.value.length
    hasMore.value = diaries.value.length < total
  } catch (e: any) {
    showToast(e?.message || '加载日记失败')
  }
}
function openDiary(d: DiaryItem) {
  try {
    sessionStorage.setItem('diary.detail', JSON.stringify(d))
    router.push('/app/record/diary/detail')
  } catch (e: any) {
    showToast(e?.message || '打开失败')
  }
}
function addDiary() {
  try {
    sessionStorage.removeItem('diary.edit')
    router.push('/app/record/diary/edit')
  } catch (e: any) {
    showToast(e?.message || '打开失败')
  }
}

// ------------------ 清单 ------------------
const checks = ref<ChecklistItem[]>([])
const catFilter = ref('')

watch(catFilter, () => {
  loadCheck()
})

const prog = computed(() => {
  const t = checks.value.length, d = checks.value.filter(c => !!c.isDone).length
  return { total: t, done: d, progressPct: t === 0 ? 0 : Math.round(d * 100 / t) }
})
const listFiltered = computed(() => {
  if (!catFilter.value) return checks.value
  if (catFilter.value === 'custom') return checks.value.filter(c => !c.isPreset)
  return checks.value.filter(c => (c.category || '') === catFilter.value)
})
function flattenChecklistResp(r: any): ChecklistItem[] {
  let arr: any[] = []
  if (Array.isArray(r)) arr = r
  else if (r) arr = r.list || r.items || r.data || []
  return arr.map((c: any) => ({
    ...c,
    isDone: !!c?.isDone || !!c?.done || !!c?.checked,
    isPreset: typeof c?.isPreset === 'boolean' ? c.isPreset : !!c?.isPresetTemplate,
    emoji: String(c?.emoji || c?.icon || '💖'),
    title: String(c?.title || c?.name || ''),
    description: String(c?.description || c?.desc || ''),
    milestoneBonus: Number(c?.milestoneBonus || c?.bonus) || undefined,
    category: String(c?.category || 'other')
  })) as ChecklistItem[]
}
async function loadCheck() {
  try {
    const params: any = {}
    if (catFilter.value && catFilter.value !== 'custom') params.category = catFilter.value
    const onlyDone = (catFilter.value === 'done')
    if (onlyDone) params.onlyDone = true
    const r: any = await checklistApi.list(params)
    checks.value = flattenChecklistResp(r)
    if (catFilter.value === 'custom') {
      checks.value = checks.value.filter(c => !c.isPreset)
    }
  } catch (e: any) {
    showToast(e?.message || '加载清单失败')
  }
}
function findCheckById(id: number): ChecklistItem | undefined {
  for (let i = 0; i < checks.value.length; i++) {
    if (checks.value[i].id === id) return checks.value[i]
  }
  return undefined
}
function toggleByRow(c: ChecklistItem, _e?: any) {
  const src = findCheckById(c.id)
  if (!src) return
  onToggle(src, !src.isDone)
}
async function onToggle(c: ChecklistItem, evt?: any) {
  // 必须操作源对象（checks.value里的，不是computed返回的副本）
  const src = findCheckById(c.id)
  if (!src) return
  let next = !!src.isDone
  if (typeof evt === 'boolean') next = evt
  else if (evt && typeof evt.detail === 'boolean') next = !!evt.detail
  else if (evt && (evt.target || {}).checked !== undefined) next = !!evt.target.checked
  else next = !next
  try {
    const r = await checklistApi.toggle(c.id, next) as any
    const item = (r?.item || r || {}) as any
    const nowDone = !!item.isDone || !!item.done || !!item.checked || next
    // 用源对象赋值，不是computed出来的
    src.isDone = nowDone
    const bonus = Number(r?.bonus || r?.milestone) || 0
    if (r?.milestone) showToast({ type: 'success', message: `🎉 里程碑达成！+${bonus}💰` })
    else if (bonus > 0) showToast({ type: 'success', message: `完成！+${bonus}💰` })
    else if (nowDone) showToast({ type: 'success', message: '已完成' })
  } catch (e: any) {
    showToast(e?.message || '操作失败')
  }
}
function editCheck() {
  try { router.push('/app/record/checklist/edit') }
  catch (e: any) { showToast(e?.message || '打开失败') }
}
async function rmCheck(c: ChecklistItem) {
  try {
    await showConfirmDialog({ title: '删除', message: '要删除这条自定义清单吗？' })
    await checklistApi.remove(c.id)
    checks.value = checks.value.filter(x => x.id !== c.id)
  } catch (e: any) {
    if (e !== 'cancel') showToast(e?.message || '删除失败')
  }
}

// ------------------ 工具 ------------------
function todayStr() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
function fmtDate(t: string) {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getMonth() + 1}月${d.getDate()}日 ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
function strip(s: string) { return (s || '').replace(/<[^>]+>/g, '').replace(/\s+/g, ' ').trim() }

onMounted(() => {
  const want = String(route.query.tab || '')
  if (['mood', 'anniv', 'diary', 'check'].includes(want)) {
    tab.value = want as any
  }
  onTab()
})
watch([() => ym.y, () => ym.m], loadMoods)
</script>
<style scoped>
.mood-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 2px;
}
.mg-head {
  text-align: center;
  font-size: 12px;
  padding: 8px 0;
}
.mg-cell {
  aspect-ratio: 1/1.1;
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  cursor: pointer;
}
.mg-cell.today { border: 1.5px solid #FF6B9D; }
.mg-cell.selected { background: #FFF0F5; }
.mg-cell.disabled { opacity: 0; }
.mg-num { font-size: 12px; color: #666; }
.mg-emoji { font-size: 18px; }

.anniv-row {
  display: flex;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px dashed #FCE4EF;
}
.anniv-row:last-child { border-bottom: 0; }
.anniv-icon {
  width: 48px; height: 48px;
  border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  font-size: 24px;
}
.row-line {
  display: flex;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px dashed #FCE4EF;
}
.row-line:last-child { border-bottom: 0; }

.diary-card { transition: all .2s; }
.diary-card:active { transform: scale(0.99); }
</style>