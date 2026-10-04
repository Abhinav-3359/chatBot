import { useForm } from "react-hook-form";
import { login as loginApi } from "../api/authApi";
import { useAuth } from "../context/AuthContext";
import { useNavigate, Link } from "react-router-dom";
import { useState } from "react";
import { Mail, Lock, Eye, EyeOff, MessageSquare, FileText, Mic, History } from "lucide-react";
import LeafMark from "../components/LeafMark";
import AuthBrandPanel from "../components/AuthBrandPanel";

const FEATURES = [
  {
    icon: MessageSquare,
    title: "Your own chatbots",
    body: "Each one its own knowledge base, built from what you upload.",
  },
  {
    icon: FileText,
    title: "Documents become knowledge",
    body: "Sakha chunks and embeds them automatically.",
  },
  {
    icon: Mic,
    title: "Talk, not just type",
    body: "Real-time voice conversation, with natural interruption.",
  },
  {
    icon: History,
    title: "Remembers the conversation",
    body: "Follow-up questions just work within a session.",
  },
];

const Login = () => {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm({ defaultValues: { remember: true } });

  const { login } = useAuth();
  const navigate = useNavigate();

  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const onSubmit = async (data) => {
    try {
      setError("");
      setSubmitting(true);

      const response = await loginApi({
        email: data.email,
        password: data.password,
      });

      login(response.data.data.token, data.remember);

      navigate("/dashboard");
    } catch (err) {
      setError(
        err.response?.data?.message ||
          "Invalid email or password."
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-screen">
      <AuthBrandPanel
        headline="Give your knowledge a voice."
        body="Create AI companions from your own documents. Ask them anything — by typing or by talking — and pick up right where you left off."
        features={FEATURES}
      />

      <div className="flex flex-1 items-center justify-center bg-canvas px-8 py-10">
        <div className="w-full max-w-sm">
          <div className="mb-6 flex items-center justify-center gap-2.5 lg:hidden">
            <LeafMark className="h-7 w-7 text-ink" />
            <div className="font-display text-xl font-semibold text-ink">Sakha</div>
          </div>

          <h2 className="font-display text-2xl font-semibold text-ink">Welcome back</h2>
          <p className="mt-1.5 text-[13.5px] text-ink-muted">
            Log in to continue to your chatbots.
          </p>

          <form onSubmit={handleSubmit(onSubmit)} className="mt-6 flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <label htmlFor="email" className="text-[12.5px] font-medium text-ink">
                Email
              </label>
              <div className="relative flex items-center">
                <Mail className="pointer-events-none absolute left-3 h-4 w-4 text-ink-muted" />
                <input
                  id="email"
                  type="email"
                  placeholder="you@example.com"
                  className="w-full rounded-[10px] border border-border bg-surface py-2.5 pl-9 pr-3 text-[14px] text-ink outline-none focus:border-accent"
                  {...register("email", { required: "Email is required" })}
                />
              </div>
              {errors.email && (
                <p className="text-[12.5px] text-danger">{errors.email.message}</p>
              )}
            </div>

            <div className="flex flex-col gap-1.5">
              <label htmlFor="password" className="text-[12.5px] font-medium text-ink">
                Password
              </label>
              <div className="relative flex items-center">
                <Lock className="pointer-events-none absolute left-3 h-4 w-4 text-ink-muted" />
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  placeholder="&#8226;&#8226;&#8226;&#8226;&#8226;&#8226;&#8226;&#8226;"
                  className="w-full rounded-[10px] border border-border bg-surface py-2.5 pl-9 pr-10 text-[14px] text-ink outline-none focus:border-accent"
                  {...register("password", { required: "Password is required" })}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  className="absolute right-3 text-ink-muted"
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {errors.password && (
                <p className="text-[12.5px] text-danger">{errors.password.message}</p>
              )}
            </div>

            <label className="flex items-center gap-2 text-[13px] text-ink-muted">
              <input
                type="checkbox"
                className="h-[15px] w-[15px] accent-accent"
                {...register("remember")}
              />
              Remember me on this device
            </label>

            {error && (
              <p className="rounded-lg border border-danger/20 bg-danger-soft px-3 py-2 text-[12.5px] text-danger">
                {error}
              </p>
            )}

            <button
              type="submit"
              disabled={submitting}
              className="mt-1 rounded-[10px] bg-accent px-4 py-3 text-[14.5px] font-medium text-accent-ink disabled:opacity-60"
            >
              {submitting ? "Signing in…" : "Sign in"}
            </button>
          </form>

          <p className="mt-5 text-center text-[13px] text-ink-muted">
            Don&rsquo;t have an account?{" "}
            <Link to="/register" className="font-medium text-accent">
              Create one
            </Link>
          </p>
        </div>
      </div>
    </div>
  );
};

export default Login;
