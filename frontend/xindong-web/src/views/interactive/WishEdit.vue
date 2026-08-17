<template>
  <div class="page">
    <van-nav-bar :title="id ? '编辑心愿' : '新建心愿'" left-arrow fixed placeholder @click-left="$router.back()" />

    <div class="card">
      <van-field v-model="form.title" label="心愿标题" placeholder="例：一起去海边" maxlength="30" />
      <van-field v-model="form.emoji" label="图标" readonly @click="emojiPop = true">
        <template #input>
          <span v-if="form.emoji" style="font-size:22px;">{{ form.emoji }}</span>
          <span v-else class="subtle">点击选择</span>
        </template>
      </van-field>
      <van-field v-model="form.description" label="描述" placeholder="具体是什么心愿？" type="textarea" rows="2" autosize maxlength="200" />
      <van-field label="所需金币" is-link>
        <template #input>
          <van-stepper v-model="form.cost" :min="5" :max="1000" :step="5" />
        </template>
      </van-field>
    </div>

    <div class="card">
      <div class="card-title">
        <span>🧩 达成步骤（可选）</span>
        <span class="pink" @click="addStep">+ 添加步骤</span>
      </div>
      <div v-if="!form.steps?.length" class="subtle" style="text-align:center;padding:24px 0;">
        可以把心愿拆分成小步骤，一步步完成更有成就感哦
      </div>
      <div v-for="(s,i) in form.steps" :key="i" style="display:flex;gap:8px;align-items:center;padding:8px 0;">
        <van-checkbox v-model="s.done" shape="square" :checked-color="'#FF6B9D'" />
        <van-field v-model="s.title" placeholder="步骤描述" style="flex:1;" />
        <van-icon name="delete-o" color="#FF4D4F" size="20" @click="form.steps!.splice(i,1)" />
      </div>
    </div>

    <div style="padding:16px;">
      <van-button v-if="id" type="danger" block plain round color="#FF4D4F" style="margin-bottom:12px;" @click="onDel">删除此心愿</van-button>
      <van-button type="primary" block color="#FF6B9D" round size="large" @click="onSave">保存心愿</van-button>
    </div>

    <van-popup v-model:show="emojiPop" round position="bottom" :style="{height:'45%'}">
      <div style="padding:24px;">
        <h3 style="margin:0 0 16px;">选择图标</h3>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { wishApi, coinApi, type WishItem } from '@/api'
import { useAuthStore } from '@/stores/auth.store'

const router = useRouter()
const auth = useAuthStore()
const emojiPop = ref(false)

const editing = computed<WishItem | null>(() => {
  try { return JSON.parse(sessionStorage.getItem('wish.edit') || 'null') } catch { return null }
})
const id = computed(() => editing.value?.id)

const form = reactive<Partial<WishItem>>({
  title: '',
  description: '',
  emoji: '🎁',
  cost: 100,
  steps: [],
  deposit: 0
})

const emojiList = [
  '🎁','💝','🌹','🌺','🥂','🍷','🎂','🍰',
  '✈️','🏖️','⛰️','🏔️','🎡','🎢','🎠','🏰',
  '💍','👗','👠','⌚','📱','💻','🎮','🎧',
  '🎬','🎵','📖','🎨','🖼️','🎸','🥁','🎤',
  '🍜','🍣','🍱','🍕','🍔','☕','🧋','🍺',
  '💈','💅','🧖','🧘','🏃','🚴','🏸','🏀'
]

onMounted(() => {
  if (editing.value) {
    const raw = JSON.parse(JSON.stringify(editing.value))
    // 🔴后端返回的steps是[{name,done}]，前端WishEdit循环绑的是steps[i].title → 映射对齐
    if (Array.isArray(raw.steps)) {
      raw.steps = raw.steps.map((s: any) => ({
        title: s?.title ?? s?.name ?? '',
        done: !!s?.done,
        name: s?.name ?? s?.title ?? ''
      }))
    }
    Object.assign(form, raw)
    if (!form.emoji) form.emoji = '🎁'
  }
})

function addStep() {
  if (!form.steps) form.steps = []
  form.steps.push({ title: '', done: false })
}

function buildStepsPayload() {
  return (form.steps || []).map((s: any) => ({
    name: s?.title ?? s?.name ?? '',
    done: !!s?.done
  }))
}

async function onSave() {
  if (!form.title?.trim()) return showToast('请填写心愿标题')
  if (auth.coupleId) {
    try {
      const ov: any = await coinApi.overview(auth.coupleId)
      if ((ov?.total ?? 0) < (form.cost ?? 0)) {
        return showToast(`金币不足：当前${ov.total || 0} / 需${form.cost}，去赚金币吧～`)
      }
    } catch (_) {}
  }
  const payload: any = {
    ...form,
    steps: buildStepsPayload(),
    coverImg: form.emoji
  }
  try {
    if (id.value) await wishApi.update(id.value, payload)
    else await wishApi.create(payload)
    showToast({ type: 'success', message: '心愿已保存' })
    sessionStorage.removeItem('wish.edit')
    router.back()
  } catch (e: any) {
    showToast(e?.message || e?.msg || e || '保存失败，请重试')
  }
}

async function onDel() {
  if (!id.value) return
  try {
    await showConfirmDialog({ title: '删除心愿', message: '确定删除这个心愿吗？' })
    await wishApi.remove(id.value)
    showToast('已删除')
    sessionStorage.removeItem('wish.edit')
    router.back()
  } catch (e: any) {
    showToast(e?.message || '删除失败')
  }
}
</script>