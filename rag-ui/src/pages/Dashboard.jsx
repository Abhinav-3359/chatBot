import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import toast from "react-hot-toast";
import { MessageSquare, Plus, Search, X } from "lucide-react";
import { getAllChatBots, createChatBot } from "../api/chatbotApi";

// Cycled across chatbot card icons for visual variety - same 4-color
// system as the approved preview (gold accent, warning, teal, rose).
const ICON_STYLES = [
  "bg-accent-soft text-accent",
  "bg-warning-soft text-warning",
  "bg-tag-teal-soft text-tag-teal",
  "bg-tag-rose-soft text-tag-rose",
];
const LINK_STYLES = ["text-accent", "text-warning", "text-tag-teal", "text-tag-rose"];

const CreateChatbotModal = ({ onClose, onCreated }) => {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm();
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  const onSubmit = async (data) => {
    try {
      setSubmitting(true);
      setError("");
      await createChatBot({ name: data.name, description: data.description });
      toast.success("Chatbot created.");
      onCreated();
    } catch (err) {
      setError(err.response?.data?.message || "Could not create the chatbot.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/40 px-4">
      <div className="w-full max-w-sm rounded-2xl border border-border bg-surface p-6 shadow-[0_1px_2px_rgba(28,35,64,.06),0_10px_28px_rgba(28,35,64,.08)]">
        <div className="flex items-center justify-between">
          <h2 className="font-display text-lg font-semibold text-ink">New chatbot</h2>
          <button type="button" onClick={onClose} className="text-ink-muted">
            <X className="h-4 w-4" />
          </button>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="mt-4 flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <label className="text-[12.5px] font-medium text-ink">Name</label>
            <input
              className="rounded-[10px] border border-border bg-canvas px-3 py-2.5 text-[14px] text-ink outline-none focus:border-accent"
              placeholder="e.g. Portfolio Assistant"
              {...register("name", { required: "Give it a name" })}
            />
            {errors.name && <p className="text-[12.5px] text-danger">{errors.name.message}</p>}
          </div>

          <div className="flex flex-col gap-1.5">
            <label className="text-[12.5px] font-medium text-ink">Description</label>
            <textarea
              rows={3}
              className="resize-none rounded-[10px] border border-border bg-canvas px-3 py-2.5 text-[14px] text-ink outline-none focus:border-accent"
              placeholder="What should this chatbot know about?"
              {...register("description")}
            />
          </div>

          {error && (
            <p className="rounded-lg border border-danger/20 bg-danger-soft px-3 py-2 text-[12.5px] text-danger">
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={submitting}
            className="rounded-[10px] bg-accent px-4 py-2.5 text-[14px] font-medium text-accent-ink disabled:opacity-60"
          >
            {submitting ? "Creating…" : "Create chatbot"}
          </button>
        </form>
      </div>
    </div>
  );
};

const Dashboard = () => {
  const navigate = useNavigate();
  const [chatbots, setChatbots] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [showCreate, setShowCreate] = useState(false);

  const loadChatbots = () => {
    setLoading(true);
    getAllChatBots()
      .then((res) => setChatbots(res.data.data ?? []))
      .catch(() => toast.error("Could not load your chatbots."))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    loadChatbots();
  }, []);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return chatbots;
    return chatbots.filter((bot) => bot.name?.toLowerCase().includes(q));
  }, [chatbots, search]);

  return (
    <div className="mx-auto flex max-w-[1080px] flex-col gap-5 px-7 py-7">
      <div>
        <h1 className="font-display text-[25px] font-semibold tracking-tight text-ink">
          Your chatbots
        </h1>
        <p className="mt-1 text-[13.5px] text-ink-muted">
          Each one is its own knowledge base &mdash; create one, upload documents, then talk to it
          by text or voice.
        </p>
      </div>

      <div className="flex items-center justify-between gap-3">
        <div className="flex max-w-[320px] flex-1 items-center gap-2 rounded-[9px] border border-border bg-surface px-3 py-2">
          <Search className="h-4 w-4 text-ink-muted" />
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search your chatbots&hellip;"
            className="w-full bg-transparent text-[13.5px] text-ink outline-none placeholder:text-ink-muted"
          />
        </div>
        <button
          type="button"
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-1.5 rounded-[9px] bg-accent px-3.5 py-2 text-[13.5px] font-medium text-accent-ink"
        >
          <Plus className="h-4 w-4" /> Create chatbot
        </button>
      </div>

      {loading ? (
        <div className="py-16 text-center text-[13.5px] text-ink-muted">Loading&hellip;</div>
      ) : chatbots.length === 0 ? (
        <div className="flex flex-col items-center gap-3 rounded-2xl border border-dashed border-border py-16 text-center">
          <MessageSquare className="h-7 w-7 text-ink-muted" />
          <div className="text-[14px] font-medium text-ink">No chatbots yet</div>
          <p className="max-w-[36ch] text-[13px] text-ink-muted">
            Create your first one, then upload documents it should know about.
          </p>
          <button
            type="button"
            onClick={() => setShowCreate(true)}
            className="mt-1 rounded-[9px] bg-accent px-4 py-2 text-[13.5px] font-medium text-accent-ink"
          >
            + Create chatbot
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-3.5 sm:grid-cols-2 lg:grid-cols-3">
          {filtered.map((bot, i) => (
            <button
              key={bot.id}
              type="button"
              onClick={() => navigate(`/chatbot/${bot.id}`)}
              className="flex flex-col gap-3 rounded-xl border border-border bg-surface p-4 text-left shadow-[0_1px_2px_rgba(28,35,64,.06),0_10px_28px_rgba(28,35,64,.04)]"
            >
              <div className="flex items-start gap-2.5">
                <div
                  className={`flex h-9 w-9 flex-none items-center justify-center rounded-[9px] ${
                    ICON_STYLES[i % ICON_STYLES.length]
                  }`}
                >
                  <MessageSquare className="h-4 w-4" />
                </div>
                <h3 className="pt-1.5 text-[14.5px] font-semibold text-ink">{bot.name}</h3>
              </div>
              {bot.description && (
                <div className="text-[12.5px] leading-relaxed text-ink-muted">
                  {bot.description}
                </div>
              )}
              <div className="flex items-center justify-between border-t border-border pt-2.5">
                <span className="rounded-[5px] border border-border bg-surface-2 px-1.5 py-0.5 font-mono text-[11px] text-ink-muted">
                  chatbot_{bot.id}
                </span>
                <span className={`text-[12.5px] font-medium ${LINK_STYLES[i % LINK_STYLES.length]}`}>
                  Open workspace &rarr;
                </span>
              </div>
            </button>
          ))}
        </div>
      )}

      {showCreate && (
        <CreateChatbotModal
          onClose={() => setShowCreate(false)}
          onCreated={() => {
            setShowCreate(false);
            loadChatbots();
          }}
        />
      )}
    </div>
  );
};

export default Dashboard;
