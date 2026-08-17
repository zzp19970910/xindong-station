import request from './request'

export interface MoodItem {
  id: number
  coupleId: number
  userId: number
  partnerIdx?: number
  dateStr: string
  emoji: string
  score: number
  note?: string
  createdAt: string
}

export interface AnniversaryItem {
  id: number
  coupleId: number
  title: string
  type: 'love' | 'travel' | 'birthday' | 'anniversary' | 'other'
  emoji?: string
  targetDate: string
  note?: string
  isTop: boolean
  displayMode?: 'countdown' | 'countup'
  createdBy: number
  createdAt: string
}

export interface DiaryItem {
  id: number
  coupleId: number
  createdById: number
  partnerIdx?: number
  title?: string
  content: string
  images?: string[]
  moodEmoji?: string
  createdAt: string
  commentsCount?: number
}

export interface ChecklistItem {
  id: number
  coupleId?: number
  templateId?: number
  category: string
  title: string
  description?: string
  emoji?: string
  isDone: boolean
  doneAt?: string
  isPreset: boolean
  sortOrder: number
  milestoneBonus?: number
}

export interface QuizQuestion {
  id: number
  category: string
  question: string
  options: { k: string; v: string }[]
  isMultiple: boolean
}

export interface QuizAnswer {
  id: number
  coupleId: number
  questionId: number
  dateStr: string
  partnerIdx: number
  answer: string[]
  matchPercent?: number
  createdAt: string
}

export interface LoveLetterItem {
  id: number
  coupleId: number
  senderId: number
  receiverId: number
  title: string
  body: string
  isRead: boolean
  isCapsule: boolean
  capsuleOpenAt?: string
  status: 'DRAFT' | 'SENT' | 'OPENED'
  sentAt?: string
  createdAt: string
}

export interface WeeklyCard {
  weekOffset: number
  weekLabel: string
  monday: string
  sunday: string
  theme: { id: number; emoji: string; name: string; coverColor: string; slogan: string }
  daysTogether: number
  mood: any
  upcomingAnniversaries: any[]
  diary: any
  messagesThisWeek: number
  quizMatchAverage?: number
  checklist: any
  loveScore: { score: number; grade: 'S' | 'A' | 'B' | 'C'; comment: string }
  generatedAt: string
}

export interface AnniversaryCard {
  id: number
  title: string
  emoji?: string
  isTop: boolean
  targetDate?: string
  daysUntil: number
}

export interface DashboardRaw {
  overview: {
    togetherDays: number
    signStreak: number
    coinsTotal: number
    isCoolingActive: boolean
    coolingRemainingHours: number
    themeId?: string
  }
  mood7Days: Array<{
    date: string
    shortDate: string
    weekday: number
    level: number
    p1?: { moodType: number } | null
    p2?: { moodType: number } | null
  }>
  anniversaries: AnniversaryCard[]
  recentDiariesRaw: Array<{
    id: number
    summary: string
    firstImage?: string | null
    imageCount: number
    mood?: string
    recordDate?: string
    partnerIdx?: number
  }>
  milestoneProgress: {
    totalIncome: number
    nextMilestone?: { stage: number; threshold: number; label: string; reward: number } | null
    nextNeed: number
    percentToNext: number
    unlockedList: Array<{ stage: number; threshold: number; label: string; reward: number }>
  }
  todayTasks: {
    moodChecked: { p1: boolean; p2: boolean; bothChecked: boolean }
    todayCoinsEarned: number
    dailyCoinLimitLeft: Record<string, number>
    tips: string[]
  }
}

export interface DashboardData extends DashboardRaw {
  daysTogether: number
  coinsTotal: number
  todayMoods: MoodItem[]
  upcomingAnniversaries: Array<
    AnniversaryCard & {
      icon: string
      name: string
      nextDate: string
      date: string
      daysLeft: number
    }
  >
  recentDiaries: DiaryItem[]
  unreadMessages: number
  todayQuizDone: boolean
  wishProgress: any
  checklistProgress: any
  streak: number
}

export const moodApi = {
  checkin: (data: { emoji: string; score: number; note?: string }): Promise<MoodItem> =>
    request.post('/moods/checkin', data),
  list: (coupleId?: number): Promise<MoodItem[]> =>
    request.get('/moods', { params: { coupleId } }),
  calendar: (year: number, month: number): Promise<Record<string, MoodItem[]>> =>
    request.get(`/moods/calendar/${year}/${month}`)
}

export const anniversaryApi = {
  list: (): Promise<AnniversaryItem[]> => request.get('/anniversaries'),
  create: (data: Omit<AnniversaryItem, 'id' | 'coupleId' | 'createdBy' | 'createdAt'>): Promise<AnniversaryItem> =>
    request.post('/anniversaries', data),
  update: (id: number, data: Partial<AnniversaryItem>): Promise<AnniversaryItem> =>
    request.put(`/anniversaries/${id}`, data),
  remove: (id: number): Promise<void> => request.delete(`/anniversaries/${id}`)
}

export const diaryApi = {
  list: (params?: { page?: number; size?: number; partnerIdx?: number }): Promise<{ list: DiaryItem[]; total: number }> =>
    request.get('/diaries', { params }),
  get: (id: number): Promise<DiaryItem & { comments?: any[] }> => request.get(`/diaries/${id}`),
  create: (data: { title?: string; content: string; images?: string[]; moodEmoji?: string }): Promise<DiaryItem> =>
    request.post('/diaries', data),
  update: (id: number, data: Partial<DiaryItem>): Promise<DiaryItem> => request.put(`/diaries/${id}`, data),
  remove: (id: number): Promise<void> => request.delete(`/diaries/${id}`),
  addComment: (id: number, data: { content: string }): Promise<any> =>
    request.post(`/diaries/${id}/comments`, data)
}

export const checklistApi = {
  list: (params?: { category?: string; onlyDone?: boolean }): Promise<any> =>
    request.get('/checklists', { params }),
  create: (data: Partial<ChecklistItem>): Promise<ChecklistItem> => request.post('/checklists', data),
  toggle: (id: number, done?: boolean): Promise<{ item: ChecklistItem; bonus?: number; milestone?: number }> =>
    request.put(`/checklists/${id}/toggle`, { done: !!done, isDone: !!done }),
  remove: (id: number): Promise<void> => request.delete(`/checklists/${id}`),
  tickIncentive: (id: number): Promise<any> => request.post(`/incentive-checklists/${id}/tick`),
  createCustom: (coupleId: number, data: any): Promise<any> =>
    request.post(`/incentive-checklists/custom/${coupleId}`, data),
  removeCustom: (id: number): Promise<void> => request.delete(`/incentive-checklists/custom/${id}`)
}

export const dailyQuizApi = {
  today: (): Promise<{ dateStr: string; questions: QuizQuestion[] }> => request.get('/daily-quiz/today'),
  submit: (data: { questionId: number; answer: string[] }): Promise<QuizAnswer> =>
    request.post('/daily-quiz/submit', data),
  todayResult: (): Promise<{ questions: QuizQuestion[]; myAnswers: any[]; partnerAnswers: any[]; matchAvg?: number }> =>
    request.get('/daily-quiz/today-result')
}

export const loveLetterApi = {
  list: (params?: { status?: string; page?: number; size?: number }): Promise<{ list: LoveLetterItem[]; total: number }> =>
    request.get('/letters', { params }),
  get: (id: number): Promise<LoveLetterItem> => request.get(`/letters/${id}`),
  create: (data: {
    title: string
    body: string
    receiverId: number
    isCapsule?: boolean
    capsuleOpenAt?: string
  }): Promise<LoveLetterItem> => request.post('/letters', data),
  cancelSchedule: (id: number): Promise<any> => request.put(`/letters/${id}/cancel-schedule`),
  markRead: (id: number): Promise<any> => request.put(`/letters/${id}/mark-read`),
  reply: (id: number, data: { body: string }): Promise<any> => request.post(`/letters/${id}/reply`, data)
}

export const weeklyApi = {
  get: (weekOffset = 0): Promise<WeeklyCard> => request.get('/weekly', { params: { weekOffset } })
}

function adaptDashboard(raw: DashboardRaw): DashboardData {
  const upcomingAnniversaries = (raw.anniversaries || []).map((a) => ({
    ...a,
    icon: a.emoji || '🎉',
    name: a.title,
    nextDate: a.targetDate || '',
    date: a.targetDate || '',
    daysLeft: a.daysUntil || 0
  }))
  const recentDiaries: DiaryItem[] = (raw.recentDiariesRaw || []).map((d) => ({
    id: d.id,
    coupleId: 0,
    createdById: 0,
    partnerIdx: d.partnerIdx,
    title: undefined,
    content: d.summary,
    images: d.firstImage ? [d.firstImage] : undefined,
    moodEmoji: d.mood,
    createdAt: d.recordDate || '',
    commentsCount: undefined
  }))
  return {
    ...raw,
    daysTogether: raw.overview?.togetherDays || 0,
    coinsTotal: raw.overview?.coinsTotal || 0,
    streak: raw.overview?.signStreak || 0,
    todayMoods: [],
    upcomingAnniversaries,
    recentDiaries,
    unreadMessages: 0,
    todayQuizDone: false,
    wishProgress: raw.milestoneProgress || {},
    checklistProgress: {
      progressPct: raw.milestoneProgress?.percentToNext || 0,
      nextStage: raw.milestoneProgress?.nextMilestone
        ? {
            needMore: raw.milestoneProgress.nextMilestone.threshold - (raw.milestoneProgress.totalIncome || 0),
            bonus: raw.milestoneProgress.nextMilestone.reward
          }
        : null
    }
  }
}

export const dashboardApi = {
  get: async (coupleId: number): Promise<DashboardData> => adaptDashboard(await request.get(`/dashboard/${coupleId}`)),
  home: async (coupleId: number): Promise<DashboardData> => adaptDashboard(await request.get(`/home/dashboard/${coupleId}`))
}