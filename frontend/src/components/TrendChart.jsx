// 调用量趋势图卡：纯 SVG 7 天折线，不用图表库
// 数据基于当前 totalRequests mock 7 天衰减曲线

const W = 760
const H = 240
const PADDING_X = 40
const PADDING_Y = 24
const CHART_W = W - PADDING_X * 2
const CHART_H = H - PADDING_Y * 2

// 生成 7 天日期标签（today 是 2026-09-08）
function getDates() {
  const dates = []
  const today = new Date('2026-09-08T00:00:00')
  for (let i = 6; i >= 0; i--) {
    const d = new Date(today)
    d.setDate(d.getDate() - i)
    dates.push(`${d.getMonth() + 1}/${d.getDate()}`)
  }
  return dates
}

// 基于 currentTotal mock 7 天数据（最新值在末尾）
function buildTrendData(currentTotal) {
  const data = []
  let v = Math.max(currentTotal, 10)
  for (let i = 0; i < 7; i++) {
    const factor = 0.55 + ((i * 7) % 35) / 100  // 0.55-0.90
    data.unshift(Math.max(0, Math.round(v * factor)))
    v = v * factor
  }
  data[6] = Math.max(currentTotal, 10)
  return data
}

export function TrendChart({ stats }) {
  const total = stats?.totalRequests ?? 0
  const current = buildTrendData(total)
  const lastWeek = current.map(v => Math.round(v * 0.85))

  const allValues = [...current, ...lastWeek, 0]
  const maxVal = Math.max(...allValues, 10)

  const dates = getDates()

  // 计算 path 坐标
  const toX = (i) => PADDING_X + (i / 6) * CHART_W
  const toY = (v) => PADDING_Y + CHART_H - (v / maxVal) * CHART_H

  const currentPoints = current.map((v, i) => `${toX(i)},${toY(v)}`)
  const currentLine = `M ${currentPoints.join(' L ')}`
  const currentFill = `${currentLine} L ${toX(6)},${PADDING_Y + CHART_H} L ${toX(0)},${PADDING_Y + CHART_H} Z`

  const lastWeekPoints = lastWeek.map((v, i) => `${toX(i)},${toY(v)}`)
  const lastWeekLine = `M ${lastWeekPoints.join(' L ')}`

  // Y 轴刻度（4 格）
  const yTicks = [0, maxVal / 4, maxVal / 2, (maxVal * 3) / 4, maxVal].map(v => ({
    value: Math.round(v),
    y: toY(v),
  }))

  return (
    <section className="bg-white border border-[#E2E8F0] rounded-xl p-5">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-base font-semibold text-[#0F172A]">近 7 日调用量</h2>
        <div className="flex items-center gap-4 text-xs">
          <span className="flex items-center gap-1.5">
            <span className="w-3 h-0.5 bg-[#7C3AED]"></span>
            <span className="text-[#64748B]">调用量</span>
          </span>
          <span className="flex items-center gap-1.5">
            <span className="w-3 h-0.5 border-t border-dashed border-[#94A3B8]"></span>
            <span className="text-[#64748B]">上周</span>
          </span>
        </div>
      </div>

      <svg viewBox={`0 0 ${W} ${H}`} className="w-full h-48">
        {/* 网格线 */}
        {yTicks.map((tick, i) => (
          <g key={i}>
            <line
              x1={PADDING_X}
              y1={tick.y}
              x2={W - PADDING_X}
              y2={tick.y}
              stroke="#E2E8F0"
              strokeWidth="1"
              strokeDasharray="2,3"
            />
            <text
              x={PADDING_X - 8}
              y={tick.y + 4}
              textAnchor="end"
              fontSize="10"
              fill="#94A3B8"
              fontFamily="JetBrains Mono"
            >
              {tick.value}
            </text>
          </g>
        ))}

        {/* X 轴日期 */}
        {dates.map((date, i) => (
          <text
            key={i}
            x={toX(i)}
            y={H - 6}
            textAnchor="middle"
            fontSize="10"
            fill="#94A3B8"
            fontFamily="Inter"
          >
            {date}
          </text>
        ))}

        {/* 上周折线（虚线灰） */}
        <path
          d={lastWeekLine}
          fill="none"
          stroke="#94A3B8"
          strokeWidth="1.5"
          strokeDasharray="4,4"
          strokeLinejoin="round"
        />

        {/* 本周填充区 */}
        <path d={currentFill} fill="#7C3AED" fillOpacity="0.08" />

        {/* 本周折线 */}
        <path
          d={currentLine}
          fill="none"
          stroke="#7C3AED"
          strokeWidth="2"
          strokeLinejoin="round"
          strokeLinecap="round"
          className="sparkline-path"
        />

        {/* hover 节点 */}
        {current.map((v, i) => (
          <circle
            key={i}
            cx={toX(i)}
            cy={toY(v)}
            r="3"
            fill="white"
            stroke="#7C3AED"
            strokeWidth="2"
          />
        ))}
      </svg>
    </section>
  )
}
