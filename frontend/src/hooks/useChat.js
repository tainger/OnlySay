// 聊天状态 hook：迁移自原 App.jsx，handleSend/handleIngest 逻辑保持不变
import { useState, useRef, useEffect } from 'react'
import { postGenerate, postIngest, getSessionId, formatIntentInfo } from '../api'

export function useChat() {
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [ingesting, setIngesting] = useState(false)
  const [ingestStatus, setIngestStatus] = useState(null)
  const messagesEndRef = useRef(null)

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages])

  const handleIngest = async () => {
    setIngesting(true)
    setIngestStatus('录入中...')
    try {
      const data = await postIngest()
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

  const handleSend = async () => {
    const text = input.trim()
    if (!text || loading) return

    setMessages(prev => [...prev, { role: 'user', content: text }])
    setInput('')
    setLoading(true)

    try {
      const data = await postGenerate(text, getSessionId())

      if (data.clarification) {
        setMessages(prev => [...prev, {
          role: 'assistant',
          content: data.message,
          intentInfo: formatIntentInfo(data),
          trace: data.trace,
        }])
      } else if (data.success && data.generatedText) {
        setMessages(prev => [...prev, {
          role: 'assistant',
          content: data.generatedText,
          retrievedSamples: data.retrievedSamples,
          intentInfo: formatIntentInfo(data),
          trace: data.trace,
        }])
      } else if (data.intent && data.intent !== 'CONTENT_GENERATION') {
        setMessages(prev => [...prev, {
          role: 'assistant',
          content: `🚧 ${data.message}`,
          intentInfo: formatIntentInfo(data),
          trace: data.trace,
        }])
      } else {
        setMessages(prev => [...prev, {
          role: 'assistant',
          content: `⚠️ ${data.message}`,
        }])
      }
    } catch (e) {
      setMessages(prev => [...prev, {
        role: 'assistant',
        content: `❌ 连接后端失败: ${e.message}`,
      }])
    } finally {
      setLoading(false)
    }
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  return {
    messages, input, setInput, loading, ingesting, ingestStatus,
    handleSend, handleIngest, handleKeyDown, messagesEndRef,
  }
}
