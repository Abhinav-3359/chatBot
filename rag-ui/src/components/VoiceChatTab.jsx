import { useEffect, useRef, useState } from "react";
import { Mic, Square } from "lucide-react";
import { useAuth } from "../context/AuthContext";

// Same barge-in threshold the Python test client uses for its own local
// VAD - this is purely a client-side "should I interrupt what's
// playing" check; the server runs its own authoritative VAD for
// actually detecting speech/utterance boundaries.
const BARGE_IN_ENERGY_THRESHOLD = 1200;

// Set VITE_VOICE_WS_URL in deployment - MUST be wss:// (not ws://)
// there, since the page itself will be served over HTTPS and browsers
// block a plain ws:// connection from a secure page as mixed content.
// Falls back to localhost for local dev, where that restriction doesn't
// apply.
const VOICE_WS_HOST = import.meta.env.VITE_VOICE_WS_URL || "ws://localhost:9000";

const VoiceChatTab = ({ chatbotId }) => {
  const { token } = useAuth();

  // "idle" | "connecting" | "listening" | "speaking" | "error"
  const [status, setStatus] = useState("idle");
  const [transcript, setTranscript] = useState("");
  const [answer, setAnswer] = useState("");
  const [errorMessage, setErrorMessage] = useState("");

  const wsRef = useRef(null);
  const audioContextRef = useRef(null);
  const micStreamRef = useRef(null);
  const sourceNodeRef = useRef(null);
  const workletNodeRef = useRef(null);

  // Playback scheduling - mirrors the lesson learned the hard way in
  // the Python client: "server said done sending" and "client finished
  // playing" are NOT the same moment, and conflating them is exactly
  // what broke barge-in there. isPlayingRef only goes false once BOTH
  // assistant_done has arrived AND every scheduled chunk has actually
  // finished playing.
  const nextStartTimeRef = useRef(0);
  const scheduledSourcesRef = useRef([]);
  const pendingAudioCountRef = useRef(0);
  const doneReceivedRef = useRef(false);
  const isPlayingRef = useRef(false);

  useEffect(() => {
    return () => {
      stop();
      // eslint-disable-next-line react-hooks/exhaustive-deps
    };
  }, []);

  const cleanupAudio = () => {
    if (workletNodeRef.current) {
      workletNodeRef.current.port.onmessage = null;
      workletNodeRef.current.disconnect();
      workletNodeRef.current = null;
    }

    if (sourceNodeRef.current) {
      sourceNodeRef.current.disconnect();
      sourceNodeRef.current = null;
    }

    if (micStreamRef.current) {
      micStreamRef.current.getTracks().forEach((t) => t.stop());
      micStreamRef.current = null;
    }

    scheduledSourcesRef.current.forEach((s) => {
      try {
        s.onended = null;
        s.stop();
      } catch {
        // already stopped/ended - fine to ignore
      }
    });
    scheduledSourcesRef.current = [];
    pendingAudioCountRef.current = 0;
    nextStartTimeRef.current = 0;
    isPlayingRef.current = false;
    doneReceivedRef.current = false;

    if (audioContextRef.current) {
      audioContextRef.current.close().catch(() => {});
      audioContextRef.current = null;
    }
  };

  const maybeFinishSpeaking = () => {
    if (doneReceivedRef.current && pendingAudioCountRef.current === 0) {
      isPlayingRef.current = false;
      setStatus("listening");
    }
  };

  const schedulePlayback = (arrayBuffer) => {
    const audioContext = audioContextRef.current;
    if (!audioContext) return;

    const int16 = new Int16Array(arrayBuffer);
    const float32 = new Float32Array(int16.length);
    for (let i = 0; i < int16.length; i++) {
      float32[i] = int16[i] / 32768;
    }

    // Kokoro's output is 24kHz - the Web Audio API resamples this
    // buffer to the context's native output rate automatically on
    // playback, so the AudioContext itself doesn't need to be 24kHz.
    const buffer = audioContext.createBuffer(1, float32.length, 24000);
    buffer.copyToChannel(float32, 0);

    const sourceNode = audioContext.createBufferSource();
    sourceNode.buffer = buffer;
    sourceNode.connect(audioContext.destination);

    const startAt = Math.max(nextStartTimeRef.current, audioContext.currentTime);
    sourceNode.start(startAt);
    nextStartTimeRef.current = startAt + buffer.duration;

    pendingAudioCountRef.current += 1;
    scheduledSourcesRef.current.push(sourceNode);
    isPlayingRef.current = true;
    setStatus("speaking");

    sourceNode.onended = () => {
      pendingAudioCountRef.current = Math.max(0, pendingAudioCountRef.current - 1);
      scheduledSourcesRef.current = scheduledSourcesRef.current.filter(
        (s) => s !== sourceNode
      );
      maybeFinishSpeaking();
    };
  };

  const interrupt = () => {
    scheduledSourcesRef.current.forEach((s) => {
      try {
        s.onended = null;
        s.stop();
      } catch {
        // already stopped - fine
      }
    });
    scheduledSourcesRef.current = [];
    pendingAudioCountRef.current = 0;
    nextStartTimeRef.current = audioContextRef.current
      ? audioContextRef.current.currentTime
      : 0;
    isPlayingRef.current = false;
    setStatus("listening");

    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      wsRef.current.send(JSON.stringify({ type: "interrupt" }));
    }
  };

  const handleControlMessage = (data) => {
    if (data.type === "assistant") {
      setTranscript(data.transcript);
      setAnswer("");
      doneReceivedRef.current = false;
    } else if (data.type === "assistant_chunk") {
      setAnswer(data.chunk);
    } else if (data.type === "assistant_done") {
      doneReceivedRef.current = true;
      maybeFinishSpeaking();
    } else if (data.type === "error") {
      setErrorMessage(data.message || "Something went wrong.");
      setStatus("listening");
    }
  };

  const start = async () => {
    try {
      setErrorMessage("");
      setStatus("connecting");

      const stream = await navigator.mediaDevices.getUserMedia({
        audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true },
      });
      micStreamRef.current = stream;

      const audioContext = new (window.AudioContext || window.webkitAudioContext)();
      audioContextRef.current = audioContext;

      await audioContext.audioWorklet.addModule("/pcm-recorder-worklet.js");

      const source = audioContext.createMediaStreamSource(stream);
      sourceNodeRef.current = source;

      const worklet = new AudioWorkletNode(audioContext, "pcm-recorder-processor");
      workletNodeRef.current = worklet;

      const wsUrl = `${VOICE_WS_HOST}/voice?chatbotId=${chatbotId}&token=${encodeURIComponent(
        token
      )}`;
      const ws = new WebSocket(wsUrl);
      ws.binaryType = "arraybuffer";
      wsRef.current = ws;

      ws.onopen = () => {
        source.connect(worklet);
        setStatus("listening");
      };

      worklet.port.onmessage = (event) => {
        const chunk = event.data;

        const int16 = new Int16Array(chunk);
        let sum = 0;
        for (let i = 0; i < int16.length; i++) {
          sum += Math.abs(int16[i]);
        }
        const energy = sum / int16.length;

        if (energy >= BARGE_IN_ENERGY_THRESHOLD && isPlayingRef.current) {
          interrupt();
        }

        if (ws.readyState === WebSocket.OPEN) {
          ws.send(chunk);
        }
      };

      ws.onmessage = (event) => {
        if (typeof event.data === "string") {
          handleControlMessage(JSON.parse(event.data));
        } else {
          schedulePlayback(event.data);
        }
      };

      ws.onerror = () => {
        setStatus("error");
        setErrorMessage("Could not connect to the voice service.");
      };

      ws.onclose = () => {
        cleanupAudio();
        setStatus((prev) => (prev === "error" ? prev : "idle"));
      };
    } catch (err) {
      setStatus("error");
      setErrorMessage(err.message || "Could not access the microphone.");
      cleanupAudio();
    }
  };

  const stop = () => {
    if (wsRef.current) {
      wsRef.current.onclose = null;
      wsRef.current.close();
      wsRef.current = null;
    }
    cleanupAudio();
    setStatus("idle");
  };

  const toggle = () => {
    if (status === "idle" || status === "error") {
      start();
    } else {
      stop();
    }
  };

  const statusText = {
    idle: "Tap the mic to start talking",
    connecting: "Connecting…",
    listening: "Listening…",
    speaking: "Speaking… (talk any time to interrupt)",
    error: errorMessage || "Something went wrong",
  }[status];

  return (
    <div className="flex flex-col items-center gap-5 py-6">
      <button
        type="button"
        onClick={toggle}
        aria-label={status === "idle" || status === "error" ? "Start talking" : "Stop"}
        className={`flex h-[84px] w-[84px] items-center justify-center rounded-full text-accent-ink transition-shadow ${
          status === "speaking"
            ? "bg-warning shadow-[0_0_0_10px_var(--color-warning-soft)]"
            : status === "listening" || status === "connecting"
            ? "bg-accent shadow-[0_0_0_10px_var(--color-accent-soft)]"
            : "bg-accent"
        }`}
      >
        {status === "idle" || status === "error" ? (
          <Mic className="h-7 w-7" />
        ) : (
          <Square className="h-6 w-6" />
        )}
      </button>

      <div className="flex items-center gap-2 text-[13px] text-ink-muted">
        <span
          className={`h-[7px] w-[7px] rounded-full ${
            status === "listening" || status === "speaking"
              ? "bg-success"
              : "bg-ink-muted"
          }`}
        />
        {statusText}
      </div>

      <div className="w-full max-w-[480px] rounded-xl border border-border bg-surface-2 p-4 text-center text-[14px] leading-relaxed text-ink-muted">
        {transcript ? (
          <>
            <span className="font-medium text-ink">&ldquo;{transcript}&rdquo;</span>
            {answer && (
              <>
                <br />
                <br />
                {answer}
              </>
            )}
          </>
        ) : (
          "Your transcript and the assistant's reply will appear here while you talk."
        )}
      </div>

      <div className="flex items-center gap-1.5 text-[12px] text-ink-muted">
        <span className="rounded-full bg-warning-soft px-2 py-0.5 font-mono text-[11px] text-warning">
          barge-in
        </span>
        Talk any time while it&rsquo;s replying to interrupt it.
      </div>
    </div>
  );
};

export default VoiceChatTab;
