<template>
  <div class="page">
    <van-nav-bar title="互动玩法" fixed placeholder />

    <div class="card" style="margin-top:16px;background:linear-gradient(135deg,#FFE0EC 0%,#FFF5F7 100%);">
      <div style="display:flex;align-items:center;justify-content:space-between;">
        <div>
          <div style="font-weight:700;font-size:18px;">💘 互动玩法中心</div>
          <div class="subtle" style="margin-top:4px;">每天完成互动可赚取金币兑换心愿</div>
        </div>
        <span class="coin-tag">💰 {{ coins }}</span>
      </div>
    </div>

    <div class="grid8">
      <div class="g8-card" @click="$router.push('/app/interactive/quiz')">
        <div class="g8-emoji" style="background:#FFF3C4;">❓</div>
        <div class="g8-name">每日默契</div>
        <div class="g8-sub">每天3道题 · +10💰</div>
      </div>
      <div class="g8-card" @click="$router.push('/app/interactive/icebreak')">
        <div class="g8-emoji" style="background:#D9F7E2;">🎡</div>
        <div class="g8-name">破冰大转盘</div>
        <div class="g8-sub">每日3次 · 赢金币</div>
      </div>
      <div class="g8-card" @click="$router.push('/app/interactive/wishes')">
        <div class="g8-emoji" style="background:#FFE0EC;">🎁</div>
        <div class="g8-name">心愿商城</div>
        <div class="g8-sub">兑换彼此小心愿</div>
      </div>
      <div class="g8-card" @click="$router.push('/app/interactive/chat')">
        <div class="g8-emoji" style="background:#E0EAFF;">💬</div>
        <div class="g8-name">悄悄话</div>
        <div class="g8-sub">私密聊天 · 已读状态</div>
      </div>
      <div class="g8-card" @click="$router.push('/app/interactive/tacit')">
        <div class="g8-emoji" style="background:#F5E0FF;">🧩</div>
        <div class="g8-name">默契小游戏</div>
        <div class="g8-sub">你懂我吗？5回合PK</div>
      </div>
      <div class="g8-card" @click="goWeekly">
        <div class="g8-emoji" style="background:#FFEACC;">📊</div>
        <div class="g8-name">恋爱周报</div>
        <div class="g8-sub">9张卡片 · 恋爱力评分</div>
      </div>
      <div class="g8-card" @click="goRecord('diary')">
        <div class="g8-emoji" style="background:#DFF6F0;">📝</div>
        <div class="g8-name">写日记</div>
        <div class="g8-sub">记录心动时刻 · +8💰</div>
      </div>
      <div class="g8-card" @click="goRecord('mood')">
        <div class="g8-emoji" style="background:#FFD6E7;">😊</div>
        <div class="g8-name">心情打卡</div>
        <div class="g8-sub">今日心情 · +5💰</div>
      </div>
    </div>

    <div class="card">
      <div class="card-title"><span>🎯 今日挑战</span><span class="subtle" @click="refreshOne">换一组</span></div>
      <div v-for="(t,i) in dailyTasks" :key="i" class="task-row">
        <van-checkbox :model-value="t.done" shape="round" :checked-color="'#FF6B9D'" />
        <div style="flex:1;margin:0 12px;">
          <div style="font-weight:600;">{{ t.title }}</div>
          <div class="subtle">{{ t.desc }}</div>
        </div>
        <span class="tag-pill">+{{ t.coin }}💰</span>
      </div>
    </div>

    <div class="card">
      <div class="card-title"><span>💡 破冰灵感</span></div>
      <div class="quote">"{{ quote }}"</div>
      <div class="subtle" style="text-align:right;margin-top:8px;">—— 今日心动小提示</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth.store'

const auth = useAuthStore()
const router = useRouter()
const coins = computed(() => auth.coupleInfo?.coinsTotal || 0)

const dailyTasks = ref<any[]>([
  { title: '一起完成1次心情打卡', desc: '了解彼此今天的状态', coin: 5, done: false },
  { title: '回答今日3道默契题', desc: '看看你们有多了解', coin: 10, done: false },
  { title: '转一次破冰大转盘', desc: '完成小任务，感情升温', coin: 15, done: false }
])

const quotes = [
  '爱情不是终日彼此对视，而是共同瞭望远方。',
  '最好的爱情，是一起成长，彼此成就。',
  '你今天想对TA说却没说出口的那句话是什么？',
  '还记得第一次牵起TA的手是什么感觉吗？',
  '选一部你们都没看过的电影，今晚一起看吧。',
  '给彼此起一个只有你们知道的昵称。',
  '一起做顿饭吧，哪怕是泡面加蛋。❤️'
]
const quote = ref(quotes[Math.floor(Math.random() * quotes.length)])

function refreshOne() {
  quote.value = quotes[Math.floor(Math.random() * quotes.length)]
}
function goWeekly() { router.push('/app/settings/weekly') }
function goRecord(tab: string) {
  // hack: 用 sessionStorage 给RecordView传tab状态
  sessionStorage.setItem('record.tab', tab)
  router.push('/app/record')
}
</script>
<style scoped>
.grid8 {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
  padding: 0 16px;
}
.g8-card {
  background: #fff;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(255,107,157,0.05);
}
.g8-emoji {
  width: 52px; height: 52px;
  border-radius: 14px;
  display: flex; align-items: center; justify-content: center;
  font-size: 28px;
  margin-bottom: 10px;
}
.g8-name { font-weight: 700; margin-bottom: 2px; }
.g8-sub { font-size: 12px; color: #888; }

.task-row {
  display: flex;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px dashed #FCE4EF;
}
.task-row:last-child { border-bottom: 0; }

.quote {
  padding: 16px;
  background: #FFFAFC;
  border-left: 3px solid #FF6B9D;
  line-height: 1.8;
  color: #555;
  border-radius: 0 10px 10px 0;
}
</style>