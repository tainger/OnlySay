// 顶部栏：面包屑 + 搜索 + 铃铛 + 头像
export function Topbar({ healthOk, onRefresh }) {
  return (
    <header className="h-16 sticky top-0 z-10 flex items-center justify-between px-6 bg-white border-b border-[#E2E8F0]">
      {/* 面包屑 */}
      <div className="flex items-center gap-2 text-sm">
        <span className="text-[#64748B]">控制台</span>
        <i className="fas fa-chevron-right text-[10px] text-[#94A3B8]"></i>
        <span className="text-[#0F172A] font-medium">总览</span>
      </div>

      {/* 搜索 */}
      <div className="hidden md:flex items-center gap-2 bg-[#F8FAFC] border border-[#E2E8F0] rounded-lg px-3 py-1.5 w-80">
        <i className="fas fa-magnifying-glass text-[#94A3B8] text-xs"></i>
        <input
          type="text"
          placeholder="搜索意图、样本..."
          className="bg-transparent border-0 outline-none text-sm flex-1 placeholder:text-[#94A3B8]"
        />
        <kbd className="text-[10px] text-[#94A3B8] bg-white border border-[#E2E8F0] rounded px-1.5 py-0.5 font-mono">⌘K</kbd>
      </div>

      {/* 右侧 */}
      <div className="flex items-center gap-3">
        {/* 健康状态 */}
        <button
          onClick={onRefresh}
          className="text-[#64748B] hover:text-[#7C3AED] text-sm flex items-center gap-1.5"
          title={healthOk ? '后端正常' : '后端未连接'}
        >
          <span className={`inline-block w-2 h-2 rounded-full ${healthOk ? 'bg-[#10B981]' : 'bg-[#EF4444]'}`}></span>
          <span className="hidden md:inline">{healthOk ? '在线' : '离线'}</span>
        </button>

        <span className="hidden md:inline w-px h-5 bg-[#E2E8F0]"></span>

        <button className="relative text-[#64748B] hover:text-[#7C3AED] text-sm">
          <i className="far fa-bell"></i>
          <span className="absolute -top-1 -right-1.5 bg-[#7C3AED] text-white text-[10px] min-w-[16px] h-4 rounded-full flex items-center justify-center px-1">3</span>
        </button>

        <img
          src="https://picsum.photos/seed/onlysay-admin/64/64"
          alt="用户"
          className="w-7 h-7 rounded-full border border-[#E2E8F0]"
        />
      </div>
    </header>
  )
}
