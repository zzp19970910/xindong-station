<template>
  <div class="auth-page page" style="background: linear-gradient(160deg,#FFD5E5 0%,#FFF5F7 50%);padding:60px 24px;">
    <div style="text-align:center;margin-bottom:48px;">
      <div style="font-size:64px;">💖</div>
      <h1 style="margin:16px 0 8px;font-size:28px;color:#FF3D7F;">心动空间站</h1>
      <p style="color:#888;margin:0;">记录每一个和TA在一起的心动瞬间</p>
    </div>
    <van-cell-group inset style="border-radius:16px;overflow:hidden;">
      <van-field v-model="phone" label="手机号" placeholder="请输入手机号" maxlength="11" type="tel" />
      <van-field v-model="smsCode" label="验证码" placeholder="开发环境: 1234" center>
        <template #button>
          <van-button size="small" type="primary" plain :disabled="cd>0" @click="onSendSms">
            {{ cd>0?cd+'s后重发':'获取验证码' }}
          </van-button>
        </template>
      </van-field>
    </van-cell-group>
    <div style="margin:32px 16px 0;">
      <van-button block type="primary" color="#FF6B9D" size="large" round :loading="loading" @click="onLogin">
        登录 / 注册
      </van-button>
      <p style="font-size:12px;color:#aaa;text-align:center;margin-top:24px;">
        登录即代表同意《用户协议》《隐私政策》
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Cell, CellGroup, Field, Button, showToast } from 'vant'
import { useAuthStore } from '@/stores/auth.store'
import { authApi } from '@/api/auth.api'
import { useRouter } from 'vue-router'

const phone = ref('')
const smsCode = ref('')
const cd = ref(0)
const loading = ref(false)
const auth = useAuthStore()
const router = useRouter()

const onSendSms = async () => {
  if (!/^1[3-9]\d{9}$/.test(phone.value)) return showToast('手机号格式错误')
  try {
    await authApi.sendSms(phone.value)
    showToast('验证码已发送')
    cd.value = 60
    const timer = setInterval(() => { cd.value--; if (cd.value<=0) clearInterval(timer) }, 1000)
  } catch (e) {}
}

const onLogin = async () => {
  if (!/^1[3-9]\d{9}$/.test(phone.value)) return showToast('手机号格式错误')
  if (!smsCode.value) return showToast('请输入验证码')
  loading.value = true
  try {
    const data: any = await authApi.login(phone.value, smsCode.value).catch(async () => {
      return await authApi.register({
        phone: phone.value, smsCode: smsCode.value,
        nickname: '心动用户' + phone.value.slice(-4),
        avatarUrl: 'emoji:🌸#FFD5E5'
      })
    })
    auth.setLogin(data?.token || 'mock-token-for-dev', data?.user || { id: 1, phone: phone.value })
    if (data?.couple) auth.setCouple(data.couple)
    showToast({ type: 'success', message: '登录成功' })
    router.replace('/app/home')
  } finally { loading.value = false }
}
</script>