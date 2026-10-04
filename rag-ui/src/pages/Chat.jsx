import { useEffect, useRef, useState } from "react";
import { useParams, Link } from "react-router-dom";
import toast from "react-hot-toast";
import { ArrowLeft, FileText, Trash2, Upload } from "lucide-react";
import { getChatBotById } from "../api/chatbotApi";
import { getDocuments, uploadDocument, deleteDocument } from "../api/documentApi";
import TextChatTab from "../components/TextChatTab";
import VoiceChatTab from "../components/VoiceChatTab";

const TABS = [
  { key: "documents", label: "Documents" },
  { key: "text", label: "Text" },
  { key: "voice", label: "Voice" },
];

const Chat = () => {
  const { id } = useParams();

  const [bot, setBot] = useState(null);
  const [tab, setTab] = useState("documents");

  const [documents, setDocuments] = useState([]);
  const [docsLoading, setDocsLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [deletingId, setDeletingId] = useState(null);
  const fileInputRef = useRef(null);

  useEffect(() => {
    getChatBotById(id)
      .then((res) => setBot(res.data.data))
      .catch(() => toast.error("Could not load this chatbot."));
  }, [id]);

  const loadDocuments = () => {
    setDocsLoading(true);
    getDocuments(id)
      .then((res) => setDocuments(res.data.data ?? []))
      .catch(() => toast.error("Could not load documents."))
      .finally(() => setDocsLoading(false));
  };

  useEffect(() => {
    loadDocuments();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const handleFileChange = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;

    try {
      setUploading(true);
      await uploadDocument(id, file);
      toast.success("Document uploaded and indexed.");
      loadDocuments();
    } catch (err) {
      toast.error(err.response?.data?.message || "Upload failed.");
    } finally {
      setUploading(false);
    }
  };

  const handleDelete = async (doc) => {
    if (!window.confirm(`Delete "${doc.documentName}"? This removes it and its embeddings.`)) {
      return;
    }

    try {
      setDeletingId(doc.id);
      await deleteDocument(id, doc.id);
      toast.success("Document deleted.");
      setDocuments((prev) => prev.filter((d) => d.id !== doc.id));
    } catch (err) {
      toast.error(err.response?.data?.message || "Could not delete the document.");
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <div className="mx-auto flex max-w-[1080px] flex-col gap-5 px-7 py-7">
      <Link to="/dashboard" className="flex w-fit items-center gap-1.5 text-[13px] text-ink-muted hover:text-accent">
        <ArrowLeft className="h-3.5 w-3.5" /> All chatbots
      </Link>

      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="font-display text-[21px] font-semibold text-ink">
            {bot?.name ?? "—"}
          </h1>
          {bot?.description && (
            <p className="mt-1 max-w-[52ch] text-[13.5px] text-ink-muted">{bot.description}</p>
          )}
        </div>
        <span className="rounded-[5px] border border-border bg-surface-2 px-1.5 py-0.5 font-mono text-[11px] text-ink-muted">
          chatbot_{id}
        </span>
      </div>

      <div className="flex gap-1 border-b border-border">
        {TABS.map((t) => (
          <button
            key={t.key}
            type="button"
            onClick={() => setTab(t.key)}
            className={`-mb-px border-b-2 px-3.5 py-2.5 text-[13.5px] font-medium ${
              tab === t.key
                ? "border-accent text-accent"
                : "border-transparent text-ink-muted"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {/*
        All three panels stay mounted and are just hidden/shown - not
        conditionally rendered - so switching tabs doesn't unmount
        TextChatTab and wipe its in-memory message list. The backend
        still remembers the conversation either way (sessionId persists
        in localStorage), but the visible chat log would reset on every
        tab switch without this.
      */}
      <div hidden={tab !== "documents"}>
        <div className="pt-1">
          <input
            ref={fileInputRef}
            type="file"
            accept=".pdf,.doc,.docx,.txt"
            className="hidden"
            onChange={handleFileChange}
          />
          <button
            type="button"
            disabled={uploading}
            onClick={() => fileInputRef.current?.click()}
            className="mb-4 flex w-full flex-col items-center gap-1.5 rounded-xl border-[1.5px] border-dashed border-border py-6 text-[13.5px] text-ink-muted disabled:opacity-60"
          >
            <Upload className="h-5 w-5" />
            <span className="font-medium text-ink">
              {uploading ? "Uploading…" : "Click to upload a document"}
            </span>
            <span>PDF, DOC, DOCX, or TXT &mdash; extracted and embedded automatically</span>
          </button>

          {docsLoading ? (
            <div className="py-6 text-center text-[13px] text-ink-muted">Loading&hellip;</div>
          ) : documents.length === 0 ? (
            <div className="py-6 text-center text-[13px] text-ink-muted">
              No documents uploaded yet.
            </div>
          ) : (
            <div className="flex flex-col gap-2">
              {documents.map((doc) => (
                <div
                  key={doc.id}
                  className="flex items-center justify-between gap-3 rounded-[10px] border border-border bg-surface px-3.5 py-3"
                >
                  <div className="flex min-w-0 items-center gap-2.5">
                    <FileText className="h-4 w-4 flex-none text-ink-muted" />
                    <div className="min-w-0">
                      <div className="truncate text-[13.5px] font-medium text-ink">
                        {doc.documentName}
                      </div>
                      <div className="font-mono text-[11.5px] text-ink-muted">
                        {doc.chunkCount} chunks &middot; {doc.documentType}
                      </div>
                    </div>
                  </div>
                  <div className="flex flex-none items-center gap-2.5">
                    <span className="rounded-full bg-success-soft px-2 py-0.5 font-mono text-[11px] text-success">
                      indexed
                    </span>
                    <button
                      type="button"
                      disabled={deletingId === doc.id}
                      onClick={() => handleDelete(doc)}
                      aria-label={`Delete ${doc.documentName}`}
                      className="text-ink-muted hover:text-danger disabled:opacity-50"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      <div hidden={tab !== "text"}>
        <TextChatTab chatbotId={id} />
      </div>

      <div hidden={tab !== "voice"}>
        <VoiceChatTab chatbotId={id} />
      </div>
    </div>
  );
};

export default Chat;
