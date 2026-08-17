<template>
  <div class="page" style="background:#fff;">
    <van-nav-bar title="心动日记" left-arrow fixed placeholder @click-left="$router.back()" />

    <div v-if="data" style="padding:20px 16px;">
      <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:20px;">
        <div style="display:flex;align-items:center;gap:10px;">
          <span style="font-size:30px;">{{ data.moodEmoji || '🌈' }}</span>
          <b style="font-size:18px;">{{ data.title || '未命名日记' }}</b>
        </div>
        <span class="subtle">P{{ data.partnerIdx || 1 }} · {{ fmt(data.createdAt) }}</span>
      </div>

      <div style="line-height:2;font-size:15px;white-space:pre-wrap;color:#333;">{{ data.content }}</div>

      <div v-if="data.images?.length" style="display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin:20px 0;">
        <div v-for="(im,i) in data.images" :key="i" style="aspect-ratio:1;background:#FFE0EC;border-radius:10px;display:flex;align-items:center;justify-content:center;font-size:36px;">🖼️</div>
      </div>

      <div style="display:flex;justify-content:flex-end;gap:8px;margin-top:20px;">
        <van-button size="small" plain color="#FF4D4F" @click="onDel">删除</van-button>
      </div>
    </div>

    <div class="card" style="margin-top:8px;">
      <div class="card-title"><span>💬 评论 ({{ comments.length }})</span></div>
      <div v-if="!comments.length" class="subtle" style="text-align:center;padding:24px 0;">还没有评论，来说点什么吧～</div>
      <div v-for="(c,i) in comments" :key="i" class="row-line">
        <div style="width:36px;height:36px;border-radius:50%;background:#FFE0EC;display:flex;align-items:center;justify-content:center;font-size:18px;">{{ c.avatar || '🐰' }}</div>
        <div style="flex:1;margin:0 10px;">
          <div style="font-weight:600;">{{ c.nick || '心动用户' }} <span class="subtle" style="margin-left:8px;font-weight:normal;">{{ fmt(c.createdAt) }}</span></div>
          <div style="margin-top:4px;">{{ c.content }}</div>
        </div>
      </div>

      <div style="display:flex;gap:8px;margin-top:16px;">
        <van-field v-model="cContent" placeholder="写下你的评论..." style="flex:1;" />
        <van-button type="primary" color="#FF6B9D" round @click="addComment">发送</van-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { diaryApi, type DiaryItem } from '@/api'

const route = useRoute()
const router = useRouter()
const id = Number(route.params.id)
const data = ref<DiaryItem | null>(null)
const comments = ref<any[]>([])
const cContent = ref('')

onMounted(async () => {
  try {
    const r: any = await diaryApi.get(id)
    data.value = r
    comments.value = r.comments || []
  } catch (e) {}
})

async function addComment() {
  if (!cContent.value.trim()) return
  try {
    const c = await diaryApi.addComment(id, { content: cContent.value.trim() })
    comments.value.push({ avatar: '🐰', nick: '我', content: cContent.value, createdAt: new Date().toISOString() })
    cContent.value = ''
    showToast({ type: 'success', message: '已评论 +2💰' })
  } catch (e) {}
}

async function onDel() {
  try {
    await showConfirmDialog({ title: '删除日记', message: '确定要删除这篇日记吗？删除后不可恢复。' })
    await diaryApi.remove(id)
    showToast('已删除')
    router.back()
  } catch (e) {}
}

function fmt(t?: string) {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getMonth()+1}/${d.getDate()} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`
}
</script>