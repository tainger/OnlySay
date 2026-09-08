import { useState, useRef, useEffect } from 'react'
import './App.css'

const API_BASE = 'http://localhost:8080'

// 会话 ID：同一浏览器会话内复用，支撑跨轮槽位继承与意图切换
const SESSION_KEY = 'onlysay-session-id'
function getSessionId() {
  let id = localStorage.getItem(SESSION_KEY)
  if (!id) {
    id = crypto.randomUUID()
    localStorage.setItem(SESSION_KEY, id)
  }
  return id
}

// 意图信息摘要行：意图 + 命中层级 + 置信度
function formatIntentInfo(data) {
  if (!data.intent) return null
  const slots = data.slots && Object.keys(data.slots).length > 0
    ? ` | 槽位: ${Object.entries(data.slots).map(([k, v]) => `${k}=${v}`).join(', ')}`
    : ''
  return `🎯 ${data.intent} · ${data.hitLayer}${slots}`
}

function App() {
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [ingesting, setIngesting] = useState(false)
  const [ingestStatus, setIngestStatus] = useState(null)
  const messagesEndRef = useRef(null)

  // 自动滚动到底部
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  // 录入样本
  const handleIngest = async () => {
    setIngesting(true)
    setIngestStatus('录入中...')
    try {
      const res = await fetch(`${API_BASE}/api/ingest`, { method: 'POST' })
      const data = await res.json()
      if (data.success) {
        setIngestStatus(`✅ 录入完成，共 ${data.totalRecords} 条样本`)
      } else {
        setIngestStatus(`❌ ${data.message}`)
      }
    } catch (e) {
      setIngestStatus(`❌ 连接后端失败: ${e.message}`)
    } finally {
      setIngesting(false)
    }
  }

  // 发送消息
  const handleSend = async () => {
    const text = input.trim()
    if (!text || loading) return

    // 添加用户消息
    setMessages(prev => [...prev, { role: 'user', content: text }])
    setInput('')
    setLoading(true)

    try {
      const res = await fetch(`${API_BASE}/api/generate`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ userInput: text, sessionId: getSessionId() })
      })
      const data = await res.json()

      if (data.clarification) {
        // 澄清反问：渲染为 AI 消息
        setMessages(prev => [...prev, {
          role: 'assistant',
          content: data.message,
          intentInfo: formatIntentInfo(data)
        }])
      } else if (data.success && data.generatedText) {
        // 正常生成
        setMessages(prev => [...prev, {
          role: 'assistant',
          content: data.generatedText,
          retrievedSamples: data.retrievedSamples,
          intentInfo: formatIntentInfo(data)
        }])
      } else if (data.intent && data.intent !== 'CONTENT_GENERATION') {
        // 已识别但不支持的意图（占位响应）
        setMessages(prev => [...prev, {
          role: 'assistant',
          content: `🚧 ${data.message}`,
          intentInfo: formatIntentInfo(data)
        }])
      } else {
        setMessages(prev => [...prev, {
          role: 'assistant',
          content: `⚠️ ${data.message}`
        }])
      }
    } catch (e) {
      setMessages(prev => [...prev, {
        role: 'assistant',
        content: `❌ 连接后端失败: ${e.message}`
      }])
    } finally {
      setLoading(false)
    }
  }

  // 回车键发送
  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  return (
    <div className="app">
      {/* 顶栏 */}
      <header className="header">
        <div className="header-left">
          <h1>OnlySay RAG 调试台</h1>
          <span className="subtitle">博主风格 → 检索 → 生成</span>
        </div>
        <div className="header-right">
          <button
            className="ingest-btn"
            onClick={handleIngest}
            disabled={ingesting}
          >
            {ingesting ? '录入中...' : '录入样本'}
          </button>
          {ingestStatus && (
            <span className="ingest-status">{ingestStatus}</span>
          )}
        </div>
      </header>

      {/* 消息区 */}
      <main className="messages">
        {messages.length === 0 && (
          <div className="welcome">
            <h2>👋 欢迎使用 OnlySay 调试台</h2>
            <p>1. 点击右上角「录入样本」加载博主风格样本</p>
            <p>2. 在下方输入你想分享的事情，按回车发送</p>
            <p>3. AI 会检索相关风格样本并生成同风格文案</p>
          </div>
        )}

        {messages.map((msg, idx) => (
          <div key={idx} className={`message ${msg.role}`}>
            <div className="message-avatar">
              {msg.role === 'user' ? '🧑' : '🤖'}
            </div>
            <div className="message-content">
              <div className="message-text">{msg.content}</div>

              {/* 意图识别信息 + trace 折叠展示 */}
              {msg.intentInfo && (
                <details className="intent-section">
                  <summary className="intent-summary">{msg.intentInfo}</summary>
                  {msg.trace && (
                    <div className="trace-list">
                      {msg.trace.layers?.map((layer, i) => (
                        <div key={i} className="trace-item">
                          <span className="trace-layer">{layer.layer}</span>
                          <span className={`trace-status trace-${layer.status}`}>{layer.status}</span>
                          <span className="trace-elapsed">{layer.elapsedMs}ms</span>
                          {layer.detail && <span className="trace-detail">{layer.detail}</span>}
                        </div>
                      ))}
                      <div className="trace-item trace-total">
                        <span>总耗时</span>
                        <span>{msg.trace.totalMs}ms</span>
                      </div>
                    </div>
                  )}
                </details>
              )}

              {/* 检索详情 */}
              {msg.retrievedSamples && msg.retrievedSamples.length > 0 && (
                <div className="retrieval-section">
                  <div className="retrieval-header">
                    📚 检索到的风格样本 ({msg.retrievedSamples.length})
                  </div>
                  {msg.retrievedSamples.map((sample, i) => (
                    <div key={i} className="retrieval-card">
                      <div className="retrieval-meta">
                        <span className="retrieval-index">样本 {sample.index}</span>
                        <span className="retrieval-score">
                          相似度: {(sample.score * 100).toFixed(1)}%
                        </span>
                      </div>
                      <div className="retrieval-text">{sample.text}</div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        ))}

        {loading && (
          <div className="message assistant">
            <div className="message-avatar">🤖</div>
            <div className="message-content">
              <div className="typing">
                <span></span><span></span><span></span>
              </div>
            </div>
          </div>
        )}
        <div ref={messagesEndRef} />
      </main>

      {/* 输入区 */}
      <footer className="input-area">
        <div className="input-wrapper">
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="输入你想分享的事情，例如：今天去爬山了，风景很美..."
            disabled={loading}
            rows={1}
          />
          <button
            className="send-btn"
            onClick={handleSend}
            disabled={loading || !input.trim()}
          >
            发送
          </button>
        </div>
      </footer>
    </div>
  )
}

export default App
