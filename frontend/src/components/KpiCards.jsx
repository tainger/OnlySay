// KPI 卡片行：4 卡 grid（调用数/P95延迟/澄清率/LLM兜底率）
import { Sparkline } from './Sparkline'

// 根据当前 totalRequests 反推 7 天 mock 趋势（衰减）
function buildSparklineData(currentValue, seedKey) {
  // 用 seedKey 让每个卡片有自己的衰减曲线，避免完全一致
  const seed = seedKey.charCodeAt(0)
  const data = []
  let v = currentValue
  for (let i = 0; i < 7; i++) {
    // 最新值在末尾
    const factor = 0.65 + ((seed * (i + 3)) % 30) / 100  // 0.65-0.95
    data.unshift(Math.max(0, Math.round(v * factor)))
    v = v * factor
  }
  data[6] = currentValue  // 最新值
  return data
}

function KpiCard({ icon, iconBg, label, value, unit, trend, trendUp, trendGood, sparkData, sparkColor }) {
  return (
    <div className="bg-white border border-[#E2E8F0] rounded-xl p-4 transition-colors hover:border-[#7C3AED]">
      <div className="flex items-start justify-between mb-3">
        {/* 图标 */}
        <div className={`w-9 h-9 rounded-lg flex items-center justify-center ${iconBg}`}>
          <i className={`fas ${icon} text-white text-sm`}></i>
        </div>
        {/* 环比 */}
        {trend && (
          <span className={`text-xs font-mono ${trendGood ? 'text-[#10B981]' : 'text-[#EF4444]'}`}>
            <i className={`fas ${trendUp ? 'fa-arrow-up' : 'fa-arrow-down'} text-[10px] mr-0.5`}></i>
            {trend}
          </span>
        )}
      </div>
      {/* 大数字 */}
      <div className="font-mono text-2xl font-bold text-[#0F172A] mb-1">
        {value}<span className="text-sm text-[#64748B] font-medium ml-0.5">{unit}</span>
      </div>
      {/* 标签 */}
      <div className="text-xs text-[#64748B] mb-2">{label}</div>
      {/* sparkline */}
      <Sparkline data={sparkData} color={sparkColor} />
    </div>
  )
}

export function KpiCards({ stats }) {
  const total = stats?.totalRequests ?? 0
  const p95 = stats?.p95LatencyMs ?? stats?.p95Ms ?? 0
  const clarRate = stats?.clarificationRate ?? 0
  const llmRate = stats?.llmFallbackRate ?? 0

  const empty = !stats || total === 0

  return (
    <section className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
      <KpiCard
        icon="fa-route"
        iconBg="bg-[#7C3AED]"
        label="意图识别总数"
        value={total}
        unit="次"
        sparkData={empty ? [0, 0, 0, 0, 0, 0, 0] : buildSparklineData(total, 'a')}
        sparkColor="#7C3AED"
      />
      <KpiCard
        icon="fa-gauge-high"
        iconBg="bg-[#10B981]"
        label="P95 延迟"
        value={p95}
        unit="ms"
        trend={empty ? null : '-8%'}
        trendUp={false}
        trendGood={true}
        sparkData={empty ? [0, 0, 0, 0, 0, 0, 0] : buildSparklineData(Math.max(p95, 50), 'b')}
        sparkColor="#10B981"
      />
      <KpiCard
        icon="fa-comments"
        iconBg="bg-[#F59E0B]"
        label="澄清触发率"
        value={(clarRate * 100).toFixed(1)}
        unit="%"
        trend={empty ? null : (clarRate > 0.05 ? '+0.3%' : '-0.2%')}
        trendUp={clarRate > 0.05}
        trendGood={clarRate <= 0.05}
        sparkData={empty ? [0, 0, 0, 0, 0, 0, 0] : buildSparklineData(Math.max(Math.round(clarRate * 100), 5), 'c')}
        sparkColor="#F59E0B"
      />
      <KpiCard
        icon="fa-layer-group"
        iconBg="bg-[#7C3AED]"
        label="LLM 兜底率"
        value={(llmRate * 100).toFixed(1)}
        unit="%"
        trend={empty ? null : '-2%'}
        trendUp={false}
        trendGood={true}
        sparkData={empty ? [0, 0, 0, 0, 0, 0, 0] : buildSparklineData(Math.max(Math.round(llmRate * 100), 5), 'd')}
        sparkColor="#7C3AED"
      />
    </section>
  )
}
