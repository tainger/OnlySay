import { useState } from 'react'
import './App.css'
import { Sidebar } from './components/Sidebar'
import { Topbar } from './components/Topbar'
import { KpiCards } from './components/KpiCards'
import { TrendChart } from './components/TrendChart'
import { IntentTable } from './components/IntentTable'
import { ChatDebugger } from './components/ChatDebugger'
import { useChat } from './hooks/useChat'
import { useStats } from './hooks/useStats'
import { useHealth } from './hooks/useHealth'

function App() {
  const [activeNav, setActiveNav] = useState('overview')
  const healthOk = useHealth()
  const { stats, refresh } = useStats(healthOk)
  const {
    messages, input, setInput, loading, ingesting, ingestStatus,
    handleSend, handleIngest, handleKeyDown, messagesEndRef,
  } = useChat()

  return (
    <div className="flex h-screen bg-[#F8FAFC]">
      <Sidebar
        activeNav={activeNav}
        onNavChange={setActiveNav}
        ingesting={ingesting}
        ingestStatus={ingestStatus}
        onIngest={handleIngest}
      />
      <div className="flex-1 flex flex-col min-w-0">
        <Topbar healthOk={healthOk} onRefresh={refresh} />

        {!healthOk && (
          <div className="bg-[#FEF3C7] border-b border-[#F59E0B]/30 px-6 py-2 text-sm text-[#92400E] flex items-center gap-2">
            <i className="fas fa-triangle-exclamation"></i>
            后端未连接（http://localhost:8080），请启动后端服务
          </div>
        )}

        <main className="flex-1 overflow-y-auto p-6 space-y-5">
          <KpiCards stats={stats} />
          <TrendChart stats={stats} />
          <IntentTable stats={stats} />
          <ChatDebugger
            messages={messages}
            input={input}
            setInput={setInput}
            loading={loading}
            handleSend={handleSend}
            handleKeyDown={handleKeyDown}
            messagesEndRef={messagesEndRef}
            healthOk={healthOk}
          />
        </main>
      </div>
    </div>
  )
}

export default App
