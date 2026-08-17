<template>
  <div class="page">
    <van-nav-bar title="个人资料" left-arrow fixed placeholder @click-left="$router.back()" />

    <div style="padding:24px 16px;text-align:center;">
      <div class="avatar-wrap" @click="avatarPop = true">
        <div class="avatar">{{ form.avatarUrl ? '' : avatarChar }}</div>
        <van-icon name="photograph" size="16" color="#fff" class="camera" />
      </div>
      <div class="subtle" style="margin-top:10px;">点击更换头像 (预置 emoji)</div>
    </div>

    <div class="card">
      <van-field v-model="form.nickname" label="昵称" placeholder="给TA一个温柔的昵称" maxlength="20" />
      <van-field name="gender" label="性别">
        <template #input>
          <van-radio-group v-model="form.gender" direction="horizontal">
            <van-radio name="F">👩 女生</van-radio>
            <van-radio name="M">👨 男生</van-radio>
          </van-radio-group>
        </template>
      </van-field>
      <van-field
        v-model="form.birthday"
        label="生日"
        placeholder="选择生日（可选）"
        readonly
        @click="showBirth = true"
      >
        <template #right-icon><van-icon name="arrow" /></template>
      </van-field>
      <van-calendar v-model:show="showBirth" type="single" :color="'#FF6B9D'" @confirm="onBirth" />
    </div>

    <div class="card">
      <div class="card-title"><span>📋 账号信息</span></div>
      <van-cell title="用户 ID" :value="`#${auth.userId || '--'}`" />
      <van-cell title="手机号" :value="phoneMask" />
      <van-cell title="注册时间" :value="regDate" />
    </div>

    <div style="padding:16px;">
      <van-button type="primary" block color="#FF6B9D" round size="large" :loading="saving" @click="onSave">保存修改</van-button>
    </div>

    <van-popup v-model:show="avatarPop" round position="bottom" :style="{height:'55%'}">
      <div style="padding:20px 24px;">
        <h3 style="margin:0 0 16px;">选择头像 emoji</h3>
        <div style="display:grid;grid-template-columns:repeat(7,1fr);gap:8px;">
          <div v-for="(a,i) in avatars" :key="i" @click="pickAvatar(a)"
               :style="{background:form.avatarUrl===a?'#FFF0F5':'transparent',borderRadius:'50%',border:`2px solid ${form.avatarUrl===a?'#FF6B9D':'transparent'}`}"
               style="aspect-ratio:1;display:flex;align-items:center;justify-content:center;font-size:28px;">
            {{ a }}
          </div>
        </div>
        <van-button block color="#FF6B9D" round style="margin-top:20px;" @click="avatarPop=false">确定</van-button>
      </div>
    </van-popup>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { showToast } from 'vant'
import { settingsApi, type SettingsProfile } from '@/api'
import { useAuthStore } from '@/stores/auth.store'

const auth = useAuthStore()
const saving = ref(false)
const showBirth = ref(false)
const avatarPop = ref(false)

const form = reactive<SettingsProfile & { avatarUrl?: string }>({
  nickname: auth.userInfo?.nickname || '',
  avatarUrl: auth.userInfo?.avatarUrl || '🌸',
  gender: (auth.userInfo?.gender as 'F'|'M') || undefined,
  birthday: auth.userInfo?.birthday || ''
})

const avatars = [
  '🌸','🌼','🌺','🌻','🌹','🌷','🍀',
  '🍓','🍎','🍑','🍒','🥝','🍋','🍉',
  '🐰','🐻','🐱','🐶','🦊','🐼','🐨',
  '🦄','🌈','⭐','🌟','✨','🌙','☀️',
  '🎀','💎','💖','💕','🎵','🎮','🍰'
]
function pickAvatar(a: string) { form.avatarUrl = a }
const avatarChar = computed(() => form.avatarUrl || '🌸')

const phoneMask = computed(() => {
  const p = auth.userInfo?.phone || ''
  if (p.length >= 11) return p.slice(0, 3) + '****' + p.slice(7)
  return p || '--'
})
const regDate = computed(() => {
  const t = auth.userInfo?.createdAt
  if (!t) return '--'
  const d = new Date(t)
  return `${d.getFullYear()}/${d.getMonth()+1}/${d.getDate()}`
})

onMounted(async () => {
  try {
    if (!auth.coupleId) return
    const r = await settingsApi.me(auth.coupleId) as any
    if (r?.profile) Object.assign(form, r.profile)
  } catch (e) {}
})

function onBirth(d: Date[]) {
  const x = d[0]
  form.birthday = `${x.getFullYear()}-${String(x.getMonth()+1).padStart(2,'0')}-${String(x.getDate()).padStart(2,'0')}`
  showBirth.value = false
}

async function onSave() {
  saving.value = true
  try {
    await settingsApi.setProfile({ ...form })
    auth.userInfo = { ...auth.userInfo, ...form }
    localStorage.setItem('xd:user', JSON.stringify(auth.userInfo))
    showToast({ type: 'success', message: '资料已保存' })
  } catch (e) {
    showToast('已保存到本地')
    auth.userInfo = { ...auth.userInfo, ...form }
    localStorage.setItem('xd:user', JSON.stringify(auth.userInfo))
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.avatar-wrap {
  width: 96px;
  height: 96px;
  margin: 0 auto;
  border-radius: 50%;
  background: linear-gradient(135deg, #FF6B9D, #FFB3D1);
  position: relative;
  padding: 3px;
  box-shadow: 0 6px 16px rgba(255, 107, 157, 0.2);
  cursor: pointer;
}
.avatar {
  width: 100%; height: 100%;
  border-radius: 50%;
  background: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 44px;
}
.camera {
  position: absolute; bottom: 4px; right: 4px;
  background: #FF6B9D;
  padding: 4px;
  border-radius: 50%;
}
</style>