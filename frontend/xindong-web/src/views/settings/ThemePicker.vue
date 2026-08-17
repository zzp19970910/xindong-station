<template>
  <div class="page">
    <van-nav-bar title="主题皮肤" left-arrow fixed placeholder @click-left="$router.back()" />

    <div class="card">
      <div class="card-title"><span>🎨 选择你们专属的主题色</span></div>
      <div class="subtle" style="margin-bottom:16px;">当前主题：<b>{{ currentName }}</b> · 选择即应用，全站生效</div>

      <div class="theme-grid">
        <div v-for="t in themes" :key="t.key" class="theme-card" @click="apply(t.key)">
          <div class="theme-preview" :style="{background:t.bg}">
            <div class="tp-top" :style="{background:t.bar}"></div>
            <div class="tp-row">
              <div class="tp-circle" :style="{background:t.dot}"></div>
              <div class="tp-lines">
                <div class="tp-line" style="width:70%;"></div>
                <div class="tp-line" style="width:40%;"></div>
              </div>
            </div>
            <div class="tp-row">
              <div class="tp-rect" :style="{background:t.dot}"></div>
              <div class="tp-rect-2"></div>
            </div>
            <div class="tp-bar" :style="{background:t.dot}"></div>
          </div>
          <div style="display:flex;align-items:center;justify-content:space-between;margin-top:8px;">
            <div style="font-weight:600;">{{ t.name }}</div>
            <van-icon v-if="currentTheme===t.key" name="success" color="#52C41A" size="18" />
          </div>
          <div class="subtle">{{ t.desc }}</div>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-title"><span>💡 主题说明</span></div>
      <div class="subtle" style="line-height:1.8;">
        · 主题应用到顶部导航栏、按钮、卡片强调色等<br/>
        · 双方选择独立，不会影响对方的视觉<br/>
        · 后续会推出节日限定主题（七夕、纪念日等）
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { showToast } from 'vant'
import { settingsApi } from '@/api'
import { useAuthStore } from '@/stores/auth.store'

const auth = useAuthStore()
const currentTheme = computed(() => auth.coupleInfo?.theme || 'default')

const themes = [
  { key: 'default', name: '经典粉', desc: '甜而不腻的恋爱主色',
    bg: '#FFF7FA', bar: 'linear-gradient(180deg, #FFE0EC 0%, #FFF7FA 100%)',
    dot: '#FF6B9D' },
  { key: 'sakura', name: '樱花烂漫', desc: '春日限定，樱花瓣飞舞',
    bg: '#FFFBFF', bar: 'linear-gradient(180deg, #FFD6EA 0%, #FFF0F7 100%)',
    dot: '#F759AB' },
  { key: 'ocean', name: '夏日海洋', desc: '清透蓝，像海边的风',
    bg: '#F0FAFF', bar: 'linear-gradient(180deg, #CDEBFF 0%, #F0FAFF 100%)',
    dot: '#1890FF' },
  { key: 'sunset', name: '日落橙', desc: '温暖的日落时分',
    bg: '#FFFBF5', bar: 'linear-gradient(180deg, #FFE0C4 0%, #FFFBF5 100%)',
    dot: '#FA8C16' },
  { key: 'forest', name: '森林绿', desc: '清新自然，氧气感',
    bg: '#F3FFF6', bar: 'linear-gradient(180deg, #CDEBD6 0%, #F3FFF6 100%)',
    dot: '#52C41A' },
  { key: 'starry', name: '星空蓝', desc: '静谧深邃的夜晚',
    bg: '#F5F6FF', bar: 'linear-gradient(180deg, #D6DEFF 0%, #F5F6FF 100%)',
    dot: '#4D51FF' }
]

const currentName = computed(() => themes.find(t => t.key === currentTheme.value)?.name || '经典粉')

onMounted(async () => {
  try {
    const saved = localStorage.getItem('xd:theme')
    if (saved) applySaved(saved)
  } catch (e) {}
})

async function apply(key: string) {
  try {
    await settingsApi.setTheme(key)
    auth.updateCouple({ theme: key })
    localStorage.setItem('xd:theme', key)
    applySaved(key)
    showToast({ type: 'success', message: '主题已应用 🎨' })
  } catch (e) {
    localStorage.setItem('xd:theme', key)
    applySaved(key)
    showToast('主题已切换')
  }
}

function applySaved(key: string) {
  const t = themes.find(x => x.key === key) || themes[0]
  const r = document.documentElement
  r.style.setProperty('--theme-main', t.dot)
  r.style.setProperty('--theme-bg', t.bg)
}
</script>

<style scoped>
.theme-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}
.theme-card {
  background: #fff;
  border-radius: 14px;
  padding: 10px;
  border: 1.5px solid #F5F5F5;
  transition: all .2s;
}
.theme-card:active { transform: scale(0.98); }
.theme-preview {
  aspect-ratio: 10/13;
  border-radius: 10px;
  padding: 6px;
  display: flex;
  flex-direction: column;
  gap: 5px;
  overflow: hidden;
}
.tp-top { height: 8%; border-radius: 4px; }
.tp-row { display: flex; align-items: center; gap: 6px; padding: 4px; background: rgba(255,255,255,0.6); border-radius: 6px; }
.tp-circle { width: 22px; height: 22px; border-radius: 50%; }
.tp-lines { flex: 1; display: flex; flex-direction: column; gap: 3px; }
.tp-line { height: 4px; background: #ddd; border-radius: 4px; }
.tp-rect { height: 18px; width: 50%; border-radius: 6px; }
.tp-rect-2 { height: 18px; width: 40%; background: #f0f0f0; border-radius: 6px; margin-left: auto; }
.tp-bar { height: 14%; border-radius: 0 0 10px 10px; margin-top: auto; }
</style>