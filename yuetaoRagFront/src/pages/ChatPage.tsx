import { FormEvent, KeyboardEvent, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import ReactMarkdown from "react-markdown";
import {
  BookOpen,
  Bot,
  Brain,
  ChevronDown,
  CircleStop,
  LogOut,
  Menu,
  MessageSquare,
  MessageSquarePlus,
  Search,
  Send,
  Trash2,
  UserRound
} from "lucide-react";

import { Button } from "@/components/Button";
import { ThinkingIndicator } from "@/components/ThinkingIndicator";
import { useAuthStore } from "@/stores/authStore";
import { useChatStore } from "@/stores/chatStore";
import { formatConversationTime } from "@/utils/format";

export function ChatPage() {
  const navigate = useNavigate();
  const { sessionId } = useParams();
  const {
    sessions,
    messages,
    currentSessionId,
    isLoading,
    isStreaming,
    useStreaming,
    deepThinkingEnabled,
    setUseStreaming,
    setDeepThinkingEnabled,
    fetchSessions,
    selectSession,
    startNewSession,
    deleteSession,
    send
  } = useChatStore();
  const { user, logout } = useAuthStore();
  const [input, setInput] = useState("");
  const [query, setQuery] = useState("");
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [thinkingExpanded, setThinkingExpanded] = useState<Record<string, boolean>>({});
  const [thinkingElapsedMs, setThinkingElapsedMs] = useState(0);
  const thinkingStartRef = useRef<number | null>(null);
  const messageListRef = useRef<HTMLDivElement | null>(null);

  const hasActiveThinking = messages.some((m) => m.isThinking);
  useEffect(() => {
    if (hasActiveThinking) {
      thinkingStartRef.current = thinkingStartRef.current ?? Date.now();
      setThinkingElapsedMs(Date.now() - thinkingStartRef.current);
      const timer = setInterval(() => {
        setThinkingElapsedMs(Date.now() - (thinkingStartRef.current ?? Date.now()));
      }, 1000);
      return () => clearInterval(timer);
    }
    thinkingStartRef.current = null;
    setThinkingElapsedMs(0);
  }, [hasActiveThinking]);

  useEffect(() => {
    fetchSessions();
  }, [fetchSessions]);

  useEffect(() => {
    if (sessionId && sessionId !== currentSessionId) {
      selectSession(sessionId);
    }
  }, [currentSessionId, selectSession, sessionId]);

  useEffect(() => {
    if (!sessionId && currentSessionId) {
      navigate(`/chat/${currentSessionId}`, { replace: true });
    }
  }, [currentSessionId, navigate, sessionId]);

  useEffect(() => {
    const node = messageListRef.current;
    if (!node) return;
    node.scrollTo({ top: node.scrollHeight, behavior: "smooth" });
  }, [messages.length, messages[messages.length - 1]?.content, messages[messages.length - 1]?.thinking, isStreaming]);

  const currentSession = sessions.find((session) => session.id === currentSessionId);
  const filteredSessions = useMemo(() => {
    const keyword = query.trim().toLowerCase();
    if (!keyword) return sessions;
    return sessions.filter((session) => (session.title || "新对话").toLowerCase().includes(keyword));
  }, [query, sessions]);
  const groupedSessions = useMemo(() => {
    const groups = new Map<string, typeof sessions>();
    filteredSessions.forEach((session) => {
      const label = resolveSessionGroup(session.lastActiveAt);
      groups.set(label, [...(groups.get(label) || []), session]);
    });
    return ["今天", "7天内", "30天内", "更早"].map((label) => ({ label, items: groups.get(label) || [] })).filter((group) => group.items.length);
  }, [filteredSessions]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const next = input.trim();
    if (!next) return;
    setInput("");
    await send(next);
  }

  function handleInputKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key !== "Enter" || event.shiftKey || event.nativeEvent.isComposing) return;
    event.preventDefault();
    if (!input.trim() || isStreaming) return;
    event.currentTarget.form?.requestSubmit();
  }

  function handleNewSession() {
    startNewSession();
    navigate("/chat");
  }

  async function handleDeleteSession(targetSessionId: string, title: string) {
    if (!window.confirm(`确认删除「${title || "新对话"}」？`)) return;
    const deletingCurrent = targetSessionId === currentSessionId;
    await deleteSession(targetSessionId);
    if (deletingCurrent) {
      navigate("/chat", { replace: true });
    }
  }

  return (
    <div className="chat-shell">
      <button
        className={`chat-backdrop ${sidebarOpen ? "visible" : ""}`}
        type="button"
        aria-label="关闭侧边栏"
        onClick={() => setSidebarOpen(false)}
      />
      <aside className={`chat-sidebar ${sidebarOpen ? "open" : ""}`}>
        <div className="chat-brand">
          <span className="chat-brand-mark">
            <Bot size={20} />
          </span>
          <span>
            <strong>YueTao RAG</strong>
            <small>Powered by AI</small>
          </span>
        </div>
        <div className="quick-start-card">
          <div className="quick-card-head">
            <span>快速开始</span>
            <em>新内容</em>
          </div>
          <button type="button" className="new-chat-card" onClick={handleNewSession}>
            <span>
              <MessageSquarePlus size={17} />
            </span>
            <strong>新建对话</strong>
            <small>从空白开始</small>
          </button>
          <button type="button" className="chat-admin-entry" onClick={() => navigate("/admin/knowledge")}>
            <span>
              <BookOpen size={15} />
            </span>
            <strong>知识库与文档管理</strong>
          </button>
        </div>
        <div className="session-search-card">
          <div className="quick-card-head">
            <span>搜索对话</span>
            <small>Ctrl / Cmd + K</small>
          </div>
          <label className="session-search">
            <Search size={16} />
            <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="搜索对话..." />
          </label>
        </div>
        <div className="session-list">
          {groupedSessions.length === 0 ? (
            <div className="session-empty">
              <MessageSquare size={52} />
              <p>暂无对话记录</p>
            </div>
          ) : (
            groupedSessions.map((group) => (
              <section key={group.label} className="session-group">
                <p>{group.label}</p>
                {group.items.map((session) => (
                  <div
                    key={session.id}
                    className={`session-item ${session.id === currentSessionId ? "active" : ""}`}
                    role="button"
                    tabIndex={0}
                    onClick={() => {
                      navigate(`/chat/${session.id}`);
                      setSidebarOpen(false);
                    }}
                    onKeyDown={(event) => {
                      if (event.key === "Enter") {
                        navigate(`/chat/${session.id}`);
                        setSidebarOpen(false);
                      }
                    }}
                  >
                    <span>
                      <strong>{session.title || "新对话"}</strong>
                      <small>{formatConversationTime(session.lastActiveAt)}</small>
                    </span>
                    <button
                      type="button"
                      className="session-delete"
                      aria-label="删除会话"
                      onClick={(event) => {
                        event.stopPropagation();
                        handleDeleteSession(session.id, session.title).catch(() => null);
                      }}
                    >
                      <Trash2 size={15} />
                    </button>
                  </div>
                ))}
              </section>
            ))
          )}
        </div>
        <button className="chat-user-menu" type="button" onClick={() => logout()}>
          <span>{(user?.displayName || user?.username || "用户").slice(0, 1).toUpperCase()}</span>
          <strong>{user?.displayName || user?.username || "用户"}</strong>
          <LogOut size={16} />
        </button>
      </aside>
      <section className="chat-main">
        <header className="chat-header">
          <button className="mobile-menu" type="button" onClick={() => setSidebarOpen(true)} aria-label="打开侧边栏">
            <Menu size={20} />
          </button>
          <h1>{currentSession?.title || "新对话"}</h1>
        </header>
        <div className="message-list" ref={messageListRef}>
          {messages.length === 0 && !isLoading ? (
            <div className="welcome-screen">
              <span className="welcome-icon">
                <Bot size={28} />
              </span>
              <h2>今天想了解什么？</h2>
              <p>发送问题后，系统会创建会话并调用当前后端 RAG 接口。</p>
            </div>
          ) : null}
          {messages.map((message) => (
            <article key={message.id} className={`message-row ${message.role}`}>
              <div className="message-avatar">{message.role === "assistant" ? <Bot size={18} /> : <UserRound size={18} />}</div>
              <div className="message-bubble">
                {message.role === "assistant" && message.isThinking ? (
                  <ThinkingIndicator content={message.thinking} durationMs={thinkingElapsedMs} />
                ) : null}
                {message.role === "assistant" && !message.isThinking && message.thinking ? (
                  <div className="thinking-accordion">
                    <button
                      type="button"
                      className="thinking-accordion-header"
                      onClick={() =>
                        setThinkingExpanded((prev) => ({
                          ...prev,
                          [message.id]: !prev[message.id]
                        }))
                      }
                    >
                      <span className="thinking-accordion-icon">
                        <Brain size={16} />
                      </span>
                      <span>深度思考</span>
                      {message.thinkingDurationMs ? (
                        <span className="thinking-duration">{Math.round(message.thinkingDurationMs / 1000)}秒</span>
                      ) : null}
                      <ChevronDown
                        size={16}
                        style={{ transform: thinkingExpanded[message.id] ? "rotate(180deg)" : "none", transition: "transform 0.2s" }}
                      />
                    </button>
                    {thinkingExpanded[message.id] ? (
                      <div className="thinking-expanded-content">{message.thinking}</div>
                    ) : null}
                  </div>
                ) : null}
                {message.role === "assistant" && message.status === "streaming" && !message.isThinking && !message.content ? (
                  <div className="thinking-wait-dots" aria-label="思考中">
                    <span className="thinking-wait-dot" />
                    <span className="thinking-wait-dot" />
                    <span className="thinking-wait-dot" />
                  </div>
                ) : null}
                {message.content ? <ReactMarkdown>{message.content}</ReactMarkdown> : null}
                {message.citations?.length ? (
                  <div className="citation-list">
                    {message.citations.map((citation) => (
                      <span key={`${citation.index}-${citation.chunkId}`}>{citation.referenceLabel || citation.documentTitle}</span>
                    ))}
                  </div>
                ) : null}
              </div>
            </article>
          ))}
        </div>
        <form className="chat-input-bar" onSubmit={handleSubmit}>
          <div className="chat-input-box">
            <textarea
              value={input}
              onChange={(event) => setInput(event.target.value)}
              onKeyDown={handleInputKeyDown}
              placeholder={deepThinkingEnabled ? "输入需要深度分析的问题..." : "输入问题，按发送调用后端 RAG..."}
              disabled={isStreaming}
            />
            <div className="chat-input-footer">
              <div className="chat-input-controls">
                <button
                  type="button"
                  className={`thinking-toggle ${deepThinkingEnabled ? "active" : ""}`}
                  onClick={() => setDeepThinkingEnabled(!deepThinkingEnabled)}
                  disabled={isStreaming}
                >
                  <Brain size={14} />
                  <span>深度思考</span>
                  {deepThinkingEnabled ? <span className="thinking-dot" /> : null}
                </button>
                <label className="stream-toggle">
                  <input type="checkbox" checked={useStreaming} onChange={(event) => setUseStreaming(event.target.checked)} />
                  <span>{useStreaming ? "流式生成" : "普通生成"}</span>
                </label>
              </div>
              {deepThinkingEnabled ? (
                <span className="thinking-hint">深度思考模式已开启，AI将进行更深入的分析推理</span>
              ) : (
                <span>Enter 发送 / Shift + Enter 换行</span>
              )}
            </div>
          </div>
          <Button className="send-btn" type="submit" disabled={isStreaming || !input.trim()}>
            {isStreaming ? <CircleStop size={18} /> : <Send size={18} />}
          </Button>
        </form>
      </section>
    </div>
  );
}

function resolveSessionGroup(value?: string | null) {
  const time = value ? new Date(value).getTime() : Date.now();
  const diff = Math.max(0, Date.now() - (Number.isNaN(time) ? Date.now() : time));
  const days = diff / 86400000;
  if (days < 1) return "今天";
  if (days <= 7) return "7天内";
  if (days <= 30) return "30天内";
  return "更早";
}
