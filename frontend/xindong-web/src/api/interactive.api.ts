import request from './request'

export interface MsgItem {
  id: number
  coupleId: number
  senderId: number
  receiverId: number
  type: 'TEXT' | 'IMAGE' | 'EMOJI'
  content: string
  isRead: boolean
  createdAt: string
}

export interface IcebreakTask {
  id: number
  title: string
  description: string
  category: string
  difficulty: 1 | 2 | 3 | 4 | 5
  bonusCoins: number
  timeMin: number
  emoji: string
}

export interface SpinResult {
  spinTodayLeft: number
  spinsLeft: number
  task: IcebreakTask
  sessionId: string
  session_id?: string
  segment: number
}

export interface IcebreakHistory {
  id: number
  coupleId: number
  taskId: number
  taskTitle: string
  taskEmoji: string
  status: 'SPUN' | 'DONE' | 'FAILED' | 'SKIPPED'
  bonusCoins?: number
  proofImage?: string
  note?: string
  createdAt: string
  doneAt?: string
}

export interface TacitQOption {
  optionId: number
  label: string
}
export interface TacitQuestion {
  questionId: number
  question: string
  options: TacitQOption[]
  myOptionId?: number
  myGuessPartnerOptionId?: number
  partnerActualOptionId?: number
  partnerGuessMyOptionId?: number
  iGuessHit?: boolean
  partnerGuessHit?: boolean
  partnerAnswered?: boolean
}
export interface TacitGame {
  gameId: number
  done: boolean
  matchPercent?: number
  score?: number
  createdById?: number
  p1PartnerIdx?: number
  p2PartnerIdx?: number
  p1FinishedAt?: string
  p2FinishedAt?: string
  createdAt: string
  questions: TacitQuestion[]
  /** 本地UI用：当前应显示 "玩家1|P2回合" 等状态 */
  status?: 'WAIT_P1' | 'WAIT_P2' | 'FINISHED'
}

export interface SettingsProfile {
  nickname?: string
  avatarUrl?: string
  gender?: 'F' | 'M'
  birthday?: string
}

export interface CoolingStatus {
  isActive: boolean
  coolingUntil?: string
  coolingLockUntil?: string
  remainingSeconds: number
  canDisable: boolean
}

const msg = (path: string) => `/messages${path}`

export const messageApi = {
  send: (data: { receiverId: number; type?: 'TEXT' | 'IMAGE' | 'EMOJI'; content: string }): Promise<MsgItem> =>
    request.post(msg('/send'), data),
  list: (params?: { page?: number; size?: number; asc?: boolean }): Promise<{ list: MsgItem[]; total: number }> =>
    request.get(msg('/list'), { params }),
  unread: (): Promise<{ count: number }> => request.get(msg('/unread')),
  readBatch: (): Promise<{ count: number }> => request.post(msg('/read-batch')),
  recall: (msgId: number): Promise<void> => request.delete(msg(`/${msgId}/recall`))
}

export const icebreakApi = {
  state: (): Promise<{ spinsLeft: number; spinTodayLeft: number; task: IcebreakTask | null; hasUnfinished: boolean; sessionId: string }> =>
    request.get('/icebreak/state'),
  spin: (): Promise<SpinResult> => request.post('/icebreak/spin'),
  submit: (taskId: number, data: { sessionId?: string; proofImage?: string; note?: string }): Promise<{ bonus: number; bonusCoins: number; spinsLeft: number; record: IcebreakHistory }> =>
    request.post(`/icebreak/task/${taskId}/submit`, data),
  history: (params?: { page?: number; size?: number }): Promise<{ list: IcebreakHistory[]; total: number }> =>
    request.get('/icebreak/history', { params })
}

export const tacitApi = {
  start: (payload: { myAnswers: Record<string, number>; guessPartnerAnswers: Record<string, number> }): Promise<TacitGame> =>
    request.post('/tacit/start', payload),
  answer: (gameId: number, payload: { myAnswers: Record<string, number>; guessPartnerAnswers: Record<string, number> }): Promise<TacitGame> =>
    request.post(`/tacit/${gameId}/answer`, payload),
  get: (gameId: number): Promise<TacitGame> => request.get(`/tacit/${gameId}`),
  history: (params?: { page?: number; size?: number }): Promise<{ list: TacitGame[]; total: number }> =>
    request.get('/tacit/history', { params }),
  seedQuestions: (): Promise<TacitQuestion[]> => request.get('/tacit/questions').then((res: any) => {
    const list: any[] = res.data ?? res ?? []
    return list.map((q: any) => ({
      questionId: Number(q.questionId ?? q.id),
      question: String(q.question ?? q.q ?? ''),
      options: Array.isArray(q.options)
        ? q.options.map((o: any, i: number) => {
            if (typeof o === 'string') return { optionId: i + 1, label: o }
            return { optionId: Number(o.optionId ?? i + 1), label: String(o.label ?? o.text ?? o.value ?? '') }
          })
        : []
    }))
  })
}

export const quizApi = {
  today: (coupleId: number): Promise<any> => request.get(`/quiz/today/${coupleId}`),
  answer: (data: any): Promise<any> => request.post('/quiz/answer', data),
  history: (coupleId: number): Promise<any> => request.get(`/quiz/history/${coupleId}`)
}

export const settingsApi = {
  me: (coupleId: number): Promise<any> => request.get(`/settings/me/${coupleId}`),
  setTheme: (theme: string): Promise<any> => request.put('/settings/theme', { theme }),
  setProfile: (profile: SettingsProfile): Promise<any> => request.put('/settings/profile', profile),
  weekly: (coupleId: number): Promise<any> => request.get(`/settings/weekly/${coupleId}`),
  coolingEnable: (): Promise<CoolingStatus> => request.post('/settings/cooling/enable'),
  coolingDisable: (): Promise<CoolingStatus> => request.post('/settings/cooling/disable'),
  coolingStatus: (coupleId: number): Promise<CoolingStatus> => request.get(`/settings/cooling/status/${coupleId}`)
}

export const homeApi = {
  dashboard: (coupleId: number): Promise<any> => request.get(`/home/dashboard/${coupleId}`)
}
