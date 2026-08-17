<template>
  <div id="layout">
    <router-view v-slot="{ Component }">
      <keep-alive :include="['Home','Record','Letters','Interactive','Settings']">
        <component :is="Component" />
      </keep-alive>
    </router-view>

    <van-tabbar v-model="active" :border="true" safe-area-inset-bottom active-color="#FF3D7F" inactive-color="#999" @change="onTab">
      <van-tabbar-item name="/app/home" class="tabbar-item">
        <template #icon>
          <van-icon name="home-o" size="22" />
        </template>
        <span>首页</span>
      </van-tabbar-item>
      <van-tabbar-item name="/app/record" class="tabbar-item">
        <template #icon>
          <van-icon name="description" size="22" />
        </template>
        <span>记录</span>
      </van-tabbar-item>
      <van-tabbar-item name="/app/letters" class="tabbar-item">
        <template #icon>
          <van-icon name="envelope-o" size="22" />
        </template>
        <span>情书</span>
      </van-tabbar-item>
      <van-tabbar-item name="/app/interactive" class="tabbar-item">
        <template #icon>
          <van-icon name="bulb-o" size="22" />
        </template>
        <span>互动</span>
      </van-tabbar-item>
      <van-tabbar-item name="/app/settings" class="tabbar-item">
        <template #icon>
          <van-icon name="user-o" size="22" />
        </template>
        <span>我的</span>
      </van-tabbar-item>
    </van-tabbar>

    <van-fab
      v-if="showBindEntry"
      v-model:show="showBindEntry"
      type="primary"
      color="#FF6B9D"
      icon="friends-o"
      text="绑定TA"
      top="80%"
      @click="$router.push('/bind')"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth.store'
import { showToast } from 'vant'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const active = ref(route.path)
watch(() => route.path, (v) => {
  if (active.value !== v) active.value = v
}, { immediate: true })

const TABS = ['/app/home', '/app/record', '/app/letters', '/app/interactive', '/app/settings']
function onTab(name: string) {
  if (!name) return
  if (TABS.includes(name) && route.path !== name) {
    try {
      router.push(name).catch((e: any) => {
        console.warn('[tabbar router.push fail]', e?.message || e)
      })
    } catch (e: any) {
      showToast(e?.message || '跳转失败')
    }
  }
  active.value = name
}

const showBindEntry = computed(() => !auth.hasCouple && ['/app/home', '/app/record', '/app/interactive'].includes(route.path))
</script>
<style>
#layout { min-height: 100vh; padding-bottom: 60px; }
.tabbar-item {
  cursor: pointer !important;
  touch-action: manipulation;
  padding: 6px 4px !important;
}
.tabbar-item .van-tabbar-item__icon,
.tabbar-item .van-tabbar-item__text {
  pointer-events: none;
}
.van-tabbar {
  z-index: 999;
  box-shadow: 0 -2px 10px rgba(255, 61, 127, 0.08);
}
</style>