// 意图分布表：5 行意图类别 + 命中次数 + 占比 + 最近活动
import { StatusDot } from './StatusDot'

const INTENTS = [
  { name: 'CONTENT_GENERATION', label: '内容创作', status: 'running', icon: 'fa-pen-fancy', lastActivity: '3 分钟前' },
  { name: 'REWRITE', label: '改写润色', status: 'running', icon: 'fa-arrows-rotate', lastActivity: '15 分钟前' },
  { name: 'HOT_SEARCH', label: '热点搜索', status: 'paused', icon: 'fa-fire', lastActivity: '1 小时前' },
  { name: 'SYSTEM_CONTROL', label: '系统控制', status: 'paused', icon: 'fa-gear', lastActivity: '2 小时前' },
  { name: 'CLARIFICATION', label: '澄清反问', status: 'running', icon: 'fa-circle-question', lastActivity: '8 分钟前' },
]

export function IntentTable({ stats }) {
  const counts = stats?.intentCounts ?? {}
  const total = stats?.totalRequests ?? 0

  return (
    <section className="bg-white border border-[#E2E8F0] rounded-xl overflow-hidden">
      <div className="flex items-center justify-between px-5 py-4 border-b border-[#E2E8F0]">
        <h2 className="text-base font-semibold text-[#0F172A]">意图分布</h2>
        <button className="text-xs text-[#7C3AED] hover:underline">查看全部</button>
      </div>

      <table className="w-full text-sm">
        <thead>
          <tr className="text-xs text-[#64748B] border-b border-[#E2E8F0]">
            <th className="text-left font-medium px-5 py-3">意图名称</th>
            <th className="text-right font-medium px-5 py-3">命中次数</th>
            <th className="text-right font-medium px-5 py-3">占比</th>
            <th className="text-left font-medium px-5 py-3 w-32">状态</th>
            <th className="text-right font-medium px-5 py-3">最近活动</th>
          </tr>
        </thead>
        <tbody>
          {INTENTS.map(intent => {
            const count = counts[intent.name] ?? 0
            const pct = total > 0 ? (count / total * 100).toFixed(1) : '0.0'
            return (
              <tr
                key={intent.name}
                className="border-b border-[#E2E8F0] last:border-0 hover:bg-[#F5F3FF] transition-colors duration-200"
              >
                <td className="px-5 py-3.5">
                  <div className="flex items-center gap-2.5">
                    <i className={`fas ${intent.icon} text-[#7C3AED] text-xs w-4 text-center`}></i>
                    <div>
                      <div className="text-[#0F172A]">{intent.label}</div>
                      <div className="text-xs text-[#94A3B8] font-mono">{intent.name}</div>
                    </div>
                  </div>
                </td>
                <td className="px-5 py-3.5 text-right font-mono text-[#0F172A]">
                  {count}
                </td>
                <td className="px-5 py-3.5 text-right font-mono text-[#0F172A]">
                  {pct}%
                </td>
                <td className="px-5 py-3.5">
                  <div className="flex items-center gap-2">
                    <StatusDot status={intent.status} />
                    <span className="text-xs text-[#64748B]">
                      {intent.status === 'running' ? '活跃' : intent.status === 'paused' ? '待命' : '异常'}
                    </span>
                  </div>
                </td>
                <td className="px-5 py-3.5 text-right text-xs text-[#64748B]">
                  {total === 0 ? '—' : intent.lastActivity}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </section>
  )
}
