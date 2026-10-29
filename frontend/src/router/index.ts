import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import PublicLayout from '@/layouts/PublicLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: PublicLayout,
      children: [
        { path: '', name: 'home', component: () => import('@/views/public/Home.vue'), meta: { title: '首页' } },
        { path: 'market', name: 'market', component: () => import('@/views/public/Market.vue'), meta: { title: '成品库' } },
        { path: 'market/:bundleId', name: 'app-detail', component: () => import('@/views/public/AppDetail.vue'), meta: { title: '应用详情' } },
        { path: 'pricing', redirect: '/docs' },
        { path: 'docs', name: 'docs', component: () => import('@/views/public/Docs.vue'), meta: { title: '文档' } }
      ]
    },
    { path: '/login', component: () => import('@/views/auth/Login.vue'), meta: { bare: true } },
    { path: '/register', component: () => import('@/views/auth/Register.vue'), meta: { bare: true } },
    { path: '/forgot', component: () => import('@/views/auth/Forgot.vue'), meta: { bare: true, title: '找回密码' } },
    {
      path: '/app',
      component: () => import('@/layouts/ConsoleLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/app/dashboard' },
        { path: 'dashboard', component: () => import('@/views/console/Dashboard.vue'), meta: { title: '概览' } },
        { path: 'new', component: () => import('@/views/console/NewTask.vue'), meta: { title: '新建脱壳' } },
        { path: 'tasks', component: () => import('@/views/console/Tasks.vue'), meta: { title: '任务' } },
        { path: 'tasks/:id', component: () => import('@/views/console/TaskDetail.vue'), meta: { title: '任务详情' } },
        { path: 'library', component: () => import('@/views/console/Library.vue'), meta: { title: '我的应用' } },
        { path: 'wallet', redirect: '/app/dashboard' },
        { path: 'settings', component: () => import('@/views/console/Settings.vue'), meta: { title: '账号设置' } }
      ]
    },
    {
      path: '/admin',
      component: () => import('@/layouts/AdminLayout.vue'),
      meta: { requiresAuth: true, requiresAdmin: true },
      children: [
        { path: '', redirect: '/admin/overview' },
        { path: 'overview', component: () => import('@/views/admin/Overview.vue'), meta: { title: '团队概览' } },
        { path: 'users', component: () => import('@/views/admin/Users.vue'), meta: { title: '用户管理' } },
        { path: 'tasks', component: () => import('@/views/admin/Tasks.vue'), meta: { title: '脱壳任务' } },
        { path: 'apps', component: () => import('@/views/admin/Apps.vue'), meta: { title: '应用库' } },
        { path: 'orders', redirect: '/admin/overview' },
        { path: 'pricing', redirect: '/admin/system' },
        { path: 'apple-ids', component: () => import('@/views/admin/AppleIds.vue'), meta: { title: 'Apple ID 池' } },
        { path: 'system', component: () => import('@/views/admin/System.vue'), meta: { title: '系统设置' } },
        { path: 'logs', component: () => import('@/views/admin/Logs.vue'), meta: { title: '审计日志' } }
      ]
    },
    { path: '/:pathMatch(.*)*', component: () => import('@/views/NotFound.vue'), meta: { bare: true } }
  ],
  scrollBehavior(to, from, saved) {
    if (saved) return saved
    if (to.hash) return { el: to.hash, top: 80, behavior: 'smooth' }
    return { top: 0 }
  }
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!auth.ready) {
    try {
      await auth.bootstrap()
    } catch {
      auth.ready = true
    }
  }

  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && !auth.isAdmin) {
    return { path: '/app/dashboard' }
  }
  if ((to.path === '/login' || to.path === '/register') && auth.isLoggedIn) {
    return { path: auth.isAdmin ? '/admin/overview' : '/app/dashboard' }
  }
})

router.afterEach((to) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · AppReseal` : 'AppReseal — iOS IPA 脱壳'
})

export default router
