import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/dashboard' },
    { path: '/login', component: Login },
    { path: '/questionnaire', component: () => import('../views/Questionnaire.vue') },
    { path: '/offer-response', component: () => import('../views/OfferResponse.vue') },
    { path: '/offer-action', component: () => import('../views/OfferAction.vue') },
    { path: '/dashboard', component: () => import('../views/Dashboard.vue'), meta: { requireAuth: true } },
    { path: '/talents', component: () => import('../views/Dashboard.vue'), meta: { requireAuth: true } },
    { path: '/inbox', component: () => import('../views/Inbox.vue'), meta: { requireAuth: true } },
    { path: '/person/:id', component: () => import('../views/PersonDetail.vue'), meta: { requireAuth: true } },
    { path: '/positions', component: () => import('../views/Positions.vue'), meta: { requireAuth: true } },
    { path: '/settings', component: () => import('../views/Settings.vue'), meta: { requireAuth: true } },
    { path: '/updates', component: () => import('../views/UpdateHistory.vue'), meta: { requireAuth: true } },
    ...['recruitment', 'communications', 'offers', 'onboarding', 'interviews', 'employees'].map(path => ({ path: '/' + path, component: () => import('../views/Workspace.vue'), meta: { requireAuth: true } })),
    { path: '/:pathMatch(.*)*', component: () => import('../views/NotFound.vue'), meta: { requireAuth: true } }
  ],
  scrollBehavior: () => ({ top: 0 })
})
router.beforeEach(to => {
  const token = localStorage.getItem('token')
  if (to.meta.requireAuth && !token) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.path === '/login' && token && to.query.preserveAuth !== '1') return '/dashboard'
})
const routeReloadKey = 'person-workbench-route-reload'
router.onError((error, to) => {
  const message = String(error?.message || error)
  const isStalePage = /dynamically imported module|module script|preload/i.test(message)
  if (!isStalePage) return
  const lastReload = Number(sessionStorage.getItem(routeReloadKey) || 0)
  if (Date.now() - lastReload < 10000) return
  sessionStorage.setItem(routeReloadKey, String(Date.now()))
  location.assign(to?.fullPath || location.pathname + location.search)
})
export default router
