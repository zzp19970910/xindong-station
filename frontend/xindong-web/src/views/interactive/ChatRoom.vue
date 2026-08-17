<template>
  <div class="chat-page">
    <van-nav-bar title="悄悄话 💬" left-arrow fixed placeholder @click-left="$router.back()">
      <template #right>
        <span v-if="unread" style="background:#FF4D4F;color:#fff;padding:2px 8px;border-radius:999px;font-size:11px;margin-right:12px;">
          {{ unread }} 条未读
        </span>
      </template>
    </van-nav-bar>

    <div class="chat-list" ref="listEl">
      <div v-if="!list.length" style="text-align:center;padding:80px 40px;color:#999;">
        <div style="font-size:80px;">💌</div>
        <div style="margin-top:16px;">悄悄话：只属于你们的私密聊天</div>
        <div class="subtle" style="margin-top:8px;">所有消息加密存储，支持已读状态</div>
      </div>

      <div v-for="m in list" :key="m.id"
           :class="['msg-row', isMe(m) ? 'mine':'theirs']"
           :ref="(el) => { if (el) lastMsgEl = el as HTMLElement }">
        <div class="avatar">{{ isMe(m) ? '🐰' : '🦊' }}</div>
        <div class="msg-wrap">
          <div class="meta">
            {{ isMe(m) ? '我' : 'TA' }} · {{ fmt(m.createdAt) }}
            <span v-if="m.type==='IMAGE'" class="tag-pill" style="margin-left:6px;">图片</span>
            <span v-if="m.type==='EMOJI'" class="tag-pill" style="margin-left:6px;">表情</span>
          </div>
          <div class="bubble" :class="isMe(m)?'b-me':'b-them'">
            {{ m.content }}
          </div>
          <div v-if="isMe(m)" class="read">
            {{ m.isRead ? '✅ 已读' : '⏳ 未读' }}
          </div>
        </div>
      </div>
    </div>

    <div class="chat-input">
      <div style="display:flex;gap:8px;padding:4px 0;">
        <div v-for="e in emojis" :key="e" @click="sendEmoji(e)"
             style="width:30px;text-align:center;cursor:pointer;">{{ e }}</div>
      </div>
      <div style="display:flex;gap:8px;align-items:flex-end;">
        <van-field
          v-model="text"
          type="textarea"
          rows="1"
          autosize
          placeholder="想对TA说点什么... (Enter 发送)"
          style="flex:1;"
          @keydown.enter.prevent.exact="sendText"
        />
        <van-button type="primary" color="#FF6B9D" round icon="paperplane-o" @click="sendText">发送</van-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick, onBeforeUnmount } from 'vue'
import { useAuthStore } from '@/stores/auth.store'
import { showToast } from 'vant'
import { messageApi, type MsgItem } from '@/api'

const auth = useAuthStore()
const list = ref<MsgItem[]>([])
const text = ref('')
const unread = ref(0)
const listEl = ref<HTMLElement | null>(null)
let lastMsgEl: HTMLElement | null = null
let tm: any = null

const emojis = ['💖','😘','🥰','😍','😊','🤗','😢','😠','🙏','👍','👀','🎉','🍔','🌹','☕','🌙']

onMounted(async () => {
  await load()
  markAllRead()
  tm = setInterval(load, 15000)
})
onBeforeUnmount(() => { if (tm) clearInterval(tm) })

async function load() {
  try {
    const r: any = await messageApi.list({ size: 100, asc: true })
    list.value = r.list || []
    await nextTick()
    scrollBottom()
    const u = await messageApi.unread().catch(() => ({ count: 0 })) as any
    unread.value = u.count || 0
  } catch (e) {}
}

async function markAllRead() {
  try { await messageApi.readBatch() } catch (e) {}
}

function isMe(m: MsgItem) {
  return m.senderId === auth.userId || m.type === 'TEXT' && (m as any).partnerIdx === 1
}

async function sendText() {
  const val = text.value.trim()
  if (!val) return
  const partnerId = Number(sessionStorage.getItem('partner.id') || '0') || (auth.userId === 1 ? 2 : 1)
  try {
    await messageApi.send({ receiverId: partnerId, type: 'TEXT', content: val })
    text.value = ''
    await load()
  } catch (e) {}
}

async function sendEmoji(e: string) {
  const partnerId = Number(sessionStorage.getItem('partner.id') || '0') || (auth.userId === 1 ? 2 : 1)
  try {
    await messageApi.send({ receiverId: partnerId, type: 'EMOJI', content: e })
    await load()
  } catch (e2) {}
}

function scrollBottom() {
  lastMsgEl?.scrollIntoView({ behavior: 'smooth' })
  if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
}

function fmt(t?: string) {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  const same = d.toDateString() === now.toDateString()
  return `${same?'':`${d.getMonth()+1}/${d.getDate()} `}${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
}
</script>

<style scoped>
.chat-page { min-height: 100vh; display: flex; flex-direction: column; background: #FFF7FA; }
.chat-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px 12px 12px;
  padding-bottom: 180px;
}
.msg-row {
  display: flex;
  margin-bottom: 16px;
  align-items: flex-start;
  gap: 8px;
}
.msg-row.mine { flex-direction: row-reverse; }
.avatar {
  width: 36px; height: 36px;
  border-radius: 50%;
  background: #FFE0EC;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}
.msg-row.mine .avatar { background: #FFE0EC; }
.msg-row.theirs .avatar { background: #E0EAFF; }
.msg-wrap { max-width: 75%; }
.msg-row.mine .msg-wrap { text-align: right; }
.meta { font-size: 11px; color: #999; margin-bottom: 4px; }
.bubble {
  display: inline-block;
  padding: 10px 14px;
  border-radius: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-width: 100%;
}
.b-me { background: #FF6B9D; color: #fff; border-top-right-radius: 4px; }
.b-them { background: #fff; border: 1px solid #F0F0F0; border-top-left-radius: 4px; }
.read { font-size: 10px; color: #bbb; margin-top: 3px; }

.chat-input {
  position: sticky;
  bottom: 0; left: 0; right: 0;
  background: #fff;
  border-top: 1px solid #F0F0F0;
  padding: 10px 12px calc(10px + env(safe-area-inset-bottom));
  box-shadow: 0 -4px 12px rgba(0,0,0,0.03);
  z-index: 10;
}
</style>