<template>
  <div class="page">
    <van-nav-bar title="心愿商城" left-arrow fixed placeholder @click-left="$router.back()">
      <template #right>
        <div style="margin-right:12px;display:flex;align-items:center;gap:8px;">
          <span class="coin-tag">💰 {{ coins }}</span>
          <van-button size="mini" type="primary" plain round color="#FF6B9D" @click="editWish()">+ 新心愿</van-button>
        </div>
      </template>
    </van-nav-bar>

    <van-tabs v-model:active="tab" sticky offset-top="46px" line-width="24px" color="#FF3D7F">
      <van-tab title="全部" name="all">
        <div class="card-list">
          <div v-for="w in list" :key="w.id" class="wish-card" @click="open(w)">
            <div class="wish-icon" :style="{background:iconBg(w.status)}">{{ w.emoji || '🎁' }}</div>
            <div style="flex:1;margin:0 12px;min-width:0;">
              <div style="display:flex;align-items:center;gap:6px;">
                <b style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">{{ w.title }}</b>
                <van-tag size="medium" :color="tagColor(w.status)" :type="w.status==='APPROVED'||w.status==='COMPLETED'?'primary':'default'" round>
                  {{ statusLabel(w.status) }}
                </van-tag>
              </div>
              <div class="subtle" style="margin-top:4px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">
                {{ w.description || '一起实现这个心愿吧' }}
              </div>
              <van-progress
                v-if="w.steps?.length"
                :percentage="stepPct(w)"
                color="#FF6B9D"
                style="margin-top:8px;"
                stroke-width="4"
              />
            </div>
            <div style="text-align:right;">
              <div style="color:#B8860B;font-weight:800;font-size:17px;">💰 {{ w.cost }}</div>
              <div class="subtle" style="margin-top:2px;">已存 {{ w.deposit || 0 }}</div>
            </div>
          </div>
          <div v-if="!list.length" class="subtle" style="text-align:center;padding:60px 0;">
            还没有心愿<br/>来添加第一个吧 ✨
          </div>
        </div>
      </van-tab>

      <van-tab title="待我审批" name="pend">
        <div class="card-list">
          <div v-for="w in pends" :key="w.id" class="wish-card" @click="open(w)">
            <div class="wish-icon" style="background:#FFF2CC;">{{ w.emoji || '⏳' }}</div>
            <div style="flex:1;margin:0 12px;">
              <b>{{ w.title }}</b>
              <div class="subtle" style="margin-top:4px;">由 P{{ w.createdById===1?1:2 }} 发起，等待你的批准</div>
            </div>
            <van-tag color="#FF7A45" round>待批</van-tag>
          </div>
          <div v-if="!pends.length" class="subtle" style="text-align:center;padding:60px 0;">暂无等待审批的心愿</div>
        </div>
      </van-tab>

      <van-tab title="已完成" name="done">
        <div class="card-list">
          <div v-for="w in dones" :key="w.id" class="wish-card" @click="open(w)">
            <div class="wish-icon" style="background:#D9F7E2;">{{ w.emoji || '✅' }}</div>
            <div style="flex:1;margin:0 12px;">
              <b>{{ w.title }}</b>
              <div class="subtle" style="margin-top:4px;">{{ fmt(w.completedAt || w.createdAt) }} · 已兑现 🎉</div>
            </div>
            <div class="green" style="font-weight:700;">+{{ w.cost }}💰</div>
          </div>
          <div v-if="!dones.length" class="subtle" style="text-align:center;padding:60px 0;">还没有已完成的心愿</div>
        </div>
      </van-tab>
    </van-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { wishApi, type WishItem } from '@/api'
import { useAuthStore } from '@/stores/auth.store'

const auth = useAuthStore()
const router = useRouter()
const tab = ref('all')
const all = ref<WishItem[]>([])
const coins = computed(() => auth.coupleInfo?.coinsTotal || 0)

const list = computed(() => all.value.filter(w => w.status !== 'COMPLETED'))
const pends = computed(() => all.value.filter(w => w.status === 'PENDING_APPROVAL'))
const dones = computed(() => all.value.filter(w => w.status === 'COMPLETED'))

onMounted(load)
async function load() {
  try {
    const r: any = await wishApi.list({ size: 100 })
    all.value = r.list || []
  } catch (e) {}
}

function open(w: WishItem) {
  sessionStorage.setItem('wish.detail', JSON.stringify(w))
  router.push(`/app/interactive/wishes/${w.id}`)
}
function editWish(w?: WishItem) {
  sessionStorage.setItem('wish.edit', JSON.stringify(w || {}))
  router.push('/app/interactive/wishes/edit')
}

function stepPct(w: WishItem) {
  if (!w.steps?.length) return 0
  const done = w.steps.filter(s => s.done).length
  return Math.round(done * 100 / w.steps.length)
}

function statusLabel(s: string) {
  return ({ DRAFT: '草稿', PENDING_APPROVAL: '审批中', APPROVED: '进行中', COMPLETED: '已完成', REJECTED: '已拒绝' } as any)[s] || s
}
function iconBg(s: string) {
  return ({ DRAFT: '#F5F5F5', PENDING_APPROVAL: '#FFF2CC', APPROVED: '#FFE0EC', COMPLETED: '#D9F7E2', REJECTED: '#FFE0E0' } as any)[s] || '#eee'
}
function tagColor(s: string) {
  return ({ DRAFT: '#999', PENDING_APPROVAL: '#FF7A45', APPROVED: '#FF6B9D', COMPLETED: '#52C41A', REJECTED: '#FF4D4F' } as any)[s] || '#ccc'
}
function fmt(t?: string) {
  if (!t) return ''
  const d = new Date(t)
  return `${d.getMonth()+1}月${d.getDate()}日`
}
</script>

<style scoped>
.card-list { padding: 12px 16px; }
.wish-card {
  display: flex;
  align-items: center;
  background: #fff;
  border-radius: 16px;
  padding: 14px;
  margin-bottom: 10px;
  box-shadow: 0 2px 8px rgba(255,107,157,0.05);
}
.wish-icon {
  width: 48px; height: 48px;
  border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  font-size: 24px;
  flex-shrink: 0;
}
</style>