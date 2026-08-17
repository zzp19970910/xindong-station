<template>
  <div class="page">
    <div class="me-head">
      <div class="avatar" @click="$router.push('/app/settings/profile')">
        {{ avatarEmoji }}
      </div>
      <div style="flex:1;margin-left:16px;">
        <div style="font-size:20px;font-weight:700;">{{ user?.nickname || '心动用户' }}</div>
        <div class="subtle" style="margin-top:4px;">{{ user?.phone || '' }}</div>
        <div v-if="auth.hasCouple" style="margin-top:6px;">
          <span class="tag-pill">💞 已绑定 · 第 {{ days }} 天</span>
        </div>
        <van-button v-else size="mini" type="primary" plain round color="#FF6B9D" style="margin-top:6px;" @click="$router.push('/bind')">
          去绑定另一半
        </van-button>
      </div>
    </div>

    <div class="card coin-card">
      <div>
        <div class="subtle">💰 金币余额</div>
        <div style="font-size:36px;font-weight:800;color:#B8860B;margin-top:4px;">{{ coins }}</div>
        <div class="subtle" style="margin-top:2px;">点击查看明细 & 支出占比</div>
      </div>
      <van-button type="primary" color="#FA8C16" round size="small" icon="gold-coin-o" @click="$router.push('/app/settings/coins')">
        金币中心
      </van-button>
    </div>

    <van-cell-group inset style="margin:16px;border-radius:16px;overflow:hidden;">
      <van-cell title="恋爱周报" icon="description" is-link to="/app/settings/weekly">
        <template #value>
          <span class="tag-pill">📊 S/A/B/C评级</span>
        </template>
      </van-cell>
      <van-cell title="主题皮肤" icon="music-o" is-link to="/app/settings/theme">
        <template #value><span class="subtle">{{ themeName }}</span></template>
      </van-cell>
      <van-cell title="冷静模式" icon="shield-o" is-link to="/app/settings/cooling">
        <template #right-icon>
          <span v-if="coolingActive" style="background:#1677FF;color:#fff;padding:2px 8px;border-radius:999px;font-size:11px;">开启中</span>
        </template>
      </van-cell>
      <van-cell title="个人资料" icon="user-o" is-link to="/app/settings/profile" />
    </van-cell-group>

    <van-cell-group inset style="margin:16px;border-radius:16px;overflow:hidden;">
      <van-cell title="情侣信息" icon="friends-o" is-link to="/bind" :value="auth.hasCouple?`ID #${auth.coupleId}`:'未绑定'" />
      <van-cell title="邀请码" icon="logistics" :value="myCode" is-link>
        <template #extra>
          <van-button size="mini" type="primary" plain round color="#FF6B9D" @click.stop="copy">复制</van-button>
        </template>
      </van-cell>
      <van-cell title="金币中心" icon="gold-coin-o" :value="`💰 ${coins}`" is-link to="/app/settings/coins" />
    </van-cell-group>

    <van-cell-group inset style="margin:16px;border-radius:16px;overflow:hidden;">
      <van-cell title="关于心动空间站" icon="info-o" :value="'V1.0 FINAL'" />
      <van-cell title="用户协议 & 隐私" icon="description" is-link />
      <van-cell title="清理缓存" icon="delete-o" value="0.0MB" is-link @click="clearCache" />
      <van-cell title="退出登录" center color="#FF4D4F" @click="onLogout" />
    </van-cell-group>

    <div style="height:24px;"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { useAuthStore } from '@/stores/auth.store'
import { coupleApi, settingsApi } from '@/api'

const auth = useAuthStore()
const router = useRouter()
const user = computed(() => auth.userInfo)
const coins = computed(() => auth.coupleInfo?.coinsTotal || 0)
const coolingActive = computed(() => {
  const c = auth.coupleInfo?.coolingUntil
  return c && new Date(c).getTime() > Date.now()
})
const myCode = ref(auth.coupleInfo?.inviteCodeP1 || '--')
const days = ref(0)
const avatarBank = ['🌸', '🌼', '🍓', '🦄', '🐰', '🐻', '🌈', '⭐']
const avatarEmoji = computed(() => avatarBank[(auth.userId || 0) % avatarBank.length])

const themeMap: Record<string, string> = {
  default: '经典粉', sakura: '樱花烂漫', ocean: '夏日海洋',
  sunset: '日落橙', forest: '森林绿', starry: '星空蓝'
}
const themeName = computed(() => themeMap[auth.coupleInfo?.theme || 'default'] || '经典粉')

onMounted(async () => {
  if (!auth.hasCouple) return
  try {
    const c = await coupleApi.info(auth.coupleId)
    auth.setCouple(c)
    myCode.value = c.inviteCodeP1 || c.inviteCodeP2 || '--'
    if (c.togetherDate) {
      days.value = Math.max(0, Math.floor((Date.now() - new Date(c.togetherDate).getTime()) / 86400000))
    }
    try {
      const s = await settingsApi.coolingStatus(auth.coupleId)
      if (s.isActive !== coolingActive.value) {
        auth.updateCouple({ coolingUntil: s.coolingUntil })
      }
    } catch (e) {}
  } catch (e) {}
})

function copy() {
  navigator.clipboard?.writeText(myCode.value)
  showToast({ type: 'success', message: '邀请码已复制' })
}
async function clearCache() {
  await showConfirmDialog({ title: '提示', message: '缓存清理会退出登录吗？', showCancelButton: true })
  localStorage.removeItem('xd:cache')
  showToast('缓存已清理')
}
async function onLogout() {
  try {
    await showConfirmDialog({ title: '退出登录', message: '确定要退出吗？' })
    auth.logout()
    showToast('已退出登录')
    router.replace('/auth')
  } catch (e) {}
}
</script>

<style scoped>
.me-head {
  padding: 32px 24px 16px;
  display: flex;
  align-items: center;
  background: linear-gradient(180deg, #FFE0EC 0%, #FFF7FA 100%);
}
.avatar {
  width: 64px; height: 64px;
  border-radius: 50%;
  background: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 34px;
  box-shadow: 0 4px 12px rgba(255,107,157,0.15);
}
.coin-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(135deg, #FFF8DC 0%, #FFE4A8 100%);
}
</style>