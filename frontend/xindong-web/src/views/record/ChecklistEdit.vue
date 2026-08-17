<template>
  <div class="page">
    <van-nav-bar title="添加清单" left-arrow fixed placeholder @click-left="$router.back()" />

    <div class="card">
      <van-field
        v-model="form.title"
        label="事项"
        placeholder="例：一起看一场日落"
        maxlength="50"
      />
      <van-field
        v-model="form.description"
        label="描述"
        placeholder="具体怎么做？（可选）"
        maxlength="200"
        type="textarea"
        rows="2"
        autosize
      />

      <van-field label="分类" is-link>
        <template #input>
          <van-radio-group v-model="form.category" direction="horizontal">
            <van-radio name="romantic">浪漫</van-radio>
            <van-radio name="daily">日常</van-radio>
            <van-radio name="travel">旅行</van-radio>
            <van-radio name="growth">成长</van-radio>
          </van-radio-group>
        </template>
      </van-field>

      <van-field
        v-model="form.emoji"
        label="图标"
        readonly
        @click="emojiPop = true"
      >
        <template #input>
          <span v-if="form.emoji" style="font-size:22px;">{{ form.emoji }}</span>
          <span v-else class="subtle">点击选择</span>
        </template>
      </van-field>
    </div>

    <div style="padding:16px;">
      <van-button type="primary" block color="#FF6B9D" round size="large" @click="onSave">添加到清单</van-button>
    </div>

    <van-popup v-model:show="emojiPop" round position="bottom" :style="{height:'45%'}">
      <div style="padding:24px;">
        <h3 style="margin:0 0 16px;">选择一个代表图标</h3>
        <div style="display:grid;grid-template-columns:repeat(8,1fr);gap:8px;">
          <div v-for="e in emojiList" :key="e" @click="form.emoji=e;emojiPop=false"
               :style="{background:form.emoji===e?'#FFF0F5':'transparent',borderRadius:'12px'}"
               style="aspect-ratio:1;display:flex;align-items:center;justify-content:center;font-size:26px;">
            {{ e }}
          </div>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { checklistApi } from '@/api'
import { useAuthStore } from '@/stores/auth.store'

const router = useRouter()
const auth = useAuthStore()
const emojiPop = ref(false)

const form = reactive<{
  title: string; description?: string; category: string; emoji: string
}>({
  title: '',
  category: 'romantic',
  emoji: '💖'
})

const emojiList = [
  '💖','💕','💗','💘','💝','🌹','🌸','🌷',
  '🌅','🌄','🌙','⭐','🌈','☁️','🍂','❄️',
  '🍳','☕','🍰','🎂','🍜','🍷','🥂','🍦',
  '🎬','🎵','🎮','📚','🖼️','🎨','✈️','🚗',
  '🏖️','⛰️','🏠','🏕️','💃','🕺','🎤','🎧',
  '💍','👗','🎁','🛍️','💅','🤝','🤗','🙈'
]

async function onSave() {
  if (!form.title.trim()) return showToast('请填写清单事项')
  try {
    // 🔴后端真实的实现是 /checklists POST（ChecklistController.create），
    // incentive-checklists/custom 是空实现+参数不匹配（它接收String content不是对象）→ 400无反应
    const payload: any = {
      title: form.title.trim(),
      name: form.title.trim(),
      description: form.description || '',
      desc: form.description || '',
      category: form.category || 'other',
      icon: form.emoji || '💖',
      emoji: form.emoji || '💖',
      isPreset: false,
      sortOrder: Date.now()
    }
    await checklistApi.create(payload)
    showToast({ type: 'success', message: '已添加到清单 ✨' })
    router.back()
  } catch (e: any) {
    showToast(e?.message || '添加失败，请稍后重试')
  }
}
</script>