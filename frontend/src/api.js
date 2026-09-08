// API 封装：统一基础地址、会话 ID 管理、fetch 助手

export const API_BASE = 'http://localhost:8080'

const SESSION_KEY = 'onlysay-session-id'

// 会话 ID：同一浏览器会话内复用，支撑跨轮槽位继承与意图切换
export function getSessionId() {
  let id = localStorage.getItem(SESSION_KEY)
  if (!id) {
    id = crypto.randomUUID()
    localStorage.setItem(SESSION_KEY, id)
  }
  return id
}

// 通用 JSON fetch 封装
async function fetchJson(url, options = {}) {
  const res = await fetch(url, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
  })
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`)
  }
  return res.json()
}

// 生成文案（含意图识别前置路由）
export const postGenerate = (userInput, sessionId) =>
  fetchJson(`${API_BASE}/api/generate`, {
    method: 'POST',
    body: JSON.stringify({ userInput, sessionId }),
  })

// 录入样本
export const postIngest = () =>
  fetchJson(`${API_BASE}/api/ingest`, { method: 'POST' })

// 纯意图识别（调试端点）
export const postIntent = (userInput, sessionId) =>
  fetchJson(`${API_BASE}/api/intent`, {
    method: 'POST',
    body: JSON.stringify({ userInput, sessionId }),
  })

// 识别指标快照
export const fetchStats = () => fetchJson(`${API_BASE}/api/intent/stats`)

// 健康检查
export const fetchHealth = () => fetchJson(`${API_BASE}/api/health`)

// 意图信息摘要行：意图 + 命中层级 + 槽位
export function formatIntentInfo(data) {
  if (!data.intent) return null
  const slots = data.slots && Object.keys(data.slots).length > 0
    ? ` | 槽位: ${Object.entries(data.slots).map(([k, v]) => `${k}=${v}`).join(', ')}`
    : ''
  return `🎯 ${data.intent} · ${data.hitLayer}${slots}`
}
