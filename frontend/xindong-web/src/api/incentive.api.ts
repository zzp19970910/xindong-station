import request from './request'

export interface WishItem {
  id: number
  coupleId: number
  title: string
  description?: string
  emoji?: string
  cost: number
  steps: { title: string; done: boolean }[]
  currentStep: number
  deposit: number
  status: 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'COMPLETED' | 'REJECTED'
  createdById: number
  appliedById?: number
  approvedById?: number
  rejectedReason?: string
  createdAt: string
  completedAt?: string
}

export interface CoinLog {
  id: number
  coupleId: number
  delta: number
  balanceAfter: number
  reason: string
  reasonText: string
  refId?: number
  createdById?: number
  createdAt: string
}

export interface CoinOverview {
  total: number
  earned: number
  spent: number
  last7: { date: string; delta: number }[]
  reasonsPie: { reason: string; count: number; total: number }[]
}

export const wishApi = {
  list: (params?: { status?: string; page?: number; size?: number }): Promise<{ list: WishItem[]; total: number }> =>
    request.get('/wishes', { params }),
  detail: (id: number): Promise<WishItem> => request.get(`/wishes/${id}`),
  create: (data: Partial<WishItem>): Promise<WishItem> => request.post('/wishes', data),
  update: (id: number, data: Partial<WishItem>): Promise<WishItem> => request.put(`/wishes/${id}`, data),
  remove: (id: number): Promise<void> => request.delete(`/wishes/${id}`),
  apply: (id: number): Promise<WishItem> => request.post(`/wishes/${id}/apply`),
  approve: (id: number): Promise<WishItem> => request.post(`/wishes/${id}/approve`),
  reject: (id: number, data?: { reason: string }): Promise<WishItem> =>
    request.post(`/wishes/${id}/reject`, data || {}),
  completeStep: (id: number, stepIdx: number): Promise<WishItem> =>
    request.post(`/wishes/${id}/step/${stepIdx}`)
}

export const coinApi = {
  overview: (coupleId: number): Promise<CoinOverview> => request.get(`/coins/overview/${coupleId}`),
  logs: (coupleId: number, params?: { page?: number; size?: number; filter?: string }): Promise<{ list: CoinLog[]; total: number }> =>
    request.get(`/coins/logs/${coupleId}`, { params }),
  pie: (coupleId: number, days = 30): Promise<any> => request.get(`/coins/pie/${coupleId}`, { params: { days } })
}