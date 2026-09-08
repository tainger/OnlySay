// 左侧固定侧边栏：logo + 录入 CTA + 6 项导航 + 底部用户卡
const NAV_ITEMS = [
  { id: 'overview', label: '概览', icon: 'fa-gauge-high' },
  { id: 'intent', label: '意图识别', icon: 'fa-route' },
  { id: 'samples', label: '样本库', icon: 'fa-book' },
  { id: 'sessions', label: '会话', icon: 'fa-message' },
  { id: 'metrics', label: '指标', icon: 'fa-chart-line' },
  { id: 'settings', label: '设置', icon: 'fa-gear' },
]

export function Sidebar({ activeNav, onNavChange, ingesting, ingestStatus, onIngest }) {
  return (
    <aside className="hidden md:flex md:w-60 w-14 flex-col bg-white border-r border-[#E2E8F0] h-screen shrink-0">
      {/* Logo */}
      <div className="h-16 flex items-center gap-2 px-4 border-b border-[#E2E8F0]">
        <span className="text-[#7C3AED] text-xl">◆</span>
        <div className="hidden md:block">
          <span className="font-bold text-[#0F172A] text-sm">OnlySay</span>
          <span className="text-[#94A3B8] text-xs ml-1">Console</span>
        </div>
      </div>

      {/* 录入 CTA */}
      <div className="px-3 pt-3 pb-2 border-b border-[#E2E8F0]">
        <button
          onClick={onIngest}
          disabled={ingesting}
          className="w-full flex items-center justify-center gap-2 bg-[#7C3AED] hover:bg-[#6D28D9] disabled:opacity-50 text-white text-sm font-medium py-2.5 rounded-lg transition-colors"
        >
          <i className="fas fa-database text-xs"></i>
          <span className="hidden md:inline">{ingesting ? '录入中...' : '录入样本'}</span>
        </button>
        {ingestStatus && (
          <div className="hidden md:block text-xs text-[#64748B] mt-1.5 px-1 truncate">
            {ingestStatus}
          </div>
        )}
      </div>

      {/* 导航 */}
      <nav className="flex-1 py-3 px-2 space-y-1 overflow-y-auto">
        {NAV_ITEMS.map(item => {
          const active = activeNav === item.id
          return (
            <button
              key={item.id}
              onClick={() => onNavChange(item.id)}
              className={`w-full relative flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-colors ${
                active
                  ? 'bg-[#7C3AED] text-white font-medium'
                  : 'text-[#0F172A] hover:bg-[#F5F3FF]'
              }`}
            >
              {active && <span className="absolute left-0 top-0 h-full w-[3px] bg-[#7C3AED] rounded-r-sm" />}
              <i className={`fas ${item.icon} text-sm w-4 text-center`}></i>
              <span className="hidden md:inline">{item.label}</span>
            </button>
          )
        })}
      </nav>

      {/* 用户卡 */}
      <div className="border-t border-[#E2E8F0] p-3">
        <div className="flex items-center gap-2.5">
          <img
            src="https://picsum.photos/seed/onlysay-admin/64/64"
            alt="用户"
            className="w-8 h-8 rounded-full shrink-0"
          />
          <div className="hidden md:flex flex-col min-w-0 flex-1">
            <span className="text-sm text-[#0F172A] truncate">林安然</span>
            <span className="text-xs text-[#64748B]">管理员</span>
          </div>
          <button className="hidden md:flex text-[#94A3B8] hover:text-[#0F172A] text-sm">
            <i className="fas fa-right-from-bracket"></i>
          </button>
        </div>
      </div>
    </aside>
  )
}
