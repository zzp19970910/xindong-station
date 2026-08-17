<template>
  <div class="page">
    <van-nav-bar title="恋爱周报" left-arrow fixed placeholder @click-left="$router.back()">
      <template #right>
        <span style="padding-right:12px;color:#FF6B9D;" @click="prevWeek">‹ 上周</span>
        <span v-if="off<0" style="color:#FF6B9D;" @click="nextWeek">本周 ›</span>
      </template>
    </van-nav-bar>

    <div v-if="card" style="padding:16px;">
      <div class="head-card" :style="{background:`linear-gradient(135deg, ${card.theme?.coverColor||'#FF6B9D'} 0%, #FFF0F5 100%)`}">
        <div style="display:flex;align-items:center;gap:12px;">
          <div style="width:56px;height:56px;border-radius:16px;background:rgba(255,255,255,0.3);display:flex;align-items:center;justify-content:center;font-size:30px;">
            {{ card.theme?.emoji || '📊' }}
          </div>
          <div style="flex:1;">
            <div style="font-weight:700;font-size:18px;color:#fff;">{{ card.theme?.name || '恋爱周报' }}</div>
            <div class="subtle" style="color:rgba(255,255,255,0.85);margin-top:2px;">
              {{ card.weekLabel }} · {{ card.monday }} ~ {{ card.sunday }}
            </div>
          </div>
        </div>

        <div style="text-align:center;margin:24px 0 8px;">
          <div style="font-size:72px;font-weight:900;color:#fff;text-shadow:0 2px 8px rgba(255,107,157,0.3);">
            {{ card.loveScore?.score || 0 }}
          </div>
          <div style="color:rgba(255,255,255,0.9);margin-top:4px;">恋爱力指数</div>
          <div style="margin-top:16px;">
            <van-tag round color="#FFF" text-color="#FF3D7F" size="medium">
              等级 {{ card.loveScore?.grade || 'C' }}
            </van-tag>
          </div>
        </div>
        <div style="text-align:center;color:rgba(255,255,255,0.92);line-height:1.6;">
          "{{ card.loveScore?.comment || '继续加油呀～' }}"
        </div>
      </div>

      <div class="card">
        <div class="card-title"><span>🌡️ 六维雷达</span></div>
        <div style="display:grid;grid-template-columns:repeat(2,1fr);gap:12px;margin-top:8px;">
          <div v-for="(sc,i) in scores" :key="sc.k" class="dim-cell">
            <div style="display:flex;align-items:center;justify-content:space-between;">
              <span>{{ sc.emoji }} {{ sc.n }}</span>
              <b>{{ sc.v }}分</b>
            </div>
            <van-progress :percentage="sc.v" :color="sc.c" stroke-width="6" style="margin-top:6px;" />
          </div>
        </div>
      </div>

      <div class="card" style="background:linear-gradient(135deg,#FFE0EC 0%,#FFF5F7 100%);">
        <div class="card-title"><span>💑 在一起</span><span style="color:#FF3D7F;font-weight:800;">第 {{ card.daysTogether }} 天</span></div>
        <div class="subtle">本周主题 slogan：{{ card.theme?.slogan || '继续创造属于你们的浪漫回忆' }}</div>
      </div>

      <div v-if="card.upcomingAnniversaries?.length" class="card">
        <div class="card-title"><span>🎂 即将到来</span></div>
        <div v-for="a in card.upcomingAnniversaries.slice(0,3)" :key="a.id" class="row-line">
          <span style="font-size:22px;">{{ a.emoji || '🎉' }}</span>
          <div style="flex:1;margin:0 12px;">
            <div style="font-weight:600;">{{ a.title || a.name }}</div>
            <div class="subtle">{{ a.targetDate || a.nextDate }}</div>
          </div>
          <div style="color:#FF6B9D;font-weight:700;">D{{ a.daysLeft>=0?'-'+a.daysLeft:'+'+(-a.daysLeft) }}</div>
        </div>
      </div>

      <div class="card">
        <div class="card-title"><span>😊 本周心情</span></div>
        <div v-if="card.mood?.avg" style="display:flex;align-items:center;gap:16px;">
          <div style="text-align:center;">
            <div style="font-size:48px;">{{ card.mood?.avgEmoji || '🙂' }}</div>
            <div class="subtle">平均情绪</div>
          </div>
          <div style="flex:1;">
            <div style="font-size:24px;font-weight:800;">{{ card.mood?.avg || 0 }}<span class="subtle" style="font-size:12px;"> / 10</span></div>
            <van-progress :percentage="(card.mood?.avg || 0)*10" color="#FF6B9D" stroke-width="6" style="margin-top:8px;" />
            <div class="subtle" style="margin-top:6px;">P1打卡 {{ card.mood?.p1Days||0 }}天 · P2打卡 {{ card.mood?.p2Days||0 }}天</div>
          </div>
        </div>
        <div v-else class="subtle" style="text-align:center;padding:20px 0;">本周暂无心情打卡记录</div>
      </div>

      <div class="card">
        <div class="card-title"><span>📖 日记记录</span></div>
        <div v-if="card.diary?.total" style="display:flex;gap:16px;">
          <div style="flex:1;text-align:center;padding:16px;background:#FFF0F5;border-radius:12px;">
            <div style="font-size:28px;font-weight:800;color:#FF6B9D;">{{ card.diary?.total || 0 }}</div>
            <div class="subtle">篇日记</div>
          </div>
          <div style="flex:1;text-align:center;padding:16px;background:#FFFBF0;border-radius:12px;">
            <div style="font-size:28px;font-weight:800;color:#B8860B;">{{ card.diary?.words || 0 }}</div>
            <div class="subtle">字的温度</div>
          </div>
          <div style="flex:1;text-align:center;padding:16px;background:#F0F8FF;border-radius:12px;">
            <div style="font-size:28px;font-weight:800;color:#1890FF;">{{ card.diary?.comments || 0 }}</div>
            <div class="subtle">次评论</div>
          </div>
        </div>
        <div v-else class="subtle" style="text-align:center;padding:20px 0;">本周暂无日记</div>
      </div>

      <div v-if="card.quizMatchAverage!==undefined" class="card">
        <div class="card-title"><span>❓ 默契答题</span></div>
        <div style="display:flex;align-items:center;gap:16px;">
          <div style="width:64px;height:64px;border-radius:50%;background:conic-gradient(#FF6B9D {{card.quizMatchAverage*3.6}}deg,#F0F0F0 0);display:flex;align-items:center;justify-content:center;">
            <div style="width:48px;height:48px;border-radius:50%;background:#fff;display:flex;align-items:center;justify-content:center;font-weight:800;color:#FF3D7F;">
              {{ card.quizMatchAverage }}%
            </div>
          </div>
          <div style="flex:1;">
            <div style="font-weight:600;">默契匹配度平均</div>
            <div class="subtle" style="margin-top:4px;">
              {{ card.quizMatchAverage>=80?'你们超懂彼此的 ✨':card.quizMatchAverage>=60?'还不错，继续加油':'多聊聊天更懂TA' }}
            </div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-title"><span>✅ 恋爱清单</span></div>
        <div v-if="card.checklist">
          <van-progress :percentage="card.checklist.progressPct||0" color="#52C41A" stroke-width="8" style="border-radius:999px;overflow:hidden;" />
          <div style="display:flex;justify-content:space-between;margin-top:8px;">
            <span class="subtle">已完成 {{ card.checklist.done||0 }} / {{ card.checklist.total||0 }} 件</span>
            <span class="green">{{ card.checklist.progressPct||0 }}%</span>
          </div>
        </div>
      </div>

      <div class="card" style="background:linear-gradient(135deg,#FFFBF0 0%,#FFF7FA 100%);">
        <div class="card-title"><span>💬 悄悄话</span></div>
        <div style="display:flex;align-items:center;gap:16px;">
          <div style="font-size:44px;">💌</div>
          <div style="flex:1;">
            <div style="font-size:24px;font-weight:800;color:#B8860B;">{{ card.messagesThisWeek || 0 }}</div>
            <div class="subtle">本周发送了这么多条悄悄话</div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-title"><span>💡 下周建议</span></div>
        <div style="padding:12px 16px;background:#FFFAFC;border-left:3px solid #FF6B9D;border-radius:0 10px 10px 0;line-height:1.8;color:#555;">
          {{ suggestion }}
        </div>
      </div>

      <div style="height:20px;"></div>
    </div>

    <van-empty v-else description="加载中..." />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { weeklyApi, type WeeklyCard } from '@/api'

const card = ref<WeeklyCard | null>(null)
const off = ref(0)

const scores = computed(() => {
  const c = card.value; if (!c) return []
  return [
    { k:'mood', n:'心情打卡', emoji:'😊', v: c.mood?.scorePct ?? (c.mood?.avg? Math.round(c.mood.avg*10):50), c:'#FF6B9D' },
    { k:'anniv', n:'纪念日常识', emoji:'🎂', v:80, c:'#FA8C16' },
    { k:'diary', n:'日记记录', emoji:'📖', v: Math.min(100, (c.diary?.total||0)*30), c:'#722ED1' },
    { k:'quiz', n:'默契答题', emoji:'❓', v: c.quizMatchAverage ?? 60, c:'#1890FF' },
    { k:'check', n:'恋爱清单', emoji:'✅', v: c.checklist?.progressPct ?? 40, c:'#52C41A' },
    { k:'msg', n:'悄悄话互动', emoji:'💬', v: Math.min(100, (c.messagesThisWeek||0)*5), c:'#13C2C2' }
  ]
})

const suggestion = computed(() => {
  const s = card.value?.loveScore?.score || 0
  if (s >= 90) return '继续保持！这一周可以尝试一个全新的破冰转盘任务，或者一起挑战默契游戏，看看是不是依然心有灵犀～'
  if (s >= 70) return '建议本周：一起写一篇日记，回答每日默契题，还有尝试一个没做过的清单任务，会让感情升温哦！'
  if (s >= 50) return '本周建议多一些文字交流：给TA写一封小情书、记录心情打卡、在悄悄话里多说说今天发生的小事。'
  return '建议从每日心情打卡和默契题开始，哪怕每天只花3分钟，也能让彼此更了解。加油呀！'
})

onMounted(load)
watch(off, load)

async function load() {
  try {
    card.value = await weeklyApi.get(off.value)
  } catch (e) {}
}
function prevWeek() { off.value-- }
function nextWeek() { if (off.value<0) off.value++ }
</script>

<style scoped>
.head-card {
  border-radius: 20px;
  padding: 20px;
  color: #fff;
  box-shadow: 0 6px 20px rgba(255,107,157,0.2);
  margin-bottom: 16px;
}
.dim-cell {
  background: #FFFAFC;
  border-radius: 10px;
  padding: 10px 12px;
}
</style>