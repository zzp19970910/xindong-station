<template>
  <div class="page">
    <van-nav-bar title="每日默契" left-arrow fixed placeholder @click-left="$router.back()">
      <template #right>
        <span class="coin-tag" style="margin-right:12px;">+10💰</span>
      </template>
    </van-nav-bar>

    <div v-if="!answered" class="card" style="margin-top:16px;">
      <div style="text-align:center;padding:12px 0;">
        <div style="font-weight:700;font-size:17px;">💕 今日灵魂拷问</div>
        <div class="subtle" style="margin-top:4px;">共 {{ questions.length }} 题 · 答完可看默契度</div>
      </div>

      <div style="margin-top:16px;">
        <div style="font-weight:700;color:#FF6B9D;">Q{{ idx + 1 }}/{{ questions.length }}</div>
        <div style="font-size:18px;font-weight:600;margin:12px 0;">{{ cur?.question }}</div>
        <van-radio-group v-model="curAns" direction="vertical">
          <div v-for="(op,i) in cur?.options" :key="op.k"
               :style="{background:curAns===op.k?'#FFF0F5':'#FAFAFA',border:`2px solid ${curAns===op.k?'#FF6B9D':'transparent'}`}"
               style="border-radius:12px;padding:14px 16px;margin:8px 0;"
               @click="curAns = op.k">
            <div style="display:flex;align-items:center;gap:10px;">
              <div :style="{background:curAns===op.k?'#FF6B9D':'#E0E0E0',color:curAns===op.k?'#fff':'#666'}"
                   style="width:26px;height:26px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-weight:700;">
                {{ ['A','B','C','D','E'][i] || '•' }}
              </div>
              <span style="flex:1;">{{ op.v }}</span>
            </div>
          </div>
        </van-radio-group>
      </div>

      <div style="display:flex;gap:10px;margin-top:20px;">
        <van-button block plain round color="#FF6B9D" :disabled="idx===0" @click="prev">上一题</van-button>
        <van-button v-if="idx < questions.length - 1" block type="primary" round color="#FF6B9D" @click="next">下一题</van-button>
        <van-button v-else block type="primary" round color="#FF6B9D" @click="submitAll">提交答题</van-button>
      </div>
    </div>

    <div v-else class="card" style="margin-top:16px;">
      <div style="text-align:center;">
        <div style="font-size:60px;">{{ matchGrade }}</div>
        <div style="font-size:20px;font-weight:700;">今日默契度 <span class="pink">{{ matchAvg }}%</span></div>
        <div class="subtle" style="margin-top:6px;">{{ matchComment }}</div>
        <van-tag v-if="partnerDone" round type="primary" color="#FFF2CC" text-color="#B8860B" style="margin-top:12px;">
          TA 已完成，查看双方答案对比
        </van-tag>
        <van-tag v-else round color="#F5F5F5" text-color="#999" style="margin-top:12px;">
          ⏳ 等待 TA 答题中...
        </van-tag>
      </div>

      <div v-for="(q,i) in questions" :key="q.id" style="margin-top:24px;border-top:1px dashed #FCE4EF;padding-top:16px;">
        <div style="font-weight:700;margin-bottom:10px;">Q{{ i + 1 }}. {{ q.question }}</div>
        <div v-for="op in q.options" :key="op.k"
             :style="{background: ansBg(q.id, op.k), borderLeft:`3px solid ${ansColor(q.id,op.k)}`}"
             style="padding:10px 12px;border-radius:8px;margin:6px 0;">
          {{ op.v }}
          <span v-if="myA(q.id)===op.k" class="tag-pill" style="background:#FFF0F5;">我选的</span>
          <span v-if="partnerDone && partnerA(q.id)===op.k" class="tag-pill" style="background:#E6F7FF;color:#1890FF;">TA选的</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { showToast } from 'vant'
import { dailyQuizApi, type QuizQuestion } from '@/api'

const questions = ref<QuizQuestion[]>([])
const idx = ref(0)
const answers = reactive<Record<number, string>>({})
const answered = ref(false)
const partnerDone = ref(false)
const matchAvg = ref(0)
const allMy = ref<any[]>([])
const allPartner = ref<any[]>([])

// 🔴 兼容后端选项格式 {optionId,label} 或 {k,v} 或 纯string
function normOptions(raw: any): { k: string; v: string }[] {
  if (!raw) return []
  if (Array.isArray(raw)) {
    return raw.map((o: any, i) => {
      if (typeof o === 'string') return { k: String(i), v: o }
      const k = String(o?.k ?? o?.optionId ?? o?.key ?? i)
      const v = String(o?.v ?? o?.label ?? o?.text ?? o?.value ?? '')
      return { k, v }
    })
  }
  return []
}
function normId(q: any): number {
  return Number(q?.id ?? q?.questionId ?? 0)
}
function normQuestion(q: any): QuizQuestion {
  return {
    id: normId(q),
    category: String(q.category || ''),
    question: String(q.question || q.title || ''),
    options: normOptions(q.options),
    isMultiple: !!q.isMultiple
  } as QuizQuestion
}

const cur = computed(() => questions.value[idx.value])
const curAns = computed({
  get: () => cur.value ? answers[cur.value.id] || '' : '',
  set: (v: string) => { if (cur.value) answers[cur.value.id] = v }
})

const matchGrade = computed(() => {
  if (matchAvg.value >= 90) return '💯'
  if (matchAvg.value >= 75) return '❤️❤️❤️'
  if (matchAvg.value >= 50) return '❤️❤️'
  if (matchAvg.value >= 30) return '❤️'
  return '🤝'
})
const matchComment = computed(() => {
  if (!partnerDone.value) return '等TA答完，就能看到你们的默契指数啦～'
  if (matchAvg.value >= 90) return '天呐！你们是灵魂伴侣吧？'
  if (matchAvg.value >= 70) return '很了解彼此哦，继续加油～'
  if (matchAvg.value >= 50) return '还不错，多聊聊会更懂'
  return '看来需要多做这题呀～'
})

onMounted(async () => {
  try {
    const r: any = await dailyQuizApi.todayResult()
    if (r && r.myAnswers?.length) {
      questions.value = (r.questions || []).map(normQuestion)
      allMy.value = r.myAnswers
      allPartner.value = r.partnerAnswers || []
      matchAvg.value = r.matchAvg ?? r.matchPercent ?? 0
      partnerDone.value = (r.partnerAnswers || []).length > 0
      for (const a of r.myAnswers) {
        const ansArr = Array.isArray(a.answer) ? a.answer : (a.answerOptionIds || a.answerOptions || [a.answerOptionId || a.answer || ''])
        const qid = Number(a.questionId ?? a.qid)
        if (qid) answers[qid] = String(ansArr[0] ?? '')
      }
      answered.value = true
    } else {
      const t: any = await dailyQuizApi.today()
      questions.value = (t.questions || []).map(normQuestion)
    }
  } catch (e: any) {
    showToast(e?.message || '加载题目失败')
  }
})

function prev() { if (idx.value > 0) idx.value-- }
function next() {
  if (!curAns.value) return showToast('请先选一个答案哦')
  if (idx.value < questions.value.length - 1) idx.value++
}

async function submitAll() {
  const undone = questions.value.filter(q => !answers[q.id])
  if (undone.length) return showToast(`还有 ${undone.length} 题没答哦`)
  try {
    for (const q of questions.value) {
      const pick = answers[q.id]
      // 兼容：后端submit期望 {questionId, answer: [string]} 或 {questionId, answerOptionIds:[number]}
      const num = Number(pick)
      const payload: any = Number.isFinite(num) && num > 0
        ? { questionId: q.id, answer: [pick], answerOptionIds: [num] }
        : { questionId: q.id, answer: [pick] }
      await dailyQuizApi.submit(payload)
    }
    showToast({ type: 'success', message: '提交成功！+10💰' })
    answered.value = true
    const r: any = await dailyQuizApi.todayResult()
    if (r) {
      allMy.value = r.myAnswers
      allPartner.value = r.partnerAnswers || []
      matchAvg.value = r.matchAvg ?? r.matchPercent ?? 0
      partnerDone.value = (r.partnerAnswers || []).length > 0
    }
  } catch (e: any) {
    showToast(e?.message || '提交失败')
  }
}

function myA(qid: number) { const x = allMy.value.find(a => a.questionId === qid); return (x?.answer || [''])[0] }
function partnerA(qid: number) { const x = allPartner.value.find(a => a.questionId === qid); return (x?.answer || [''])[0] }

function ansBg(qid: number, k: string) {
  const me = myA(qid), ta = partnerA(qid)
  if (me === k && ta === k && partnerDone.value) return '#FFF0F5'
  if (me === k) return '#FFFAF7'
  if (partnerDone.value && ta === k) return '#F0F8FF'
  return '#FAFAFA'
}
function ansColor(qid: number, k: string) {
  const me = myA(qid), ta = partnerA(qid)
  if (me === k && ta === k && partnerDone.value) return '#FF6B9D'
  if (me === k) return '#FF6B9D'
  if (partnerDone.value && ta === k) return '#1890FF'
  return '#eee'
}
</script>