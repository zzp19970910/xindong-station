<template>
  <div class="page">
    <van-nav-bar title="编辑纪念日" left-arrow fixed placeholder @click-left="$router.back()" />

    <div class="card">
      <van-field
        v-model="form.title"
        label="标题"
        placeholder="例：恋爱纪念日"
        maxlength="20"
      />
      <van-field
        v-model="form.targetDate"
        label="日期"
        placeholder="选择日期"
        readonly
        @click="showDate = true"
      >
        <template #right-icon><van-icon name="arrow" /></template>
      </van-field>
      <van-calendar v-model:show="showDate" :color="'#FF6B9D'" type="single" @confirm="onDate" />

      <van-cell-group inset style="margin:16px 0;padding:0;">
        <van-field name="type" label="类型">
          <template #input>
            <van-radio-group v-model="form.type" direction="horizontal">
              <van-radio name="love">恋爱</van-radio>
              <van-radio name="birthday">生日</van-radio>
              <van-radio name="anniversary">周年</van-radio>
              <van-radio name="travel">旅行</van-radio>
              <van-radio name="other">其他</van-radio>
            </van-radio-group>
          </template>
        </van-field>
      </van-cell-group>

      <van-field
        v-model="form.emoji"
        label="图标"
        placeholder="选一个可爱的 emoji"
        readonly
        @click="emojiPop = true"
      >
        <template #input>
          <span v-if="form.emoji" style="font-size:22px;">{{ form.emoji }}</span>
          <span v-else class="subtle">点击选择</span>
        </template>
      </van-field>

      <van-field
        v-model="form.note"
        label="备注"
        placeholder="对你们特别的意义（可选）"
        type="textarea"
        rows="2"
        autosize
        maxlength="100"
      />

      <van-cell title="置顶显示" is-link>
        <template #right-icon>
          <van-switch v-model="form.isTop" :active-color="'#FF6B9D'" />
        </template>
      </van-cell>

      <van-cell title="倒计时模式" :value="form.displayMode==='countup'?'已过天数':'倒数天数'" is-link>
        <template #right-icon>
          <van-switch v-model="modeUp" :active-color="'#FF6B9D'" />
        </template>
      </van-cell>
    </div>

    <div style="padding:16px;">
      <van-button v-if="id" type="danger" block plain round color="#FF4D4F" style="margin-bottom:12px;" @click="onDel">删除此纪念日</van-button>
      <van-button type="primary" block color="#FF6B9D" round size="large" @click="onSave">保存</van-button>
    </div>

    <van-popup v-model:show="emojiPop" round position="bottom" :style="{height:'40%'}">
      <div style="padding:24px;">
        <h3 style="margin:0 0 16px;">选择图标</h3>
        <div style="display:grid;grid-template-columns:repeat(8,1fr);gap:8px;">
          <div v-for="e in emojiList" :key="e" @click="form.emoji=e;emojiPop=false"
               :style="{background:form.emoji===e?'#FFF0F5':'transparent',borderRadius:'12px'}"
               style="aspect-ratio:1;display:flex;align-items:center;justify-content:center;font-size:28px;">
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
import { anniversaryApi, type AnniversaryItem } from '@/api'

const router = useRouter()
const id = computed<number | undefined>(() => {
  const raw = sessionStorage.getItem('anniv.edit')
  if (!raw) return undefined
  const obj = JSON.parse(raw)
  return obj.id || undefined
})
const editing = computed<AnniversaryItem | null>(() => {
  const raw = sessionStorage.getItem('anniv.edit')
  if (!raw) return null
  try { return JSON.parse(raw) } catch { return null }
})

const form = reactive<Partial<AnniversaryItem>>({
  title: '',
  targetDate: '',
  type: 'love',
  emoji: '🎉',
  isTop: false,
  displayMode: 'countdown',
  note: ''
})

const modeUp = ref(false)
const showDate = ref(false)
const emojiPop = ref(false)
const emojiList = ['🎉','💖','🎂','✈️','🏖️','🌹','💍','🎁','🌟','🐾','☕','🍕','🎬','🎮','🎵','📸','🌙','☀️','🌈','⭐','🎄','🎃','🥂','🏠']

onMounted(() => {
  if (editing.value) {
    Object.assign(form, editing.value)
    modeUp.value = form.displayMode === 'countup'
  }
})

function onDate(d: any) {
  const x: Date = Array.isArray(d) ? d[0] : d
  if (!x) return
  form.targetDate = `${x.getFullYear()}-${String(x.getMonth()+1).padStart(2,'0')}-${String(x.getDate()).padStart(2,'0')}`
  showDate.value = false
}

async function onSave() {
  if (!form.title) return showToast('请填写标题')
  if (!form.targetDate) return showToast('请选择日期')
  form.displayMode = modeUp.value ? 'countup' : 'countdown'
  try {
    if (id.value) await anniversaryApi.update(id.value, form)
    else await anniversaryApi.create(form as any)
    showToast({ type: 'success', message: '已保存' })
    sessionStorage.removeItem('anniv.edit')
    router.back()
  } catch (e) {}
}

async function onDel() {
  if (!id.value) return
  try {
    await showConfirmDialog({ title: '确认删除', message: '要删除这个纪念日吗？' })
    await anniversaryApi.remove(id.value)
    showToast('已删除')
    sessionStorage.removeItem('anniv.edit')
    router.back()
  } catch (e) {}
}
</script>