import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { LoginResp, CoupleInfo } from '@/api/auth.api'

const LS_KEY_TOKEN = 'xd:token'
const LS_KEY_USER = 'xd:user'
const LS_KEY_COUPLE = 'xd:couple'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem(LS_KEY_TOKEN) || '')
  const userInfo = ref<any>(JSON.parse(localStorage.getItem(LS_KEY_USER) || 'null'))
  const coupleInfo = ref<CoupleInfo | null>(JSON.parse(localStorage.getItem(LS_KEY_COUPLE) || 'null'))

  const isLogin = computed(() => !!token.value)
  const hasCouple = computed(() => !!(coupleInfo.value && coupleInfo.value.id))
  const userId = computed<number>(() => userInfo.value?.id || 0)
  const coupleId = computed<number>(() => coupleInfo.value?.id || 0)

  function setLogin(t: string, user?: any) {
    token.value = t
    localStorage.setItem(LS_KEY_TOKEN, t)
    if (user) {
      userInfo.value = user
      localStorage.setItem(LS_KEY_USER, JSON.stringify(user))
      if (user.coupleId) {
        // 等接口拉couple
      }
    }
  }

  function setCouple(c: CoupleInfo | null) {
    coupleInfo.value = c
    if (c) localStorage.setItem(LS_KEY_COUPLE, JSON.stringify(c))
    else localStorage.removeItem(LS_KEY_COUPLE)
  }

  function updateCouple(patch: Partial<CoupleInfo>) {
    if (!coupleInfo.value) return
    coupleInfo.value = { ...coupleInfo.value, ...patch }
    localStorage.setItem(LS_KEY_COUPLE, JSON.stringify(coupleInfo.value))
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    coupleInfo.value = null
    localStorage.removeItem(LS_KEY_TOKEN)
    localStorage.removeItem(LS_KEY_USER)
    localStorage.removeItem(LS_KEY_COUPLE)
  }

  return {
    token,
    userInfo,
    coupleInfo,
    isLogin,
    hasCouple,
    userId,
    coupleId,
    setLogin,
    setCouple,
    updateCouple,
    logout
  }
})