// 健康检查 hook：30s 探测后端
import { useState, useEffect } from 'react'
import { fetchHealth } from '../api'

const POLL_INTERVAL = 30000

export function useHealth() {
  const [ok, setOk] = useState(false)

  useEffect(() => {
    let cancelled = false

    const probe = async () => {
      try {
        await fetchHealth()
        if (!cancelled) setOk(true)
      } catch {
        if (!cancelled) setOk(false)
      }
    }

    probe()
    const timer = setInterval(probe, POLL_INTERVAL)
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [])

  return ok
}
