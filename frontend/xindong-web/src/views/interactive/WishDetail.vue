<template>
  <div class="page">
    <van-nav-bar title="心愿详情" left-arrow fixed placeholder @click-left="$router.back()" />

    <div v-if="w" style="padding:16px;">
      <div class="card" :style="{background:headBg}">
        <div style="display:flex;align-items:flex-start;gap:16px;">
          <div style="width:64px;height:64px;border-radius:16px;background:#fff;display:flex;align-items:center;justify-content:center;font-size:36px;box-shadow:0 4px 12px rgba(0,0,0,0.05);">
            {{ w.emoji || '🎁' }}
          </div>
          <div style="flex:1;min-width:0;">
            <div style="display:flex;align-items:center;gap:6px;flex-wrap:wrap;">
              <b style="font-size:20px;">{{ w.title || w.name }}</b>
              <van-tag size="medium" :color="tagColor" :type="w.status==='APPROVED'||w.status==='COMPLETED'?'primary':'default'" round>
                {{ statusLabel }}
              </van-tag>
            </div>
            <div style="color:#555;margin-top:8px;line-height:1.6;">{{ w.description || w.desc || '一起加油！' }}</div>
            <div style="display:flex;gap:8px;margin-top:10px;flex-wrap:wrap;">
              <span class="tag-pill" style="background:#FFF2CC;color:#B8860B;font-weight:700;">💰 {{ safeNum(w.cost) }}</span>
              <span class="tag-pill">已存 {{ savedCoins }}</span>
              <span class="tag-pill">{{ progressPct }}%</span>
            </div>
          </div>
        </div>

        <van-progress :percentage="progressPct" color="#FF6B9D" stroke-width="8" style="margin-top:20px;border-radius:999px;overflow:hidden;" />

        <div v-if="w.rejectedReason" style="margin-top:16px;padding:12px;background:#FFEDED;border-radius:10px;">
          <div style="color:#FF4D4F;font-weight:600;">❌ 已拒绝理由：</div>
          <div style="margin-top:4px;">{{ w.rejectedReason }}</div>
        </div>
      </div>

      <div v-if="w.steps?.length" class="card">
        <div class="card-title"><span>🧩 达成步骤</span><span class="subtle">{{ stepsDone }}/{{ w.steps.length }}</span></div>
        <div v-for="(s,i) in w.steps" :key="i"
             class="row-line step-row"
             :class="{ clickable: canToggleStep }"
             @click="toggleStepByRow(i)">
          <van-checkbox :model-value="!!s.done" shape="square" :icon-size="22" :checked-color="'#FF6B9D'"
                        :disabled="!canToggleStep"
                        @change="toggleStep(i, $event)" />
          <div :style="{flex:'1',textDecoration:s.done?'line-through':'none',color:s.done?'#aaa':'#333',marginLeft:'12px'}">
            <span style="color:#FF6B9D;font-weight:700;margin-right:6px;">#{{ i+1 }}</span>
            {{ s.title || s.name || `步骤 ${i+1}` }}
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-title"><span>📋 流程</span></div>
        <van-steps :active="stepActive" icon-prefix="van-icon" style="padding:14px 0;">
          <van-step>
            <template #active-icon>✅</template>
            <template #inactive-icon>1</template>
            <div class="step-title">创建心愿</div>
            <div class="step-desc">填写心愿内容</div>
          </van-step>
          <van-step>
            <template #active-icon>✅</template>
            <template #inactive-icon>2</template>
            <div class="step-title">提交审批</div>
            <div class="step-desc">等待TA批准</div>
          </van-step>
          <van-step>
            <template #active-icon>✅</template>
            <template #inactive-icon>3</template>
            <div class="step-title">批准通过</div>
            <div class="step-desc">开始一步步完成</div>
          </van-step>
          <van-step>
            <template #active-icon>✅</template>
            <template #inactive-icon>4</template>
            <div class="step-title">积累金币</div>
            <div class="step-desc">存入需要的金币</div>
          </van-step>
          <van-step>
            <template #active-icon>🎉</template>
            <template #inactive-icon>5</template>
            <div class="step-title">完成兑现</div>
            <div class="step-desc">一起实现心愿</div>
          </van-step>
        </van-steps>
      </div>

      <div class="card">
        <div class="card-title"><span>💡 操作</span></div>

        <van-button v-if="w.status==='DRAFT'" block type="primary" color="#FF6B9D" round style="margin-bottom:10px;" @click="doApply">
          提交给 TA 审批 →
        </van-button>
        <van-button v-if="w.status==='DRAFT'" block plain round color="#FF6B9D" @click="edit">编辑心愿</van-button>

        <div v-if="w.status==='PENDING_APPROVAL'" style="display:flex;gap:10px;">
          <van-button block type="primary" color="#52C41A" round icon="checked" @click="doApprove">批准</van-button>
          <van-button block type="danger" plain round color="#FF4D4F" icon="close" @click="doReject">拒绝</van-button>
        </div>

        <van-button v-if="w.status==='APPROVED' && allStepsDone" block type="primary" color="#52C41A" round size="large" @click="doComplete">
          🎉 心愿已全部达成，申请兑换
        </van-button>

        <van-button v-if="w.status!=='COMPLETED'" block plain round style="margin-top:10px;" @click="edit">
          编辑内容
        </van-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { showToast, showDialog, showConfirmDialog } from 'vant'
import { wishApi, type WishItem } from '@/api'

const router = useRouter()
const w = ref<any>(null)
const rejectReason = ref('')
const route = useRoute()

function safeNum(v: any): number {
  if (v === null || v === undefined || v === '') return 0
  const s = String(v).replace(/[\u0000-\u001F\u007F-\u009F\u2000-\u200F\u2028-\u202F\uFEFF]/g, '').replace(/[^\d.\-+]/g, '')
  const n = Number(s)
  return Number.isFinite(n) ? n : 0
}

onMounted(async () => {
  const idFromRoute = Number(route.params?.id) || 0
  try {
    if (idFromRoute > 0) {
      const detail = await wishApi.detail(idFromRoute) as any
      w.value = normalizeWish(detail)
      sessionStorage.setItem('wish.detail', JSON.stringify(w.value))
      return
    }
  } catch (e) {}
  try {
    const stored = sessionStorage.getItem('wish.detail')
    if (stored) w.value = normalizeWish(JSON.parse(stored))
  } catch (e) {}
  if (!w.value) return router.back()
})

function normalizeWish(raw: any): any {
  if (!raw) return raw
  const out: any = { ...raw }
  out.title = raw.title || raw.name || ''
  out.name = raw.name || raw.title || ''
  out.description = raw.description || raw.desc || raw.content || ''
  out.cost = safeNum(raw.cost ?? raw.coinCost ?? raw.price ?? 0)
  out.deposit = safeNum(raw.deposit ?? raw.depositSaved ?? raw.savedCoins ?? raw.saved ?? raw.currentSaved ?? 0)
  const totalSteps = safeNum(raw.totalSteps ?? raw.stepCount ?? (Array.isArray(raw.steps) ? raw.steps.length : 0))
  const completedSteps = safeNum(raw.completedSteps ?? raw.doneCount ?? raw.doneSteps ?? 0)
  out.totalSteps = totalSteps
  out.completedSteps = completedSteps
  if (!out.steps && Array.isArray(raw.stepsJson)) out.steps = raw.stepsJson
  if (!Array.isArray(out.steps)) out.steps = []
  out.steps = out.steps.map((s: any, i: number) => ({
    ...s,
    title: s?.title ?? s?.name ?? `步骤 ${i+1}`,
    name: s?.name ?? s?.title ?? `步骤 ${i+1}`,
    done: !!s?.done || !!s?.checked || !!s?.completed
  }))
  return out
}

const savedCoins = computed(() => w.value ? safeNum(w.value.deposit ?? w.value.saved ?? w.value.currentSaved ?? 0) : 0)

const progressPct = computed(() => {
  if (!w.value) return 0
  const t = Math.max(1, safeNum(w.value.cost))
  return Math.min(100, Math.round(savedCoins.value * 100 / t))
})

const stepsDone = computed(() => {
  const st = w.value?.steps || []
  let c = 0
  for (const s of st) if (!!s.done || !!s.checked || !!s.completed) c++
  return c
})
const allStepsDone = computed(() => {
  const st = w.value?.steps || []
  return !st.length || st.every((s: any) => !!s.done || !!s.checked || !!s.completed)
})
const canToggleStep = computed(() => w.value?.status === 'APPROVED')

const headBg = computed(() => {
  const s = w.value?.status
  if (s === 'COMPLETED') return 'linear-gradient(135deg,#D9F7E2 0%,#F5FFF7 100%)'
  if (s === 'APPROVED') return 'linear-gradient(135deg,#FFE0EC 0%,#FFF5F7 100%)'
  if (s === 'PENDING_APPROVAL') return 'linear-gradient(135deg,#FFF2CC 0%,#FFBE6 100%)'
  if (s === 'REJECTED') return 'linear-gradient(135deg,#FFE0E0 0%,#FFF5F5 100%)'
  return 'linear-gradient(135deg,#F5F5F5 0%,#FAFAFA 100%)'
})

const statusLabel = computed(() =>
  ({ DRAFT: '草稿', PENDING_APPROVAL: '审批中', APPROVED: '进行中', COMPLETED: '已完成', REJECTED: '已拒绝' } as any)[w.value?.status || '']
)
const tagColor = computed(() =>
  ({ DRAFT: '#999', PENDING_APPROVAL: '#FF7A45', APPROVED: '#FF6B9D', COMPLETED: '#52C41A', REJECTED: '#FF4D4F' } as any)[w.value?.status || '']
)

const stepActive = computed(() => {
  switch (w.value?.status) {
    case 'DRAFT': return 0
    case 'PENDING_APPROVAL': return 1
    case 'APPROVED': return 2 + (savedCoins.value >= safeNum(w.value?.cost) ? 1 : 0)
    case 'COMPLETED': return 4
    case 'REJECTED': return 0
    default: return 0
  }
})

function toggleStepByRow(i: number) {
  if (!canToggleStep.value) return
  toggleStep(i, null)
}

async function toggleStep(i: number, evt: any) {
  if (!w.value || !canToggleStep.value) return
  const arr = w.value.steps || []
  const origin = !!arr[i]?.done
  let next = !origin
  if (typeof evt === 'boolean') next = evt
  else if (evt && typeof evt.detail === 'boolean') next = !!evt.detail
  else if (evt && (evt.target || {}).checked !== undefined) next = !!evt.target.checked
  arr[i].done = next
  arr[i].checked = next
  try {
    const r = await wishApi.completeStep(w.value.id, i) as any
    if (r) w.value = normalizeWish(r)
    showToast({ type: 'success', message: next ? '已完成步骤 👍' : '已取消标记' })
  } catch (e: any) {
    arr[i].done = origin
    arr[i].checked = origin
    showToast(e?.message || '操作失败')
  }
}

async function doApply() {
  if (!w.value) return
  try {
    const r = await wishApi.apply(w.value.id) as any
    w.value = normalizeWish(r)
    showToast({ type: 'success', message: '已提交TA审批' })
  } catch (e: any) { showToast(e?.message || '提交失败') }
}

async function doApprove() {
  if (!w.value) return
  try {
    const r = await wishApi.approve(w.value.id) as any
    w.value = normalizeWish(r)
    showToast({ type: 'success', message: '已批准，一起加油实现吧' })
  } catch (e: any) { showToast(e?.message || '审批失败') }
}

async function doReject() {
  if (!w.value) return
  rejectReason.value = ''
  try {
    await showDialog({
      title: '拒绝此心愿',
      message: '请输入拒绝理由后点确定',
      showCancelButton: true,
      confirmButtonText: '确定拒绝',
    })
    // 简单prompt兜底
    const input = (window as any).prompt ? (window as any).prompt('请填写拒绝理由（必填）：') : null
    const reason = String(input || rejectReason.value || '').trim()
    if (!reason) { showToast('请填写理由'); return }
    const r = await wishApi.reject(w.value.id, { reason }) as any
    w.value = normalizeWish(r)
    showToast('已拒绝')
  } catch (_) {}
}

async function doComplete() {
  if (!w.value) return
  try {
    await showConfirmDialog({ title: '确认兑换？', message: `确认心愿已完成，将扣除 ${safeNum(w.value.cost)}💰 并完成心愿。` })
    const r = await wishApi.completeStep(w.value.id, -1) as any
    w.value = normalizeWish(r)
    showToast({ type: 'success', message: `心愿完成！太棒了 🎉 -${safeNum(w.value.cost)}💰` })
    setTimeout(() => router.back(), 600)
  } catch (e: any) { showToast(e?.message || '操作失败') }
}

function edit() {
  sessionStorage.setItem('wish.edit', JSON.stringify(w.value || {}))
  router.push('/app/interactive/wishes/edit')
}
</script>

<style scoped>
.step-row.clickable {
  cursor: pointer !important;
  touch-action: manipulation;
  border-radius: 10px;
  margin: 2px -6px;
  padding: 6px 6px;
}
.step-row.clickable:active { background: #FFF6FB; }
.step-title { font-weight: 600; color: #333; }
.step-desc { font-size: 12px; color: #999; margin-top: 2px; }
</style>