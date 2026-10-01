import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

import { ElDatePicker, ElDialog } from 'element-plus'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/date-picker/style/css'
import 'element-plus/es/components/message/style/css'

const preloadReloadKey = 'person-workbench-preload-reload'
window.addEventListener('vite:preloadError', event => {
  event.preventDefault()
  const lastReload = Number(sessionStorage.getItem(preloadReloadKey) || 0)
  if (Date.now() - lastReload < 10000) return
  sessionStorage.setItem(preloadReloadKey, String(Date.now()))
  location.reload()
})

const app = createApp(App)
app.component('ElDialog', ElDialog)
app.component('ElDatePicker', ElDatePicker)
app.use(router).mount('#app')
