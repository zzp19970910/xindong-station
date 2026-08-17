<template>
  <div class="page bind-page" style="padding:48px 24px;">
    <div style="text-align:center;margin-bottom:40px;">
      <div style="font-size:72px;">💞</div>
      <h2 style="color:#FF3D7F;margin:8px 0;">邀请TA一起加入</h2>
      <p class="subtle" style="margin:0;">只有绑定情侣后才能解锁全部心动功能</p>
    </div>

    <div v-if="myCode" class="card" style="background: linear-gradient(135deg,#FFE0EC 0%,#FFF0F5 100%);">
      <div class="card-title">我的邀请码
        <span class="subtle">分享给TA</span>
      </div>
      <div style="text-align:center;font-size:40px;letter-spacing:8px;font-weight:700;color:#FF3D7F;user-select:all;">
        {{ myCode }}
      </div>
      <van-button block type="primary" color="#FF6B9D" plain round style="margin-top:16px;" @click="copyCode">
        复制邀请码
      </van-button>
    </div>

    <div class="card">
      <div class="card-title">我有TA的邀请码</div>
      <van-field v-model="inputCode" label="邀请码" placeholder="请输入6位邀请码" maxlength="6" center>
        <template #button>
          <van-button size="small" type="primary" color="#FF6B9D" :disabled="inputCode.length<6" @click="bind">
            绑定
          </van-button>
        </template>
      </van-field>
    </div>

    <van-button block plain type="primary" color="#999" round style="margin:32px 16px;" @click="$router.back()">
      先跳过，以后再说
    </van-button>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { showToast } from 'vant'
import { coupleApi } from '@/api'
import { useAuthStore } from '@/stores/auth.store'
import { useRouter } from 'vue-router'

const auth = useAuthStore()
const router = useRouter()
const myCode = ref('')
const inputCode = ref('')

onMounted(async () => {
  if (!auth.coupleId) return
  try {
    const resp = await coupleApi.inviteCode(auth.coupleId)
    myCode.value = resp.myCode || auth.coupleInfo?.inviteCodeP1 || ''
  } catch (e) {}
})

function copyCode() {
  navigator.clipboard?.writeText(myCode.value)
  showToast({ type: 'success', message: '邀请码已复制' })
}

async function bind() {
  if (inputCode.value.length < 6) return showToast('请输入6位邀请码')
  try {
    const c = await coupleApi.bind(inputCode.value.trim().toUpperCase())
    auth.setCouple(c)
    showToast({ type: 'success', message: '绑定成功！🎉' })
    setTimeout(() => router.replace('/app/home'), 800)
  } catch (e) {}
}
</script>