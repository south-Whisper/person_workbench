import axios from 'axios'
const request = axios.create({ baseURL: import.meta.env.VITE_API_BASE_URL || '/api', timeout: 20000 })
request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})
request.interceptors.response.use(response => response, error => {
  const requestUrl = String(error.config?.url || '')
  const isPublicRequest = requestUrl.includes('/public/')
  const isPublicPage = ['/questionnaire', '/offer-response', '/offer-action'].includes(location.pathname)
  if (error.response?.status === 401 && !requestUrl.includes('/auth/') && !isPublicRequest && !isPublicPage) {
    for (const key of ['token', 'username', 'role']) localStorage.removeItem(key)
    if (location.pathname !== '/login') location.assign('/login?redirect=' + encodeURIComponent(location.pathname + location.search))
  }
  const message = error.response?.data?.message || error.response?.data?.error
  error.message = typeof message === 'string' ? message : error.response ? '请求失败（' + error.response.status + '），请稍后重试' : '暂时无法连接服务，请确认后端已启动后重试'
  return Promise.reject(error)
})
export default request
