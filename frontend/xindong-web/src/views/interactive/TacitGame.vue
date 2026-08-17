<template>
  <div class="page">
    <van-nav-bar title="默契小游戏" left-arrow fixed placeholder @click-left="$router.back()" />

    <!-- 🟡 Phase 1: 首页 -->
    <div v-if="phase === 'home'" class="card" style="margin-top:16px;">
      <div style="text-align:center;padding:20px 12px;">
        <div style="font-size:80px;">🧩</div>
        <div style="font-size:20px;font-weight:800;margin-top:8px;">你到底有多懂TA？</div>
        <div class="subtle" style="margin-top:8px;line-height:1.6;">
          双方各自回答 8 道关于自己的问题，<br/>
          同时猜测对方会怎么选。<br/>
          答对预测越多 → 默契度越高！
        </div>
        <div style="margin-top:24px;">
          <van-button type="primary" color="#FF6B9D" round size="large" style="width:240px;"
                      :loading="starting" @click="startGame">
            开始一轮默契 PK
          </van-button>
        </div>
      </div>

      <div v-if="history.length" style="margin-top:20px;border-top:1px dashed #FCE4EF;padding-top:16px;">
        <div style="font-weight:700;margin-bottom:10px;">📊 历史战绩</div>
        <div v-for="(h,i) in history.slice(0,5)" :key="i" class="row-line clickable" @click="openHistory(h)">
          <span style="font-size:22px;">{{ (h.score ?? h.matchPercent ?? 0)>=80?'💯':(h.score ?? h.matchPercent ?? 0)>=60?'❤️❤️':'❤️' }}</span>
          <div style="flex:1;margin:0 12px;">
            <div style="font-weight:600;">默契度 {{ h.score ?? h.matchPercent ?? 0 }}%</div>
            <div class="subtle">{{ fmt(h.createdAt) }} · {{ h.done || h.status==='FINISHED' ? '已结束' : '进行中' }}</div>
          </div>
          <span class="tag-pill">{{ h.done || h.status==='FINISHED' ? '已结束' : '进行中' }}</span>
        </div>
      </div>
    </div>

    <!-- 🟢 Phase 2: 答题中(P1 or P2) -->
    <div v-else-if="phase === 'answering'" class="card" style="margin-top:16px;">
      <div style="display:flex;align-items:center;justify-content:space-between;">
        <div style="font-weight:700;">第 {{ qIdx+1 }}/{{ totalQs }} 题</div>
        <span class="tag-pill" :style="{ background: playerIdx===1 ? '#FFF0F5' : '#E6F7FF', color: playerIdx===1 ? '#FF3D7F' : '#1890FF' }">
          {{ playerText }}
        </span>
      </div>

      <div style="margin:20px 0;">
        <div style="font-size:18px;font-weight:600;line-height:1.6;">{{ curQ?.question }}</div>

        <div style="margin-top:16px;">
          <div class="subtle" style="margin-bottom:8px;">⭕ 你自己的答案：</div>
          <div v-for="op in curQ?.options" :key="op.optionId"
               @click="setMy(curQ.questionId, op.optionId)"
               class="tacit-op"
               :class="{ 'tacit-op-me': isMy(curQ.questionId, op.optionId) }">
            {{ op.label }}
          </div>
        </div>

        <div style="margin-top:16px;">
          <div class="subtle" style="margin-bottom:8px;">🔮 你预测 TA 会选：</div>
          <div v-for="op in curQ?.options" :key="op.optionId"
               @click="setPred(curQ.questionId, op.optionId)"
               class="tacit-op"
               :class="{ 'tacit-op-ta': isPred(curQ.questionId, op.optionId) }">
            {{ op.label }}
          </div>
        </div>
      </div>

      <div style="display:flex;gap:10px;">
        <van-button block plain round color="#FF6B9D" :disabled="qIdx===0" @click="qIdx--">上一题</van-button>
        <van-button v-if="qIdx < totalQs - 1" block type="primary" round color="#FF6B9D"
                    :disabled="!isQDone(curQ?.questionId)" @click="qIdx++">下一题</van-button>
        <van-button v-else block type="primary" round color="#FF6B9D"
                    :loading="submitting" :disabled="!allDone" @click="submitRound">
          提交本轮 8 题
        </van-button>
      </div>
      <div class="subtle" style="margin-top:12px;text-align:center;">
        还剩 {{ remainQ }} 题没答完哦（每题要选"自己答案"+"猜TA答案"）
      </div>
    </div>

    <!-- ⏳ Phase 3: 等待对方作答 -->
    <div v-else-if="phase === 'waiting'" class="card" style="margin-top:16px;">
      <div style="text-align:center;padding:32px 12px;">
        <van-loading color="#FF6B9D" size="40px" vertical>
          <span style="margin-top:12px;">等待 TA 答题中...</span>
        </van-loading>
        <div class="subtle" style="margin-top:16px;line-height:1.7;">
          你已经提交了本轮答案 💖<br/>
          等 TA 完成后默契度就会揭晓！<br/>
          页面会每 10 秒自动刷新
        </div>
        <div style="margin-top:20px;display:flex;gap:10px;">
          <van-button block plain round color="#FF6B9D" @click="$router.back()">返回</van-button>
          <van-button block type="primary" round color="#FF6B9D" @click="refreshGame">手动刷新</van-button>
        </div>
      </div>
    </div>

    <!-- 🎯 Phase 4: 结果页 -->
    <div v-else-if="phase === 'finished' && cur" class="card" style="margin-top:16px;background:linear-gradient(135deg,#FFE0EC 0%,#FFF5F7 100%);">
      <div style="text-align:center;padding:20px;">
        <div style="font-size:70px;">{{ scoreGrade }}</div>
        <div style="font-size:36px;font-weight:800;color:#FF3D7F;margin:12px 0;">{{ score }}%</div>
        <div style="font-size:16px;">{{ scoreComment }}</div>
      </div>

      <div class="card" style="background:#fff;margin:0;margin-top:16px;">
        <div style="font-weight:700;margin-bottom:12px;">📋 答题复盘</div>
        <div v-for="(q,i) in cur.questions" :key="q.questionId" style="margin-bottom:20px;">
          <div style="font-weight:600;margin-bottom:8px;">Q{{ i+1 }}. {{ q.question }}</div>
          <div v-for="op in q.options" :key="op.optionId"
               :style="{background:opBg(q,op),borderLeft:`3px solid ${opColor(q,op)}`}"
               style="padding:8px 12px;border-radius:8px;margin:4px 0;">
            {{ op.label }}
            <span v-if="q.myOptionId===op.optionId" class="tag-pill" style="background:#FFF0F5;">我答</span>
            <span v-if="q.partnerActualOptionId===op.optionId" class="tag-pill" style="background:#E6F7FF;color:#1890FF;">TA答</span>
            <span v-if="q.myGuessPartnerOptionId===op.optionId" class="tag-pill" style="background:#FFFBF0;color:#B8860B;">我猜TA</span>
            <span v-if="q.partnerGuessMyOptionId===op.optionId" class="tag-pill" style="background:#F5FFF0;color:#52C41A;">TA猜我</span>
          </div>
          <div class="subtle" style="margin-top:4px;">
            <span v-if="q.iGuessHit && q.partnerGuessHit" style="color:#52C41A;font-weight:600;">✅ 双方都猜中！满分默契</span>
            <span v-else-if="q.iGuessHit || q.partnerGuessHit" style="color:#FAAD14;font-weight:600;">
              ⭕ {{ q.iGuessHit ? '你猜中TA的了' : 'TA猜中你的了' }}
            </span>
            <span v-else style="color:#999;">❌ 这题双方都猜错啦~</span>
          </div>
        </div>
      </div>

      <div style="display:flex;gap:10px;margin-top:16px;">
        <van-button block plain round color="#FF6B9D" @click="$router.back()">返回</van-button>
        <van-button block type="primary" round color="#FF6B9D" @click="backHome">再来一局</van-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted, onBeforeUnmount } from 'vue'
import { showToast, showConfirmDialog } from 'vant'
import { tacitApi, type TacitGame, type TacitQuestion, type TacitQOption } from '@/api'

type Phase = 'home' | 'answering' | 'waiting' | 'finished'

const phase = ref<Phase>('home')
const cur = ref<TacitGame | null>(null)
const curQuestions = ref<TacitQuestion[]>([])
const qIdx = ref(0)
const starting = ref(false)
const submitting = ref(false)
const history = ref<TacitGame[]>([])

// 本地答案收集（仅answering阶段使用） key=questionId
const myAnswers = reactive<Record<number, number>>({})
const predAnswers = reactive<Record<number, number>>({})
const playerIdx = ref<1 | 2>(1)
let pollTimer: any = null

const totalQs = computed(() => curQuestions.value.length)
const playerText = computed(() =>
  cur.value?.done ? '' : `玩家${playerIdx.value} · ${playerIdx.value === 1 ? '发起方' : '应战方'}回合`
)
const curQ = computed(() => curQuestions.value[qIdx.value])

const score = computed(() => cur.value?.matchPercent ?? cur.value?.score ?? 0)
const scoreGrade = computed(() => {
  const s = score.value
  if (s >= 90) return '💯'
  if (s >= 75) return '❤️❤️❤️❤️'
  if (s >= 50) return '❤️❤️❤️'
  if (s >= 30) return '❤️❤️'
  return '🤝'
})
const scoreComment = computed(() => {
  const s = score.value
  if (s >= 90) return '天呐！你们是双胞胎吗？默契爆表 🔥'
  if (s >= 70) return '非常默契！你们彼此都很了解 ✨'
  if (s >= 50) return '还不错，多聊聊会更懂哦 💬'
  if (s >= 30) return '还需要多相处多沟通呢 🌸'
  return '看来你们还有很多可以探索的空间！'
})

const remainQ = computed(() => {
  let left = 0
  for (const q of curQuestions.value) {
    if (!myAnswers[q.questionId] || !predAnswers[q.questionId]) left++
  }
  return left
})
const allDone = computed(() => remainQ.value === 0 && totalQs.value > 0)

onMounted(async () => {
  try {
    const r: any = await tacitApi.history({ size: 5 })
    history.value = r.list || []
  } catch (_) {}
  startPoll()
})
onBeforeUnmount(() => stopPoll())

function startPoll() {
  stopPoll()
  pollTimer = setInterval(() => {
    if (phase.value === 'waiting' && cur.value?.gameId) refreshGame(true)
  }, 10000)
}
function stopPoll() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

function isMy(qid: number, oid: number) { return myAnswers[qid] === oid }
function isPred(qid: number, oid: number) { return predAnswers[qid] === oid }
function setMy(qid: number, oid: number) { myAnswers[qid] = oid }
function setPred(qid: number, oid: number) { predAnswers[qid] = oid }
function isQDone(qid?: number) {
  if (!qid) return false
  return !!myAnswers[qid] && !!predAnswers[qid]
}

function resetLocal() {
  for (const k of Object.keys(myAnswers)) delete myAnswers[Number(k)]
  for (const k of Object.keys(predAnswers)) delete predAnswers[Number(k)]
}

async function startGame() {
  starting.value = true
  try {
    // 从样例题拿题（后端 /tacit/questions 有返回样例3题）；不够就加本地兜底到8题
    const seeds = await tacitApi.seedQuestions()
    const fallback = [
      { q: '对方最喜欢的颜色是？', opts: ['红色', '蓝色', '绿色', '黄色', '紫色', '粉色', '黑白', '金色'] },
      { q: '对方周末理想的放松方式？', opts: ['宅家追剧', '出门逛街', '户外散步', '看电影', '打游戏', '看书', '吃顿好的', '睡一天'] },
      { q: '对方最想去的旅行地？', opts: ['海边', '山区', '大城市', '古镇', '国外', '小众秘境', '主题乐园', '温泉乡'] },
      { q: '对方遇到压力通常会？', opts: ['找人倾诉', '独自消化', '运动发泄', '吃顿好的', '熬夜刷手机', '哭', '睡觉', '逛街花钱'] },
      { q: '对方最看重伴侣的哪个特质？', opts: ['善良', '外貌', '上进心', '幽默', '陪伴', '金钱', '才华', '诚实'] },
      { q: '对方吃饭的口味偏好？', opts: ['重辣', '清淡', '酸甜', '无肉不欢', '素食', '日料海鲜', '奶茶甜品', '都可以'] },
      { q: '对方小时候的梦想职业？', opts: ['医生老师', '科学家', '明星', '画家', '程序员', '开店当老板', '环游世界', '飞行员'] },
      { q: '对方吵架后的性格？', opts: ['马上沟通', '先冷静', '需要对方哄', '冷战到底', '哭', '写小作文', '假装没事', '出去走走就好'] }
    ]
    let qs: TacitQuestion[] = [...seeds]
    for (let i = 0; qs.length < 8 && i < fallback.length; i++) {
      qs.push({
        questionId: 10000 + i,
        question: fallback[i].q,
        options: fallback[i].opts.map((label, j) => ({ optionId: j + 1, label }))
      })
    }
    qs = qs.slice(0, 8)
    if (qs.length < 8) throw new Error('题库不足8题')

    curQuestions.value = qs
    qIdx.value = 0
    resetLocal()
    playerIdx.value = 1
    phase.value = 'answering'
  } catch (e: any) {
    showToast(e?.message || '加载题目失败')
  } finally {
    starting.value = false
  }
}

async function submitRound() {
  if (!allDone.value) return
  submitting.value = true
  try {
    const myStr: Record<string, number> = {}
    const gpStr: Record<string, number> = {}
    for (const q of curQuestions.value) {
      myStr[String(q.questionId)] = myAnswers[q.questionId]
      gpStr[String(q.questionId)] = predAnswers[q.questionId]
    }
    let g: TacitGame
    if (playerIdx.value === 1) {
      g = await tacitApi.start({ myAnswers: myStr, guessPartnerAnswers: gpStr })
    } else {
      if (!cur.value?.gameId) throw new Error('gameId missing')
      g = await tacitApi.answer(cur.value.gameId, { myAnswers: myStr, guessPartnerAnswers: gpStr })
    }
    cur.value = g
    if (g.done) {
      phase.value = 'finished'
    } else {
      phase.value = 'waiting'
    }
  } catch (e: any) {
    showToast(e?.message || '提交失败，请检查网络')
  } finally {
    submitting.value = false
  }
}

async function refreshGame(silent = false) {
  if (!cur.value?.gameId) return
  try {
    const g = await tacitApi.get(cur.value.gameId)
    cur.value = g
    if (g.done) {
      phase.value = 'finished'
    } else {
      // 判断我是P2 而且 P2还没答 → 自动进入答题
      if (g.p2PartnerIdx != null) {
        phase.value = 'waiting'
      }
    }
  } catch (e: any) {
    if (!silent) showToast(e?.message || '刷新失败')
  }
}

function openHistory(h: TacitGame) {
  if (h.gameId == null) return
  showConfirmDialog({
    title: h.done ? '查看历史对局' : '继续进行中的对局',
    message: h.done
      ? `默契度 ${h.matchPercent ?? 0}% · ${fmt(h.createdAt)}`
      : `本局还在进行中，点击继续回到对局`,
    confirmButtonText: h.done ? '查看详情' : '继续对局'
  }).then(async () => {
    try {
      const g = await tacitApi.get(h.gameId)
      cur.value = g
      if (g.done) {
        phase.value = 'finished'
      } else if (g.questions?.length) {
        curQuestions.value = g.questions
        qIdx.value = 0
        resetLocal()
        // P2回答自己的8题
        playerIdx.value = 2
        phase.value = 'answering'
      } else {
        phase.value = 'waiting'
      }
    } catch (e: any) {
      showToast(e?.message || '对局不存在')
    }
  }).catch(() => {})
}

function backHome() {
  cur.value = null
  curQuestions.value = []
  qIdx.value = 0
  resetLocal()
  phase.value = 'home'
  // 刷新历史战绩
  tacitApi.history({ size: 5 }).then(r => { history.value = r.list || [] }).catch(() => {})
}

function opBg(q: TacitQuestion, op: TacitQOption) {
  const hit = (q.myGuessPartnerOptionId === op.optionId && q.partnerActualOptionId === op.optionId)
              || (q.partnerGuessMyOptionId === op.optionId && q.myOptionId === op.optionId)
  if (hit) return '#FFF0F5'
  if (q.myOptionId === op.optionId || q.partnerActualOptionId === op.optionId) return '#FAFAFA'
  if (q.myGuessPartnerOptionId === op.optionId || q.partnerGuessMyOptionId === op.optionId) return '#FAFAFA'
  return 'transparent'
}
function opColor(q: TacitQuestion, op: TacitQOption) {
  if (q.myOptionId === op.optionId) return '#FF6B9D'
  if (q.partnerActualOptionId === op.optionId) return '#1890FF'
  if (q.myGuessPartnerOptionId === op.optionId || q.partnerGuessMyOptionId === op.optionId) return '#D9D9D9'
  return '#eee'
}
function fmt(t?: string) {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getMonth()+1}/${d.getDate()}`
}
</script>

<style scoped>
.row-line {
  display: flex;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px dashed #FCE4EF;
}
.row-line:last-child { border-bottom: 0; }
.tag-pill {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  background: #F5F5F5;
  color: #666;
  margin-left: 6px;
}
.clickable { cursor: pointer; }
.clickable:active { transform: scale(0.99); }
.tacit-op {
  padding: 12px 16px;
  border-radius: 12px;
  margin: 6px 0;
  background: #FAFAFA;
  border: 2px solid transparent;
  transition: all .12s;
  cursor: pointer;
  font-weight: 500;
}
.tacit-op:active { transform: scale(0.995); }
.tacit-op-me {
  background: #FFF0F5 !important;
  border-color: #FF6B9D !important;
  color: #FF3D7F;
}
.tacit-op-ta {
  background: #E6F7FF !important;
  border-color: #1890FF !important;
  color: #1890FF;
}
</style>