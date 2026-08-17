import axios, { AxiosInstance, InternalAxiosRequestConfig } from 'axios'
import { showToast, showDialog } from 'vant'
import { useAuthStore } from '@/stores/auth.store'
import router from '@/router'

const request: AxiosInstance = axios.create({
  baseURL: ((import.meta as any).env.VITE_API_BASE as string)?.trim() || '/api/v1',
  timeout: 10000
})

request.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

request.interceptors.response.use(
  (res) => {
    const body = res.data
    if (!body || typeof body.code === 'undefined') return res
    const firstChar = String(body.code).charAt(0)

    if (firstChar === '0') return body.data

    if (body.code === '30005' || body.code === '30006') {
      showDialog({ title: '登录过期', message: '请重新登录', confirmButtonText: '好的' }).then(() => {
        useAuthStore().logout()
        router.replace('/auth')
      })
      return Promise.reject(body)
    }

    if (firstChar === '1') {
      showToast({ type: 'success', message: body.msg, forbidClick: true })
      return body.data
    }

    if (firstChar === '2') {
      // 🔴业务类错误(2xxxx)：由各页面catch自己决定怎么展示（是Toast/Dialog/还是静默处理如破冰21103自动恢复任务）
      //   只在页面没catch的Dev环境会有控制台提示，生产用户不会看到"未捕获的Promise rejection"大红屏
      return Promise.reject(Object.assign(new Error(body.msg || '操作失败'), {
        code: body.code,
        msg: body.msg,
        message: body.msg,
        data: body.data || null
      }))
    }

    showToast({ type: 'fail', message: body.msg || '操作失败', forbidClick: true })
    return Promise.reject(Object.assign(new Error(body.msg || '操作失败'), {
      code: body.code,
      msg: body.msg,
      message: body.msg,
      data: body.data || null
    }))
  },
  (err) => {
    if (err?.response?.status === 401) {
      useAuthStore().logout()
      router.replace('/auth')
    }
    // 后端非2xx返回(如409/404)但有body={code,msg,data}时，透传code/data给页面catch
    const body = err?.response?.data
    if (body && typeof body.code !== 'undefined') {
      return Promise.reject(Object.assign(new Error(body.msg || '操作失败'), {
        code: body.code,
        msg: body.msg,
        message: body.msg,
        data: body.data || null,
        status: err.response.status
      }))
    }
    showToast({ type: 'fail', message: '网络异常，请稍后重试' })
    return Promise.reject(err)
  }
)

export default request