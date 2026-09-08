// 状态点：彩色圆点 + 呼吸动画（运行中绿点）
export function StatusDot({ status }) {
  // status: running | paused | error
  const color = {
    running: 'bg-[#10B981]',
    paused: 'bg-[#94A3B8]',
    error: 'bg-[#EF4444]',
  }[status] || 'bg-[#94A3B8]'

  return (
    <span className={`inline-block w-2 h-2 rounded-full ${color} ${status === 'running' ? 'status-dot-pulse' : ''}`}></span>
  )
}
