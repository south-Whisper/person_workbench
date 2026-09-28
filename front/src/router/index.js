import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', component: Login },
    { path: '/dashboard', component: () => import('../views/Dashboard.vue'), meta: { requireAuth: true } },
    { path: '/talents', component: () => import('../views/Dashboard.vue'), meta: { requireAuth: true } },
    { path: '/person/:id', component: () => import('../views/PersonDetail.vue'), meta: { requireAuth: true } },
    { path: '/positions', component: () => import('../views/Positions.vue'), meta: { requireAuth: true } },
    { path: '/settings', component: () => import('../views/Settings.vue'), meta: { requireAuth: true } },
    ...['opportunities', 'recruitment', 'interviews', 'employees', 'collaborations', 'tasks', 'analytics'].map(path => ({ path: '/' + path, component: () => import('../views/Workspace.vue'), meta: { requireAuth: true } })),
    { path: '/:pathMatch(.*)*', component: () => import('../views/NotFound.vue'), meta: { requireAuth: true } }
  ],
  scrollBehavior: () => ({ top: 0 })
})
router.beforeEach(to => {
  const token = localStorage.getItem('token')
  if (to.meta.requireAuth && !token) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.path === '/login' && token && to.query.preserveAuth !== '1') return '/dashboard'
})
export default router
