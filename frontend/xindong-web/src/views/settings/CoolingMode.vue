<template>
  <div class="page">
    <van-nav-bar title="冷静模式" left-arrow fixed placeholder @click-left="$router.back()" />

    <div class="hero-card" :class="{active: st.isActive}">
      <div style="font-size:64px;">{{ st.isActive ? '🧊' : '💘' }}</div>
      <div style="font-size:22px;font-weight:800;margin-top:8px;">
        {{ st.isActive ? '冷静模式开启中' : '让感情更有缓冲空间' }}
      </div>
      <div class="sub" style="margin-top:8px;">
        {{ st.isActive ? '部分敏感功能已被限制，避免冲动伤害' : '吵架了？开个冷静模式，24小时后自动解除' }}
      </div>
    </div>

    <div v-if="st.isActive" class="card" style="background:linear-gradient(135deg,#E6F7FF 0%,#F5FAFF 100%);">
      <div class="card-title"><span>⏱️ 倒计时</span></div>
      <div style="font-size:32px;font-weight:800;color:#1677FF;text-align:center;">{{ countdown }}</div>
      <div class="subtle" style="text-align:center;margin-top:4px;">
        解除：{{ st.coolingUntil }}
        <span v-if="st.coolingLockUntil && new Date(st.coolingLockUntil) > new Date()" class="tag-pill" style="background:#E6F7FF;color:#1677FF;margin-left:8px;">强制锁定期</span>
      </div>
    </div>

    <div class="card">
      <div class="card-title"><span>🛡️ 开启后会限制什么？</span></div>
      <div v-for="r in rules" :key="r.t" class="row-line">
        <div style="width:36px;height:36px;border-radius:10px;background:#E6F7FF;display:flex;align-items:center;justify-content:center;font-size:18px;">{{ r.e }}</div>
        <div style="flex:1;margin:0 12px;">
          <div style="font-weight:600;">{{ r.t }}</div>
          <div class="subtle">{{ r.d }}</div>
        </div>
        <van-icon name="checked" color="#1677FF" size="20" />
      </div>
    </div>

    <div class="card">
      <div class="card-title"><span>💡 为什么需要冷静模式？</span></div>
      <div class="subtle" style="line-height:1.9;">
        吵架的时候最容易说出伤人的话、做出让自己后悔的决定。<br/>
        开启冷静模式后：
        <ul style="margin:8px 0 0;padding-left:18px;">
          <li>双方无法发送带负面关键词的悄悄话</li>
          <li>无法提交拒绝心愿（防止冲动消费/报复）</li>
          <li>无法删除共同记录（日记/纪念日等）</li>
          <li>无法解绑情侣关系（防冲动分手）</li>
        </ul>
        <br/>
        默认 24 小时后自动解除，<b style="color:#FF6B9D;">如果是你开启的，1小时后可手动解除；否则需等对方同意或到期。</b>
      </div>
    </div>

    <div style="padding:16px;">
      <van-button v-if="!st.isActive" type="primary" block color="#1677FF" round size="large" icon="shield-o" @click="enable">
        🧊 开启 24 小时冷静模式
      </van-button>
      <van-button v-else :disabled="!st.canDisable" type="primary" block color="#52C41A" round size="large" icon="passed" @click="disable">
        {{ st.canDisable ? '解除冷静模式' : '锁定中，无法解除' }}
      </van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { showConfirmDialog, showToast } from 'vant'
import { settingsApi, type CoolingStatus } from '@/api'
import { useAuthStore } from '@/stores/auth.store'

const auth = useAuthStore()
const st = reactive<CoolingStatus>({ isActive: false, remainingSeconds: 0, canDisable: false })
const countdown = ref('')
let tm: any = null

const rules = [
  { e: '💬', t: '悄悄话过滤', d: '含敏感词的消息会被拦截、提醒冷静' },
  { e: '❌', t: '拒绝操作限制', d: '无法拒绝心愿、清单等协作审批' },
  { e: '🗑️', t: '删除保护', d: '无法删除日记、纪念日、清单等共同记录' },
  { e: '💔', t: '解绑锁定', d: '禁止发起解绑/分手操作，防止冲动' },
  { e: '🎁', t: '消费保护', d: '无法用金币完成高价值心愿兑换' }
]

onMounted(async () => {
  await load()
  tick()
  tm = setInterval(tick, 1000)
})
onBeforeUnmount(() => { if (tm) clearInterval(tm) })

async function load() {
  if (!auth.coupleId) return
  try { Object.assign(st, await settingsApi.coolingStatus(auth.coupleId)) } catch (e) {}
}
function tick() {
  if (st.remainingSeconds <= 0) { countdown.value = '即将解除'; return }
  const h = Math.floor(st.remainingSeconds / 3600)
  const m = Math.floor((st.remainingSeconds % 3600) / 60)
  const s = st.remainingSeconds % 60
  countdown.value = `${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`
  if (st.isActive) st.remainingSeconds = Math.max(0, st.remainingSeconds - 1)
}

async function enable() {
  try {
    await showConfirmDialog({
      title: '开启冷静模式？',
      message: '接下来24小时部分敏感操作将被限制，防止吵架时的冲动决定。确定开启吗？'
    })
    Object.assign(st, await settingsApi.coolingEnable())
    auth.updateCouple({ coolingUntil: st.coolingUntil })
    showToast({ type: 'success', message: '冷静模式已开启 🧊' })
  } catch (e) {}
}

async function disable() {
  if (!st.canDisable) return
  try {
    await showConfirmDialog({
      title: '解除冷静模式',
      message: '确定双方都冷静好了吗？所有限制将恢复正常。'
    })
    Object.assign(st, await settingsApi.coolingDisable())
    auth.updateCouple({ coolingUntil: st.coolingUntil })
    showToast('已解除，继续好好相爱吧 💕')
  } catch (e) {}
}
</script>

<style scoped>
.hero-card {
  margin: 16px;
  padding: 32px 20px;
  border-radius: 20px;
  text-align: center;
  background: linear-gradient(135deg, #FFE0EC 0%, #FFF5F7 100%);
  box-shadow: 0 4px 16px rgba(255,107,157,0.1);
  transition: all .3s;
}
.hero-card.active {
  background: linear-gradient(135deg, #E6F7FF 0%, #F0FAFF 100%);
  box-shadow: 0 4px 16px rgba(22, 119, 255, 0.12);
}
.sub { color: #666; font-size: 13px; }
.row-line {
  display: flex;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px dashed #E6F0FF;
}
.row-line:last-child { border-bottom: 0; }
</style>