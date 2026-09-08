// 聊天调试器：保留原 chat-bubble + trace + 检索样本卡，重写为模板样式
export function ChatDebugger({
  messages, input, setInput, loading,
  handleSend, handleKeyDown, messagesEndRef, healthOk,
}) {
  return (
    <section className="bg-white border border-[#E2E8F0] rounded-xl flex flex-col h-[600px]">
      {/* 标题栏 */}
      <div className="flex items-center justify-between px-5 py-4 border-b border-[#E2E8F0]">
        <div>
          <h2 className="text-base font-semibold text-[#0F172A]">调试会话</h2>
          <p className="text-xs text-[#64748B] mt-0.5">
            输入任意内容测试意图识别 + RAG 生成链路
          </p>
        </div>
        <span className="text-xs text-[#64748B] font-mono">
          {messages.length} 条消息
        </span>
      </div>

      {/* 消息区 */}
      <div className="flex-1 overflow-y-auto px-5 py-4 space-y-4">
        {messages.length === 0 && (
          <div className="h-full flex items-center justify-center text-center">
            <div>
              <i className="fas fa-comments text-[#E2E8F0] text-4xl mb-3"></i>
              <p className="text-[#64748B] text-sm">
                {healthOk ? '输入消息开始调试' : '等待后端启动...'}
              </p>
            </div>
          </div>
        )}

        {messages.map((msg, idx) => (
          <div
            key={idx}
            className={`flex gap-3 ${msg.role === 'user' ? 'flex-row-reverse' : ''}`}
          >
            {/* 头像 */}
            <div className={`w-8 h-8 rounded-full flex items-center justify-center text-sm shrink-0 ${
              msg.role === 'user'
                ? 'bg-[#7C3AED] text-white'
                : 'bg-[#F5F3FF] text-[#7C3AED] border border-[#E2E8F0]'
            }`}>
              <i className={`fas ${msg.role === 'user' ? 'fa-user' : 'fa-robot'} text-xs`}></i>
            </div>

            {/* 消息内容 */}
            <div className={`max-w-[80%] ${msg.role === 'user' ? 'items-end' : 'items-start'} flex flex-col gap-2`}>
              {/* 文本气泡 */}
              <div className={`px-4 py-3 rounded-xl text-sm whitespace-pre-wrap break-words ${
                msg.role === 'user'
                  ? 'bg-[#7C3AED] text-white rounded-tr-sm'
                  : 'bg-white border border-[#E2E8F0] text-[#0F172A] rounded-tl-sm'
              }`}>
                {msg.content}
              </div>

              {/* 意图信息 + trace 折叠 */}
              {msg.intentInfo && (
                <details className="bg-[#F8FAFC] border border-[#E2E8F0] rounded-lg px-3 py-2 text-xs">
                  <summary className="cursor-pointer text-[#7C3AED] font-medium list-none">
                    {msg.intentInfo}
                  </summary>
                  {msg.trace && (
                    <div className="mt-2 space-y-1.5">
                      {msg.trace.layers?.map((layer, i) => (
                        <div key={i} className="flex items-center gap-2 text-[#64748B] font-mono text-[11px]">
                          <span className="text-[#0F172A]">{layer.layer}</span>
                          <span className={`px-1.5 py-0.5 rounded text-[10px] ${
                            layer.status === 'hit' ? 'bg-[#10B981]/10 text-[#10B981]' :
                            layer.status === 'miss' ? 'bg-[#94A3B8]/10 text-[#64748B]' :
                            'bg-[#F59E0B]/10 text-[#F59E0B]'
                          }`}>{layer.status}</span>
                          <span>{layer.elapsedMs}ms</span>
                          {layer.detail && <span className="text-[#94A3B8]">· {layer.detail}</span>}
                        </div>
                      ))}
                      <div className="flex justify-between pt-1.5 border-t border-[#E2E8F0] text-[#0F172A]">
                        <span>总耗时</span>
                        <span>{msg.trace.totalMs}ms</span>
                      </div>
                    </div>
                  )}
                </details>
              )}

              {/* 检索样本卡 */}
              {msg.retrievedSamples && msg.retrievedSamples.length > 0 && (
                <div className="space-y-1.5">
                  <div className="text-xs text-[#64748B] flex items-center gap-1.5">
                    <i className="fas fa-book text-[#7C3AED]"></i>
                    检索到 {msg.retrievedSamples.length} 条风格样本
                  </div>
                  {msg.retrievedSamples.map((sample, i) => (
                    <div
                      key={i}
                      className="bg-[#F8FAFC] border border-[#E2E8F0] rounded-lg p-3 text-xs"
                    >
                      <div className="flex items-center justify-between mb-1.5">
                        <span className="text-[#64748B] font-mono">#{sample.index}</span>
                        <span className="text-[#7C3AED] font-mono">
                          相似度 {(sample.score * 100).toFixed(1)}%
                        </span>
                      </div>
                      <p className="text-[#0F172A] line-clamp-2">{sample.text}</p>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        ))}

        {/* 加载指示器 */}
        {loading && (
          <div className="flex gap-3">
            <div className="w-8 h-8 rounded-full bg-[#F5F3FF] text-[#7C3AED] border border-[#E2E8F0] flex items-center justify-center">
              <i className="fas fa-robot text-xs"></i>
            </div>
            <div className="bg-white border border-[#E2E8F0] rounded-xl rounded-tl-sm px-4 py-3">
              <div className="typing flex gap-1">
                <span></span><span></span><span></span>
              </div>
            </div>
          </div>
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* 输入区 */}
      <div className="border-t border-[#E2E8F0] p-4">
        <div className="flex gap-2 items-end">
          <textarea
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={healthOk ? '输入你想分享的事情，例如：今天去爬山了，风景很美...' : '等待后端启动'}
            disabled={loading || !healthOk}
            rows={1}
            className="flex-1 bg-[#F8FAFC] border border-[#E2E8F0] rounded-lg px-3 py-2.5 text-sm resize-none outline-none focus:border-[#7C3AED] focus:bg-white transition-colors placeholder:text-[#94A3B8] disabled:opacity-60"
          />
          <button
            onClick={handleSend}
            disabled={loading || !input.trim() || !healthOk}
            className="bg-[#7C3AED] hover:bg-[#6D28D9] disabled:opacity-40 text-white px-4 py-2.5 rounded-lg text-sm font-medium transition-colors flex items-center gap-1.5"
          >
            <i className="fas fa-paper-plane text-xs"></i>
            发送
          </button>
        </div>
      </div>
    </section>
  )
}
