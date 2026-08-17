<template>
  <div class="page">
    <van-nav-bar title="心动空间站" fixed placeholder>
      <template #right>
        <span class="coin-tag" @click="$router.push('/app/settings/coins')" style="margin-right:12px;">💰 {{ coins }}</span>
      </template>
    </van-nav-bar>

    <div v-if="auth.hasCouple" style="padding:0 16px;">
      <div class="hero-card">
        <div class="hero-days">
          <span class="hero-num">{{ data?.daysTogether || 0 }}</span>
          <span class="hero-unit">天</span>
        </div>
        <div class="hero-slogan">和TA在一起的每一秒都值得被珍藏 💕</div>
        <div v-if="data?.streak" class="hero-streak">🔥 连续打卡 {{ data.streak }} 天</div>
      </div>

      <div class="quick">
        <div class="q-item" @click="moodCheckin">
          <div class="grid-emoji">😊</div>
          <div class="grid-label">心情打卡</div>
        </div>
        <div class="q-item" @click="$router.push('/app/interactive/quiz')">
          <div class="grid-emoji">❓</div>
          <div class="grid-label">每日默契</div>
          <div v-if="data?.todayQuizDone" class="badge-done">已答</div>
        </div>
        <div class="q-item" @click="$router.push('/app/interactive/icebreak')">
          <div class="grid-emoji">🎡</div>
          <div class="grid-label">破冰转盘</div>
        </div>
        <div class="q-item" @click="$router.push('/app/record/diary/edit')">
          <div class="grid-emoji">📝</div>
          <div class="grid-label">写日记</div>
        </div>
        <div class="q-item" @click="$router.push('/app/settings/weekly')">
          <div class="grid-emoji">📊</div>
          <div class="grid-label">恋爱周报</div>
        </div>
        <div class="q-item" @click="$router.push('/app/interactive/chat')">
          <div class="grid-emoji">💬</div>
          <div class="grid-label">悄悄话</div>
          <div v-if="data?.unreadMessages" class="badge-red">{{ data.unreadMessages }}</div>
        </div>
        <div class="q-item" @click="$router.push('/app/interactive/wishes')">
          <div class="grid-emoji">🎁</div>
          <div class="grid-label">心愿商城</div>
        </div>
        <div class="q-item" @click="$router.push('/app/interactive/tacit')">
          <div class="grid-emoji">🧩</div>
          <div class="grid-label">默契游戏</div>
        </div>
      </div>

      <div class="card clickable" @click="$router.push('/app/record?tab=anniv')">
        <div class="card-title">
          <span>🎂 即将到来的纪念日</span>
          <span class="subtle arrow-link">全部 ></span>
        </div>
        <div v-if="!data?.upcomingAnniversaries?.length" class="subtle empty-tip" @click.stop="$router.push('/app/record/anniv/edit')">
          暂无临近的纪念日，<span class="pink">去添加一个吧～</span>
        </div>
        <div v-for="a in data?.upcomingAnniversaries?.slice(0, 3)" :key="a.id" class="row-line" @click.stop="$router.push('/app/record?tab=anniv')">
          <span style="font-size:22px;">{{ a.icon || a.emoji || '🎉' }}</span>
          <div style="flex:1;margin:0 12px;">
            <div style="font-weight:600;">{{ a.name || a.title }}</div>
            <div class="subtle">{{ a.nextDate || a.targetDate || a.date }}</div>
          </div>
          <div style="text-align:right;">
            <div class="pink" style="font-weight:700;">D{{ (a.daysLeft ?? 0) >= 0 ? '-' + a.daysLeft : '+' + (-a.daysLeft) }}</div>
            <div class="subtle">{{ (a.daysLeft ?? 0) === 0 ? '就是今天!' : (a.daysLeft > 0 ? '天后' : '天前') }}</div>
          </div>
        </div>
      </div>

      <div class="card clickable" @click="$router.push('/app/record?tab=diary')">
        <div class="card-title">
          <span>📖 最近的时光日记</span>
          <span class="subtle arrow-link">全部 ></span>
        </div>
        <div v-if="!data?.recentDiaries?.length" class="subtle empty-tip" @click.stop="$router.push('/app/record/diary/edit')">
          还没有日记，<span class="pink">去记录今天的小美好吧～</span>
        </div>
        <div v-for="d in data?.recentDiaries?.slice(0,2)" :key="d.id" class="row-line" @click.stop="$router.push(`/app/record/diary/${d.id}`)">
          <span style="font-size:22px;">{{ d.moodEmoji || '🌈' }}</span>
          <div style="flex:1;margin:0 12px;overflow:hidden;">
            <div style="font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">
              {{ d.title || '未命名日记' }}
            </div>
            <div class="subtle" style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">
              {{ stripHtml(d.content).slice(0, 40) }}...
            </div>
          </div>
          <div class="subtle">{{ fmtTime(d.createdAt) }}</div>
        </div>
      </div>

      <div class="card clickable" @click="$router.push('/app/record?tab=check')">
        <div class="card-title">
          <span>✅ 恋爱清单进度</span>
          <span class="pink arrow-link">{{ data?.checklistProgress?.done || 0 }}/{{ data?.checklistProgress?.total || 0 }}</span>
        </div>
        <van-progress
          :percentage="data?.checklistProgress?.progressPct || 0"
          color="#FF6B9D"
          stroke-width="10"
          style="margin:12px 0 6px;"
        />
        <div v-if="data?.checklistProgress?.nextStage" class="subtle">
          再完成 {{ data.checklistProgress.nextStage.needMore }} 件 → 下一里程碑
          <b class="pink">🎁 +{{ data.checklistProgress.nextStage.bonus }}金币</b>
        </div>
        <div v-else class="subtle empty-tip" @click.stop="$router.push('/app/record/checklist/edit')">
          还没有清单，<span class="pink">去创建第一条心动清单吧～</span>
        </div>
      </div>

      <div class="card">
        <div class="card-title">
          <span>💝 今日心情</span>
          <span class="subtle arrow-link" @click="moodCheckin">打卡 ></span>
        </div>
        <div style="display:flex;gap:12px;justify-content:space-around;">
          <div v-for="p in moodsPair" :key="p.who" style="text-align:center;"
               class="clickable" @click="p.who === 'me' ? moodCheckin() : viewTaMood(p)">
            <div :style="{
              width: '64px', height: '64px', borderRadius: '50%',
              background: p.emoji ? (p.who === 'me' ? '#FFF0F5' : '#E6F7FF') : '#FFF0F5',
              border: p.who === 'me' ? '2px dashed #FF8FB1' : '2px dashed #91CAFF',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: p.emoji ? '34px' : '30px',
              transition: 'transform .15s'
            }" :class="{ 'tap-scale': true }">
              {{ p.emoji || '🤍' }}
            </div>
            <div :class="['subtle', { pink: p.who==='me', 'blue': p.who!=='me' }]" style="margin-top:6px;font-weight:600;">
              {{ p.who === 'me' ? '我 ' + (p.emoji ? '✓ 已打卡' : ' 点击打卡') : 'TA ' + (p.emoji ? '✓ 已打卡' : ' 还未打卡') }}
            </div>
          </div>
        </div>
      </div>

      <div style="height:20px;"></div>
    </div>

    <div v-else style="padding:80px 24px;text-align:center;">
      <div style="font-size:80px;">💘</div>
      <h2 style="color:#FF3D7F;margin:16px 0 8px;">还没绑定另一半哦</h2>
      <p class="subtle" style="margin-bottom:32px;">邀请TA一起开启只属于你们的心动记录</p>
      <van-button type="primary" block color="#FF6B9D" round size="large" @click="$router.push('/bind')">
        去绑定 / 生成邀请码
      </van-button>
    </div>

    <!-- 打卡弹层 -->
    <van-popup v-model:show="moodPop" round position="bottom" :style="{ height: '65%' }">
      <div style="padding:24px;">
        <h3 style="margin:0 0 16px;">今天心情怎么样？🌸</h3>
        <div style="display:flex;gap:12px;justify-content:space-around;margin:24px 0;flex-wrap:wrap;">
          <div v-for="e in emos" :key="e.k" @click="pEmoji=e.k; pScore=e.s"
               :style="{background:pEmoji===e.k?'#FFF0F5':'transparent',borderRadius:'50%'}"
               style="width:60px;height:60px;display:flex;align-items:center;justify-content:center;font-size:36px;">
            {{ e.k }}
          </div>
        </div>
        <van-slider v-model="pScore" :min="1" :max="10" bar-height="4" />
        <van-field v-model="pNote" rows="3" autosize type="textarea" placeholder="想说点什么？(可选)" style="margin-top:16px;" />
        <van-button block type="primary" color="#FF6B9D" round style="margin-top:20px;" @click="submitMood">
          提交心情打卡（+5金币）
        </van-button>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showDialog } from 'vant'
import { useAuthStore } from '@/stores/auth.store'
import { dashboardApi, moodApi, type DashboardData, coupleApi } from '@/api'

const auth = useAuthStore()
const router = useRouter()

const data = ref<DashboardData | null>(null)
const coins = ref(0)

const emos = [
  { k: '😍', s: 10 }, { k: '🥰', s: 9 }, { k: '😊', s: 8 }, { k: '🙂', s: 7 },
  { k: '😐', s: 5 }, { k: '😟', s: 4 }, { k: '😢', s: 3 }, { k: '😠', s: 2 }
]
const moodPop = ref(false)
const pEmoji = ref('😊')
const pScore = ref(8)
const pNote = ref('')

const moodsPair = computed(() => {
  const today = (data.value?.todayMoods || []).slice(0, 2)
  const me = today.find(x => x.userId === auth.userId) || today[0]
  const ta = today.find(x => x.userId !== auth.userId) || today[1]
  return [
    { who: 'me', emoji: me?.emoji },
    { who: 'ta', emoji: ta?.emoji }
  ]
})

onMounted(async () => {
  if (!auth.hasCouple && auth.userInfo?.coupleId) {
    try {
      const c = await coupleApi.info(auth.userInfo.coupleId)
      auth.setCouple(c)
    } catch (e) {}
  }
  await reload()
})

async function reload() {
  if (!auth.coupleId) return
  try {
    data.value = await dashboardApi.home(auth.coupleId)
    coins.value = auth.coupleInfo?.coinsTotal ?? data.value?.coinsTotal ?? 0
  } catch (e) {}
}

function moodCheckin() {
  moodPop.value = true
  pEmoji.value = '😊'
  pScore.value = 8
  pNote.value = ''
}

async function submitMood() {
  try {
    await moodApi.checkin({ emoji: pEmoji.value, score: pScore.value, note: pNote.value })
    showToast({ type: 'success', message: '打卡成功！+5💰' })
    moodPop.value = false
    reload()
  } catch (e: any) {
    showToast(e?.message || '打卡失败，请稍后重试')
  }
}

function viewTaMood(p: { who: string; emoji?: string }) {
  if (p.emoji) {
    const ta = (data.value?.todayMoods || []).find(x => x.userId !== auth.userId)
    showDialog({
      title: 'TA今日心情',
      message: `${p.emoji}\n${ta?.note ? 'TA说：' + ta.note : '（TA没有留下文字说明）'}\n心情指数 ${ta?.score ?? '-'} / 10`,
      confirmButtonText: '查看心情历史',
      cancelButtonText: '好的',
      showCancelButton: true
    }).then(() => {
      router.push('/app/record?tab=mood')
    }).catch(() => {})
  } else {
    showDialog({
      title: 'TA还没打卡哦',
      message: '提醒TA来打个卡吧，互相看看今天的心情~',
      confirmButtonText: '知道了'
    })
  }
}

function stripHtml(s: string) { return (s || '').replace(/<[^>]+>/g, '').replace(/\s+/g, ' ') }
function fmtTime(t: string) {
  if (!t) return ''
  const d = new Date(t)
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const da = String(d.getDate()).padStart(2, '0')
  return `${m}-${da}`
}
defineExpose({ reload })
</script>

<style scoped>
.hero-card {
  margin: 16px 0;
  padding: 24px;
  border-radius: 20px;
  background: linear-gradient(135deg, #FF6B9D 0%, #FF8FB1 50%, #FFB3D1 100%);
  color: #fff;
  position: relative;
  overflow: hidden;
}
.hero-card::before {
  content: '💖';
  position: absolute;
  right: -16px;
  top: -16px;
  font-size: 160px;
  opacity: 0.2;
}
.hero-days { display: flex; align-items: baseline; gap: 6px; }
.hero-num { font-size: 54px; font-weight: 800; }
.hero-unit { font-size: 18px; opacity: 0.9; }
.hero-slogan { margin-top: 12px; font-size: 14px; opacity: 0.9; }
.hero-streak {
  position: absolute;
  right: 20px;
  bottom: 20px;
  background: rgba(255,255,255,0.25);
  backdrop-filter: blur(10px);
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 13px;
}
.quick {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  padding: 12px 0;
}
.q-item {
  background: #fff;
  border-radius: 16px;
  padding: 16px 4px;
  text-align: center;
  position: relative;
  box-shadow: 0 2px 8px rgba(255,107,157,0.05);
}
.row-line {
  display: flex;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px dashed #FCE4EF;
}
.row-line:last-child { border-bottom: 0; }
.badge-red {
  position: absolute;
  top: 6px; right: 6px;
  background: #FF4D4F;
  color: #fff;
  border-radius: 999px;
  min-width: 18px; height: 18px;
  font-size: 11px;
  display: flex; align-items: center; justify-content: center;
  padding: 0 4px;
}
.badge-done {
  position: absolute;
  top: 6px; right: 6px;
  background: #52C41A;
  color: #fff;
  border-radius: 4px;
  font-size: 10px;
  padding: 2px 6px;
}
.clickable { cursor: pointer; transition: transform .12s ease, box-shadow .12s ease; }
.clickable:active { transform: scale(0.995); }
.tap-scale:active { transform: scale(0.9); }
.arrow-link {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-weight: 600;
  padding: 4px 8px;
  border-radius: 999px;
  transition: background .12s;
}
.arrow-link:active { background: rgba(255,107,157,0.08); }
.empty-tip {
  padding: 16px 12px;
  border-radius: 12px;
  background: #FFF9FC;
  margin-top: 6px;
  text-align: center;
  cursor: pointer;
  line-height: 1.7;
  border: 1px dashed #FFD6E7;
}
.empty-tip:active { background: #FFF0F5; }
.card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.blue { color: #1890FF !important; }
</style>