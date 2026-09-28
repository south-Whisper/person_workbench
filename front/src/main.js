import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

import { ElDialog } from 'element-plus'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/message/style/css'

const app = createApp(App)
app.component('ElDialog', ElDialog)
app.use(router).mount('#app')
