import axios, { AxiosInstance, InternalAxiosRequestConfig } from 'axios'
import { showToast, showDialog } from 'vant'
import { useAuthStore } from '@/stores/auth.store'
import router from '@/router'


// --- 3-LAYER DEFENSE AGAINST localhost ---
// Layer 1: Force empty baseURL (same-origin). Ignore ALL env vars that may contain localhost from cached old builds.
// Layer 2: In request interceptor, if any URL/baseURL has localhost/127.0.0.1, rewrite to window.location.origin.
// Layer 3: Any env var value with ":8080" or ":5173" is considered dev junk -> dropped.
function sanitizeBaseUrl(raw) {
  if (!raw) return ''
  var v = String(raw).trim()
  if (v.indexOf('localhost') >= 0 || v.indexOf('127.0.0.1') >= 0 || v.indexOf(':8080') >= 0 || v.indexOf(':5173') >= 0) return ''
  if (v === '/' || v.lastIndexOf('/') === v.length - 1) v = v.slice(0, -1)
  return v
}
var envBase
try { envBase = (typeof import.meta !== 'undefined' && import.meta && import.meta.env) ? import.meta.env.VITE_API_BASE : undefined } catch(e) { envBase = undefined }
var apiBase = sanitizeBaseUrl(envBase)
if (typeof window !== 'undefined') {
  try { console.log('[request] init baseURL=[' + apiBase + '] env=[' + (envBase == null ? 'N/A' : String(envBase)) + '] origin=[' + window.location.origin + ']') } catch(e){}
}

var request = axios.create({
  baseURL: apiBase,
  timeout: 10000
})

request.interceptors.request.use(function (config) {
  var auth = useAuthStore()
  if (auth && auth.token) {
    config.headers.Authorization = 'Bearer ' + auth.token
  }
  // --- LAYER 2 LAST DEFENSE: rewrite any localhost URL to current origin ---
  try {
    var before = String(config.baseURL || '') + String(config.url || '')
    if (before.indexOf('localhost') >= 0 || before.indexOf('127.0.0.1') >= 0) {
      var origin = (typeof window !== 'undefined') ? window.location.origin : ''
      if (origin) {
        var after = before.replace(/https?:\/\/(localhost|127\.0\.0\.1)(:\d+)?/i, origin)
        config.baseURL = ''
        config.url = after
        try { console.warn('[request] SANITIZED URL! before=[' + before + '] after=[' + after + ']') } catch(e){}
      }
    }
  } catch(ee) { /* ignore */ }
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