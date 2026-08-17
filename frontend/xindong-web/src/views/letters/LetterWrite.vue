<template>
  <div class="page" style="background: linear-gradient(180deg, #FFF0F5 0%, #FFF 20%);">
    <van-nav-bar :title="isCapsule ? '写胶囊信' : '写情书'" left-arrow fixed placeholder @click-left="$router.back()">
      <template #right>
        <span :style="{color:saving?'#ccc':'#FF6B9D',fontWeight:'600',paddingRight:'12px'}" @click="onSend">
          {{ saving ? '发送中...' : '发送' }}
        </span>
      </template>
    </van-nav-bar>

    <div style="padding:20px 16px;">
      <div v-if="isCapsule" class="card" style="background:linear-gradient(135deg,#E0D9FF,#FFF0F5);margin-bottom:20px;">
        <div style="display:flex;align-items:center;justify-content:space-between;">
          <div>
            <div style="font-weight:700;">⏳ 时光胶囊</div>
            <div class="subtle" style="margin-top:4px;">选择未来拆封这封信的时间</div>
          </div>
        </div>
        <van-cell-group inset style="margin-top:12px;border-radius:12px;overflow:hidden;">
          <van-cell title="开启时间" :value="form.capsuleOpenAt || '请选择'" is-link @click="showDate = true" />
        </van-cell-group>
        <van-calendar
          v-model:show="showDate"
          type="single"
          :min-date="tomorrow"
          :color="'#6B5BFF'"
          @confirm="onDate"
        />
      </div>

      <div class="card" style="box-shadow:0 4px 20px rgba(255,107,157,0.1);">
        <van-field
          v-model="form.title"
          placeholder="给这封信起个名字..."
          maxlength="40"
          :border="false"
          style="font-size:18px;font-weight:700;"
        />

        <van-field
          v-model="form.body"
          type="textarea"
          rows="18"
          autosize
          :placeholder="placeholder"
          maxlength="8000"
          :border="false"
          style="line-height:2;font-size:15px;"
        />

        <div style="margin-top:16px;padding:12px;background:#FFFAFC;border-radius:10px;border-left:3px solid #FF6B9D;">
          <div style="font-size:12px;color:#666;line-height:1.8;">
            💡 {{ isCapsule ? 'AES-256-GCM 加密保存，到开启时间前任何人（包括TA）都无法看到内容。' : '信件内容已加密存储，仅彼此可见。发送即获得 +15 金币。' }}
          </div>
        </div>
      </div>

      <div style="text-align:center;margin:32px 0;color:#bbb;font-size:12px;">— to my dearest 💕 —</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { loveLetterApi } from '@/api'
import { useAuthStore } from '@/stores/auth.store'

const router = useRouter()
const auth = useAuthStore()
const isCapsule = computed(() => sessionStorage.getItem('letter.mode') === 'capsule')
const saving = ref(false)
const showDate = ref(false)
const tomorrow = new Date(Date.now() + 86400000)

const placeholder = isCapsule.value
  ? '写给未来的你们：\n比如一年后的今天，打开这封信时你们会在做什么？\n对那时的TA说点什么...'
  : '亲爱的：\n今天我想告诉你...\n\n（可以是任何想对TA说的话，小感动、小感谢、小告白都可以）'

const form = reactive<{
  title: string; body: string; receiverId: number;
  isCapsule: boolean; capsuleOpenAt?: string
}>({
  title: '',
  body: '',
  receiverId: 0,
  isCapsule: isCapsule.value,
  capsuleOpenAt: undefined
})

onMounted(() => {
  const partnerId = Number(sessionStorage.getItem('partner.id') || '0')
  form.receiverId = partnerId || (auth.userId === 1 ? 2 : 1)
})

function onDate(d: Date[]) {
  const x = d[0]
  form.capsuleOpenAt = `${x.getFullYear()}-${String(x.getMonth()+1).padStart(2,'0')}-${String(x.getDate()).padStart(2,'0')}T08:00:00`
  showDate.value = false
}

async function onSend() {
  if (!form.title.trim()) return showToast('给信起个标题吧')
  if (!form.body.trim()) return showToast('写点内容再发送呀')
  if (form.isCapsule && !form.capsuleOpenAt) return showToast('请选择胶囊开启时间')
  saving.value = true
  try {
    await loveLetterApi.create({
      title: form.title,
      body: form.body,
      receiverId: form.receiverId,
      isCapsule: form.isCapsule,
      capsuleOpenAt: form.capsuleOpenAt
    })
    sessionStorage.removeItem('letter.mode')
    showToast({ type: 'success', message: isCapsule.value ? '胶囊已封存 ⏳' : '已送达TA的信箱 💌' })
    router.back()
  } catch (e) {
  } finally {
    saving.value = false
  }
}
</script>