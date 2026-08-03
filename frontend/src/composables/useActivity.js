import { ref } from 'vue'
import { recordActivity as apiRecord, getRecentAccess as apiGetAccess, getRecentOperations as apiGetOps } from '@/api'

const STORAGE_KEY_ACCESS = 'ck_plm_recent_access'
const STORAGE_KEY_OPERATIONS = 'ck_plm_recent_operations'
const MAX_ITEMS = 10

const recentAccess = ref(loadLocalAccess())
const recentOperations = ref(loadLocalOps())

function loadLocalAccess() {
  try { return JSON.parse(localStorage.getItem(STORAGE_KEY_ACCESS) || '[]') }
  catch { return [] }
}
function loadLocalOps() {
  try { return JSON.parse(localStorage.getItem(STORAGE_KEY_OPERATIONS) || '[]') }
  catch { return [] }
}
function saveLocalAccess(data) {
  localStorage.setItem(STORAGE_KEY_ACCESS, JSON.stringify(data.slice(0, MAX_ITEMS)))
}
function saveLocalOps(data) {
  localStorage.setItem(STORAGE_KEY_OPERATIONS, JSON.stringify(data.slice(0, MAX_ITEMS)))
}

/** 获取相对时间描述 */
function relativeTime(isoString) {
  if (!isoString) return ''
  const diff = Date.now() - new Date(isoString).getTime()
  const s = Math.floor(diff / 1000)
  if (s < 60) return '刚刚'
  const m = Math.floor(s / 60)
  if (m < 60) return `${m}分钟前`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h}小时前`
  if (h < 48) return '昨天'
  return `${Math.floor(h / 24)}天前`
}

function typeColor(type) {
  const map = { '产品系列': 'blue', '产品型号': 'purple', '文档': 'blue', '变更单': 'orange', '企业资源': 'green', '流程': 'cyan', '系统': 'default' }
  return map[type] || 'default'
}
function opColor(action) {
  if (!action) return '#8c8c8c'
  if (action.includes('登录')) return 'green'
  if (action.includes('注销')) return '#8c8c8c'
  if (action.includes('检出')) return 'orange'
  if (action.includes('检入')) return 'green'
  if (action.includes('创建')) return 'blue'
  if (action.includes('删除')) return 'red'
  if (action.includes('编辑')) return 'blue'
  return '#8c8c8c'
}

/** 合并远端+本地数据，去重，排序 */
function mergeAccess(local, remote) {
  const map = new Map()
  for (const a of [...local, ...remote]) {
    const key = a.targetPath || a.path || a.name
    if (!map.has(key) || new Date(a.time) > new Date(map.get(key).time)) {
      map.set(key, a)
    }
  }
  return [...map.values()].sort((a, b) => new Date(b.time) - new Date(a.time)).slice(0, MAX_ITEMS)
}

function mergeOps(local, remote) {
  const all = [...local, ...remote]
  return all.sort((a, b) => new Date(b.time) - new Date(a.time)).slice(0, MAX_ITEMS)
}

/**
 * 记录一次页面访问（同时写本地+远程）
 */
export async function recordAccess(page) {
  const now = new Date().toISOString()
  const item = { name: page.name, type: page.type, path: page.path, time: now, typeColor: typeColor(page.type) }
  const local = loadLocalAccess()
  local.unshift(item)
  saveLocalAccess(local)
  recentAccess.value = mergeAccess(loadLocalAccess(), [])

  // 异步写远程（不阻塞）
  try {
    await apiRecord({
      activityType: 'ACCESS',
      targetName: page.name,
      targetType: page.type,
      targetPath: page.path
    })
  } catch { /* 静默失败 */ }
}

/**
 * 记录一次操作（同时写本地+远程）
 */
export async function recordOperation(op) {
  const now = new Date().toISOString()
  const item = { ...op, id: Date.now(), time: now, color: opColor(op.action) }
  const local = loadLocalOps()
  local.unshift(item)
  saveLocalOps(local)
  recentOperations.value = mergeOps(loadLocalOps(), [])

  try {
    await apiRecord({
      activityType: 'OPERATION',
      targetName: op.target,
      actionDesc: op.action
    })
  } catch { /* 静默失败 */ }
}

/** 从远端拉取并合并数据 */
export async function fetchFromServer() {
  try {
    const [accessRes, opsRes] = await Promise.all([apiGetAccess(), apiGetOps()])
    if (accessRes.code === 200) {
      const remote = accessRes.data.map(a => ({ ...a, typeColor: typeColor(a.type) }))
      recentAccess.value = mergeAccess(loadLocalAccess(), remote)
    }
    if (opsRes.code === 200) {
      const remote = opsRes.data.map(a => ({ ...a, color: opColor(a.action) }))
      recentOperations.value = mergeOps(loadLocalOps(), remote)
    }
  } catch { /* 静默失败，使用本地数据 */ }
}

/** 获取最近访问（含相对时间） */
export function getRecentAccess() {
  return recentAccess.value.map(a => ({ ...a, displayTime: relativeTime(a.time) }))
}

/** 获取最近操作（含相对时间） */
export function getRecentOperations() {
  return recentOperations.value.map(a => ({ ...a, displayTime: relativeTime(a.time) }))
}

/**
 * 记录登录（本地写一份，远端异步写）
 */
export async function recordLogin(username) {
  const now = new Date().toISOString()
  const item = { action: '用户登录', target: username, id: Date.now(), time: now, color: 'green' }
  const local = loadLocalOps()
  local.unshift(item)
  saveLocalOps(local)
  recentOperations.value = mergeOps(loadLocalOps(), [])

  try {
    await apiRecord({
      activityType: 'LOGIN',
      actionDesc: '用户登录',
      targetName: username,
      targetType: '系统',
      result: 'SUCCESS'
    })
  } catch { /* 静默失败 */ }
}

/**
 * 记录注销
 */
export async function recordLogout(username) {
  const now = new Date().toISOString()
  const item = { action: '用户注销', target: username, id: Date.now(), time: now, color: '#8c8c8c' }
  const local = loadLocalOps()
  local.unshift(item)
  saveLocalOps(local)
  recentOperations.value = mergeOps(loadLocalOps(), [])

  try {
    await apiRecord({
      activityType: 'LOGOUT',
      actionDesc: '用户注销',
      targetName: username,
      targetType: '系统',
      result: 'SUCCESS'
    })
  } catch { /* 静默失败 */ }
}

export { recentAccess, recentOperations }
