// 迷你折线图：KPI 卡底部 7 天趋势
// 100x30 SVG，无坐标轴，仅 path + 浅填充区
export function Sparkline({ data, color = '#7C3AED' }) {
  if (!data || data.length === 0) {
    return <svg width="100" height="30" viewBox="0 0 100 30" />
  }

  const max = Math.max(...data, 1)
  const min = Math.min(...data, 0)
  const range = max - min || 1
  const step = data.length > 1 ? 100 / (data.length - 1) : 100

  // 折线 path
  const linePoints = data.map((v, i) => {
    const x = i * step
    const y = 28 - ((v - min) / range) * 24
    return `${x},${y}`
  })
  const linePath = `M ${linePoints.join(' L ')}`
  // 浅填充区 path
  const fillPath = `${linePath} L 100,30 L 0,30 Z`

  return (
    <svg width="100" height="30" viewBox="0 0 100 30" className="sparkline-svg">
      <path d={fillPath} fill={color} fillOpacity="0.12" />
      <path
        d={linePath}
        fill="none"
        stroke={color}
        strokeWidth="1.5"
        strokeLinejoin="round"
        strokeLinecap="round"
        className="sparkline-path"
      />
    </svg>
  )
}
