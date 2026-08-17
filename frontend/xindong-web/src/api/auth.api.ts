import request from './request'

export interface LoginResp {
  token: string
  user: { id: number; phone: string; nickname?: string; avatarUrl?: string; coupleId?: number }
  couple?: { id: number; inviteCodeP1?: string; inviteCodeP2?: string; togetherDate?: string }
}

export interface CoupleInfo {
  id: number
  togetherDate?: string
  inviteCodeP1: string
  inviteCodeP2: string
  coinsTotal: number
  theme: string
  coolingUntil?: string
  coolingLockUntil?: string
  signStreak: number
  hasPartner: boolean
}

export interface SendSmsResp { ok: true }
export interface InviteCodeResp { myCode: string; partnerCode: string }

export const authApi = {
  sendSms: (phone: string): Promise<SendSmsResp> => request.post('/auth/sms-code', { phone }),
  register: (data: { phone: string; smsCode: string; nickname: string; avatarUrl: string }): Promise<LoginResp> =>
    request.post('/auth/register', data),
  login: (phone: string, smsCode: string): Promise<LoginResp> => request.post('/auth/login', { phone, smsCode }),
  logout: (): Promise<void> => request.post('/auth/logout')
}

export const coupleApi = {
  bind: (inviteCode: string): Promise<CoupleInfo> => request.post('/couple/bind', { inviteCode }),
  info: (coupleId: number): Promise<CoupleInfo> => request.get(`/couple/info/${coupleId}`),
  setTogetherDate: (coupleId: number, date: string): Promise<{ ok: true }> =>
    request.put(`/couple/together-date`, null, { params: { coupleId, date } }),
  inviteCode: (coupleId: number): Promise<InviteCodeResp> => request.get(`/couple/invite-code/${coupleId}`)
}