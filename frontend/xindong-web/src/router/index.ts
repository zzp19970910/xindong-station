import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { showToast } from 'vant'
import { useAuthStore } from '@/stores/auth.store'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/auth' },
  { path: '/auth', component: () => import('@/views/AuthView.vue'), meta: { noAuth: true } },
  { path: '/bind', component: () => import('@/views/BindView.vue') },
  {
    path: '/app',
    component: () => import('@/views/MainLayout.vue'),
    redirect: '/app/home',
    children: [
      { path: 'home', name: 'Home', component: () => import('@/views/HomeView.vue'), meta: { title: '心动空间站' } },
      {
        path: 'record',
        name: 'Record',
        component: () => import('@/views/RecordView.vue'),
        meta: { title: '记录' }
      },
      {
        path: 'record/anniv/edit',
        name: 'AnnivEdit',
        component: () => import('@/views/record/AnnivEdit.vue'),
        meta: { title: '编辑纪念日' }
      },
      {
        path: 'record/diary/edit',
        name: 'DiaryEdit',
        component: () => import('@/views/record/DiaryEdit.vue'),
        meta: { title: '写日记' }
      },
      {
        path: 'record/diary/:id',
        name: 'DiaryDetail',
        component: () => import('@/views/record/DiaryDetail.vue'),
        meta: { title: '日记' }
      },
      {
        path: 'record/checklist/edit',
        name: 'ChecklistEdit',
        component: () => import('@/views/record/ChecklistEdit.vue'),
        meta: { title: '添加清单' }
      },
      {
        path: 'letters',
        name: 'Letters',
        component: () => import('@/views/LettersView.vue'),
        meta: { title: '情书' }
      },
      {
        path: 'letters/write',
        name: 'LetterWrite',
        component: () => import('@/views/letters/LetterWrite.vue'),
        meta: { title: '写情书' }
      },
      {
        path: 'letters/:id',
        name: 'LetterDetail',
        component: () => import('@/views/letters/LetterDetail.vue'),
        meta: { title: '情书' }
      },
      {
        path: 'interactive',
        name: 'Interactive',
        component: () => import('@/views/InteractiveView.vue'),
        meta: { title: '互动' }
      },
      {
        path: 'interactive/quiz',
        name: 'QuizDaily',
        component: () => import('@/views/interactive/QuizDaily.vue'),
        meta: { title: '每日默契' }
      },
      {
        path: 'interactive/icebreak',
        name: 'Icebreak',
        component: () => import('@/views/interactive/Icebreak.vue'),
        meta: { title: '破冰大转盘' }
      },
      {
        path: 'interactive/wishes',
        name: 'WishList',
        component: () => import('@/views/interactive/WishList.vue'),
        meta: { title: '心愿商城' }
      },
      {
        path: 'interactive/wishes/edit',
        name: 'WishEdit',
        component: () => import('@/views/interactive/WishEdit.vue'),
        meta: { title: '编辑心愿' }
      },
      {
        path: 'interactive/wishes/:id',
        name: 'WishDetail',
        component: () => import('@/views/interactive/WishDetail.vue'),
        meta: { title: '心愿详情' }
      },
      {
        path: 'interactive/tacit',
        name: 'TacitGame',
        component: () => import('@/views/interactive/TacitGame.vue'),
        meta: { title: '默契小游戏' }
      },
      {
        path: 'interactive/chat',
        name: 'ChatRoom',
        component: () => import('@/views/interactive/ChatRoom.vue'),
        meta: { title: '悄悄话' }
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/SettingsView.vue'),
        meta: { title: '我的' }
      },
      {
        path: 'settings/weekly',
        name: 'WeeklyReport',
        component: () => import('@/views/settings/WeeklyReport.vue'),
        meta: { title: '恋爱周报' }
      },
      {
        path: 'settings/coins',
        name: 'CoinCenter',
        component: () => import('@/views/settings/CoinCenter.vue'),
        meta: { title: '金币中心' }
      },
      {
        path: 'settings/cooling',
        name: 'CoolingMode',
        component: () => import('@/views/settings/CoolingMode.vue'),
        meta: { title: '冷静模式' }
      },
      {
        path: 'settings/theme',
        name: 'ThemePicker',
        component: () => import('@/views/settings/ThemePicker.vue'),
        meta: { title: '主题皮肤' }
      },
      {
        path: 'settings/profile',
        name: 'ProfileEdit',
        component: () => import('@/views/settings/ProfileEdit.vue'),
        meta: { title: '个人资料' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.noAuth) return true
  if (!auth.token) {
    showToast('请先登录')
    return '/auth'
  }
  if (to.meta.title) document.title = to.meta.title as string
  return true
})

export default router