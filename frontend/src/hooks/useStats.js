// 意图识别指标 hook：mount 拉取 + 15s 轮询 + 页面可见时刷新
import { useState, useEffect, useCallback } from 'react'
import { fetchStats } from '../api'

const POLL_INTERVAL = 15000

export function useStats(healthOk) {
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    if (!healthOk) {
      setLoading(false)
      return
    }
    try {
      const data = await fetchStats()
      setStats(data)
    } catch (e) {
      // 静默失败：不刷屏，下次轮询继续
    } finally {
      setLoading(false)
    }
  }, [healthOk])

  useEffect(() => {
    refresh()
    if (!healthOk) return

    const timer = setInterval(refresh, POLL_INTERVAL)
    const onVisible = () => {
      if (!document.hidden) refresh()
    }
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      clearInterval(timer)
      document.removeEventListener('visibilitychange', onVisible)
    }
  }, [refresh, healthOk])

  return { stats, loading, refresh }
}
