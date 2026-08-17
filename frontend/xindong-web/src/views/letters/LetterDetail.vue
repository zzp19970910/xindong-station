<template>
  <div class="page">
    <van-nav-bar title="情书" left-arrow fixed placeholder @click-left="$router.back()" />

    <div v-if="data" style="padding:16px;">
      <div class="letter-cover" :style="{background: bgStyle}">
        <div style="text-align:center;padding:30px 20px;">
          <div v-if="data.isCapsule && !canOpen" style="font-size:80px;opacity:0.6;">🔒</div>
          <template v-else>
            <div style="font-size:60px;margin-bottom:12px;">💌</div>
            <div style="font-size:18px;font-weight:700;color:#FF3D7F;">{{ data.title }}</div>
            <div class="subtle" style="margin-top:8px;color:#884;">
              来自 {{ senderLabel }} · {{ fmt(data.sentAt || data.createdAt) }}
            </div>
          </template>
        </div>

        <div v-if="data.isCapsule && !canOpen" style="text-align:center;padding:20px;background:rgba(255,255,255,0.7);margin:0 16px 24px;border-radius:14px;">
          <div style="font-size:15px;font-weight:600;">胶囊还未开启</div>
          <div style="font-size:13px;color:#666;margin-top:8px;">
            拆封时间：{{ data.capsuleOpenAt }}<br/>
            还有 <b style="color:#6B5BFF;">{{ countdown }}</b>
          </div>
        </div>

        <div v-if="!data.isCapsule || canOpen" class="letter-body">
          <div style="line-height:2.2;white-space:pre-wrap;color:#444;font-size:15px;">{{ data.body }}</div>
          <div style="text-align:right;margin-top:30px;color:#999;font-size:12px;">
            —— 你的 {{ senderLabel }} 💕
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-title"><span>💬 回信</span></div>
        <van-field v-model="replyBody" type="textarea" rows="3" autosize placeholder="想对TA说点什么？" />
        <div style="display:flex;justify-content:flex-end;margin-top:10px;">
          <van-button type="primary" color="#FF6B9D" round size="small" @click="onReply">回复这封信</van-button>
        </div>
      </div>

      <div style="display:flex;gap:8px;margin-top:16px;">
        <van-button block plain color="#FF4D4F" round @click="onCancel" v-if="data.isCapsule && !canOpen">取消预约</van-button>
        <van-button block type="primary" color="#FF6B9D" round @click="$router.push('/app/letters/write')">再写一封</van-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { loveLetterApi, type LoveLetterItem } from '@/api'
import { useAuthStore } from '@/stores/auth.store'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const id = Number(route.params.id)
const data = ref<LoveLetterItem | null>(null)
const replyBody = ref('')
const countdown = ref('')

const canOpen = computed(() => {
  if (!data.value?.isCapsule) return true
  if (!data.value.capsuleOpenAt) return true
  return new Date(data.value.capsuleOpenAt).getTime() <= Date.now()
})

const senderLabel = computed(() => {
  if (!data.value) return 'TA'
  return data.value.senderId === auth.userId ? '我' : 'TA'
})

const bgStyle = computed(() =>
  data.value?.isCapsule
    ? 'linear-gradient(135deg, #E0D9FF 0%, #FFF0F5 100%)'
    : 'linear-gradient(135deg, #FFF0F5 0%, #FFF 100%)'
)

let tm: any = null

onMounted(async () => {
  try {
    data.value = await loveLetterApi.get(id)
    if (!data.value!.isRead && data.value!.senderId !== auth.userId) {
      loveLetterApi.markRead(id).catch(() => {})
    }
  } catch (e) {}
  tickCountdown()
  tm = setInterval(tickCountdown, 60000)
})
onBeforeUnmount(() => { if (tm) clearInterval(tm) })

function tickCountdown() {
  if (!data.value?.capsuleOpenAt) return
  const ms = new Date(data.value.capsuleOpenAt).getTime() - Date.now()
  if (ms < 0) { countdown.value = '已到开启时间'; return }
  const d = Math.floor(ms / 86400000)
  const h = Math.floor((ms % 86400000) / 3600000)
  const m = Math.floor((ms % 3600000) / 60000)
  if (d > 0) countdown.value = `${d} 天 ${h} 小时`
  else if (h > 0) countdown.value = `${h} 小时 ${m} 分钟`
  else countdown.value = `${m} 分钟`
}

async function onReply() {
  if (!replyBody.value.trim()) return showToast('写点内容呀')
  try {
    await loveLetterApi.reply(id, { body: replyBody.value })
    showToast({ type: 'success', message: '回信已送达 💌' })
    replyBody.value = ''
  } catch (e) {}
}

async function onCancel() {
  try {
    await showConfirmDialog({ title: '取消胶囊？', message: '确定取消此时光胶囊预约吗？' })
    await loveLetterApi.cancelSchedule(id)
    showToast('已取消预约')
    router.back()
  } catch (e) {}
}

function fmt(t?: string) {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getFullYear()}/${d.getMonth()+1}/${d.getDate()}`
}
</script>

<style scoped>
.letter-cover {
  border-radius: 20px;
  box-shadow: 0 4px 20px rgba(255, 107, 157, 0.1);
  overflow: hidden;
}
.letter-body {
  padding: 24px 20px 30px;
  background: #FFFCF9;
  margin: 0 16px 24px;
  border-radius: 16px;
  border: 1px dashed #FFE0D0;
}
</style>