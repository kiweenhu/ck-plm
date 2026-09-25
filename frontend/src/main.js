import { createApp } from 'vue'
import { createPinia } from 'pinia'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import App from './App.vue'
import router from './router'
import './style.css'
import { installDevErrorOverlay } from './utils/dev-error-overlay'

const app = createApp(App)
const pinia = createPinia()

// 开发期把渲染/异步错误直接画在页面上：避免「内容区整片空白、控制台一堆错误」无从下手
if (import.meta.env.DEV) {
  installDevErrorOverlay(app)
}

app.use(pinia)
app.use(Antd)
app.use(router)
app.mount('#app')
