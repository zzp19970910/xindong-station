<template>
  <div class="page">
    <van-nav-bar title="情书" fixed placeholder>
      <template #right>
        <van-button icon="edit" size="small" type="primary" plain round color="#FF6B9D" @click="$router.push('/app/letters/write')">
          写信
        </van-button>
      </template>
    </van-nav-bar>

    <van-tabs v-model:active="tab" sticky offset-top="46px" line-width="24px" color="#FF3D7F" @change="onTab">
      <van-tab title="收件箱" name="inbox">
        <div class="card">
          <div v-if="!inbox.length" class="subtle" style="text-align:center;padding:32px 0;">
            还没收到情书💌 <br/>点右上角写给TA 或 邀请TA写给你～
          </div>
          <div v-for="l in inbox" :key="l.id" class="letter-row" @click="openLetter(l)">
            <div style="width:44px;height:44px;border-radius:12px;background:#FFE0EC;display:flex;align-items:center;justify-content:center;font-size:22px;">
              {{ l.isRead ? '💌' : '💖' }}
            </div>
            <div style="flex:1;margin:0 12px;overflow:hidden;">
              <div style="display:flex;align-items:center;gap:6px;">
                <b>{{ l.title }}</b>
                <span v-if="!l.isRead" style="background:#FF4D4F;color:#fff;border-radius:999px;font-size:10px;padding:1px 6px;">NEW</span>
              </div>
              <div class="subtle" style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin-top:4px;">
                {{ preview(l.body) }}
              </div>
            </div>
            <div style="text-align:right;">
              <div class="subtle">{{ fmt(l.sentAt || l.createdAt) }}</div>
              <div style="font-size:11px;" :class="l.isRead ? 'subtle':'pink'">{{ l.isRead ? '已读' : '未拆封' }}</div>
            </div>
          </div>
        </div>
      </van-tab>

      <van-tab title="时光胶囊" name="capsule">
        <div style="padding:12px 16px 4px;">
          <div class="card" style="margin:0;background: linear-gradient(135deg,#E0D9FF,#FFF0F5);">
            <div style="display:flex;align-items:center;justify-content:space-between;">
              <div>
                <div style="font-weight:700;font-size:17px;">⏳ 时光胶囊</div>
                <div class="subtle" style="margin-top:4px;">写一封未来的信，定时打开</div>
              </div>
              <van-button type="primary" color="#6B5BFF" round size="small" @click="writeCapsule">写胶囊信</van-button>
            </div>
          </div>
        </div>

        <div class="card">
          <div v-if="!capsules.length" class="subtle" style="text-align:center;padding:32px 0;">
            还没有胶囊信<br/>写一封，给未来的彼此一个惊喜
          </div>
          <div v-for="c in capsules" :key="c.id" class="letter-row" @click="openCapsule(c)">
            <div style="width:44px;height:44px;border-radius:12px;background:#E0D9FF;display:flex;align-items:center;justify-content:center;font-size:22px;">
              {{ canOpen(c) ? '📭' : '🔒' }}
            </div>
            <div style="flex:1;margin:0 12px;">
              <b>{{ c.title }}</b>
              <div class="subtle" style="margin-top:4px;">
                开启时间 {{ c.capsuleOpenAt }}
              </div>
            </div>
            <div style="text-align:right;">
              <div v-if="canOpen(c)" class="pink" style="font-weight:700;">可拆封</div>
              <div v-else class="subtle">{{ left(c.capsuleOpenAt) }}</div>
            </div>
          </div>
        </div>
      </van-tab>
    </van-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { loveLetterApi, type LoveLetterItem } from '@/api'

const router = useRouter()
const tab = ref('inbox')
const inbox = ref<LoveLetterItem[]>([])
const capsules = ref<LoveLetterItem[]>([])

function onTab() { load() }

async function load() {
  try {
    const r: any = await loveLetterApi.list({ status: 'SENT', page: 1, size: 50 })
    const all = (r.list || []) as LoveLetterItem[]
    inbox.value = all.filter(l => !l.isCapsule)
    capsules.value = all.filter(l => l.isCapsule)
  } catch (e) {}
}

function openLetter(l: LoveLetterItem) { router.push(`/app/letters/${l.id}`) }
function openCapsule(c: LoveLetterItem) {
  if (!canOpen(c)) return showToast('⏳ 还没到拆封时间哦')
  router.push(`/app/letters/${c.id}`)
}
function writeCapsule() {
  sessionStorage.setItem('letter.mode', 'capsule')
  router.push('/app/letters/write')
}
onMounted(load)

function preview(s: string) {
  if (!s) return ''
  const t = s.replace(/\s+/g, ' ')
  return t.length > 30 ? t.slice(0, 30) + '...' : t
}
function fmt(t?: string) {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getMonth() + 1}-${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}
function canOpen(l: LoveLetterItem) {
  if (!l.capsuleOpenAt) return true
  return new Date(l.capsuleOpenAt).getTime() <= Date.now()
}
function left(t?: string) {
  if (!t) return ''
  const ms = new Date(t).getTime() - Date.now()
  if (ms < 0) return '可拆封'
  const day = Math.floor(ms / 86400000)
  const hr = Math.floor((ms % 86400000) / 3600000)
  if (day > 0) return `${day}天${hr}小时后`
  const min = Math.floor((ms % 3600000) / 60000)
  if (hr > 0) return `${hr}小时${min}分`
  return `${min}分钟后`
}
</script>
<style scoped>
.letter-row {
  display: flex;
  align-items: center;
  padding: 14px 0;
  border-bottom: 1px dashed #FCE4EF;
}
.letter-row:last-child { border-bottom: 0; }
</style>