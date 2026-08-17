<template>
  <div class="page">
    <van-nav-bar title="写日记" left-arrow fixed placeholder @click-left="$router.back()">
      <template #right>
        <span :style="{color:saving?'#ccc':'#FF6B9D',fontWeight:'600',paddingRight:'12px'}" @click="onSave">
          {{ saving ? '保存中...' : '发布' }}
        </span>
      </template>
    </van-nav-bar>

    <div class="card">
      <div style="display:flex;align-items:center;gap:12px;margin-bottom:16px;">
        <div class="subtle">今日心情：</div>
        <div v-for="e in moods" :key="e" @click="form.moodEmoji=e"
             :style="{border:`2px solid ${form.moodEmoji===e?'#FF6B9D':'transparent'}`,background:form.moodEmoji===e?'#FFF0F5':'#fff'}"
             style="width:40px;height:40px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:24px;">
          {{ e }}
        </div>
      </div>

      <van-field
        v-model="form.title"
        placeholder="给今天取个小标题（可选）"
        maxlength="50"
        :border="false"
        style="font-size:18px;font-weight:700;"
      />

      <van-field
        v-model="form.content"
        type="textarea"
        rows="16"
        autosize
        placeholder="今天发生了什么心动的事？\n写下来，以后一起慢慢看..."
        maxlength="5000"
        :border="false"
        style="line-height:1.8;"
      />

      <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin-top:16px;">
        <div v-for="(img,i) in form.images" :key="i" style="aspect-ratio:1;background:#FFE0EC;border-radius:8px;display:flex;align-items:center;justify-content:center;position:relative;">
          🖼️
          <van-icon name="cross" size="18" color="#fff" :style="{position:'absolute',top:'-6px',right:'-6px',background:'#FF4D4F',borderRadius:'50%',padding:'4px'}" @click="form.images?.splice(i,1)" />
        </div>
        <div v-if="(form.images?.length||0) < 6" style="aspect-ratio:1;background:#FFF;border:2px dashed #FFD6E7;border-radius:8px;display:flex;align-items:center;justify-content:center;color:#FF6B9D;flex-direction:column;" @click="addImg">
          <van-icon name="photograph" size="24" />
          <span style="font-size:11px;margin-top:4px;">加图片</span>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="subtle" style="margin-bottom:8px;">💡 写日记可获得 +8 金币</div>
      <div class="subtle">
        - 支持 Markdown 风格简单格式<br/>
        - 仅彼此可见，安心写下心动
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { diaryApi } from '@/api'

const router = useRouter()
const saving = ref(false)
const moods = ['😍','🥰','😊','🙂','😌','😐','🥺','😢','😤','🤔']

const form = reactive<{
  title?: string; content: string; moodEmoji: string; images: string[]
}>({
  title: '', content: '', moodEmoji: '😊', images: []
})

function addImg() {
  form.images.push(`mock://${Date.now()}_${Math.random().toString(36).slice(2,7)}`)
}

async function onSave() {
  if (!form.content.trim()) return showToast('写点内容再发布吧')
  saving.value = true
  try {
    await diaryApi.create({
      title: form.title || undefined,
      content: form.content,
      moodEmoji: form.moodEmoji,
      images: form.images.length ? form.images : undefined
    })
    showToast({ type: 'success', message: '发布成功！+8💰' })
    router.back()
  } catch (e) {
  } finally {
    saving.value = false
  }
}
</script>