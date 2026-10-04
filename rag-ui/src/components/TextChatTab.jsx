import { useEffect, useRef, useState } from "react";
import { Send } from "lucide-react";
import { askQuestion, getChatHistory } from "../api/chatApi";

function formatTime(dateInput) {
  const date = dateInput instanceof Date ? dateInput : new Date(dateInput);
  if (Number.isNaN(date.getTime())) return "";
  return date.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
}

function toBubble(apiMessage) {
  const time = formatTime(apiMessage.createdAt);

  const meta =
    apiMessage.role === "assistant" && apiMessage.model
      ? `${time} · ${apiMessage.model} · ${apiMessage.totalTokens} tokens · ${apiMessage.latencyMs}ms`
      : time;

  return { role: apiMessage.role, content: apiMessage.content, meta };
}

const TextChatTab = ({ chatbotId }) => {
  const [messages, setMessages] = useState([]);
  const [loadingHistory, setLoadingHistory] = useState(true);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const scrollRef = useRef(null);

  useEffect(() => {
    setLoadingHistory(true);
    getChatHistory(chatbotId)
      .then((res) => setMessages((res.data.data ?? []).map(toBubble)))
      .catch(() => setMessages([]))
      .finally(() => setLoadingHistory(false));
  }, [chatbotId]);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, sending]);

  const handleSend = async () => {
    const text = input.trim();
    if (!text || sending) return;

    setInput("");
    setMessages((prev) => [
      ...prev,
      { role: "user", content: text, meta: formatTime(new Date()) },
    ]);
    setSending(true);

    try {
      const res = await askQuestion(chatbotId, text);
      const { answer, model, totalTokens, latency, createdAt } = res.data.data;

      setMessages((prev) => [
        ...prev,
        {
          role: "assistant",
          content: answer,
          meta: `${formatTime(createdAt)} · ${model} · ${totalTokens} tokens · ${latency}ms`,
        },
      ]);
    } catch (err) {
      setMessages((prev) => [
        ...prev,
        {
          role: "assistant",
          content:
            err.response?.data?.message ||
            "Something went wrong answering that. Try again.",
          isError: true,
        },
      ]);
    } finally {
      setSending(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="flex h-[420px] flex-col gap-3.5">
      <div ref={scrollRef} className="flex flex-1 flex-col gap-3 overflow-y-auto pr-1">
        {loadingHistory ? (
          <div className="flex flex-1 items-center justify-center text-[13px] text-ink-muted">
            Loading conversation&hellip;
          </div>
        ) : messages.length === 0 ? (
          <div className="flex flex-1 items-center justify-center text-center text-[13px] text-ink-muted">
            Ask this chatbot anything about the documents you&rsquo;ve uploaded.
          </div>
        ) : (
          messages.map((m, i) => (
            <div
              key={i}
              className={`flex max-w-[72%] flex-col gap-1 ${
                m.role === "user" ? "self-end items-end" : "self-start items-start"
              }`}
            >
              <div
                className={`rounded-xl px-3.5 py-2.5 text-[14px] leading-relaxed ${
                  m.role === "user"
                    ? "rounded-br-[3px] bg-accent text-accent-ink"
                    : m.isError
                    ? "rounded-bl-[3px] border border-danger/20 bg-danger-soft text-danger"
                    : "rounded-bl-[3px] bg-surface-2 text-ink"
                }`}
              >
                {m.content}
              </div>
              {m.meta && (
                <div className="font-mono text-[11px] text-ink-muted">{m.meta}</div>
              )}
            </div>
          ))
        )}

        {sending && (
          <div className="self-start rounded-xl rounded-bl-[3px] bg-surface-2 px-3.5 py-2.5 text-[13px] text-ink-muted">
            Thinking&hellip;
          </div>
        )}
      </div>

      <div className="flex items-center gap-2 rounded-[10px] border border-border bg-surface py-1.5 pl-3.5 pr-1.5">
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Ask about this chatbot&rsquo;s documents&hellip;"
          className="flex-1 bg-transparent text-[14px] text-ink outline-none placeholder:text-ink-muted"
        />
        <button
          type="button"
          onClick={handleSend}
          disabled={sending || !input.trim()}
          className="flex items-center gap-1.5 rounded-[8px] bg-accent px-3 py-2 text-[13px] font-medium text-accent-ink disabled:opacity-50"
        >
          <Send className="h-3.5 w-3.5" /> Send
        </button>
      </div>
    </div>
  );
};

export default TextChatTab;
