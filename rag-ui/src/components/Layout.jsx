import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import LeafMark from "./LeafMark";

function initialsFrom(email) {
  if (!email) return "?";
  return email.slice(0, 2).toUpperCase();
}

const Layout = ({ children }) => {
  const { email, logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  const onDashboard = location.pathname === "/dashboard";

  const handleLogout = () => {
    logout();
    navigate("/");
  };

  return (
    <div className="flex min-h-screen bg-canvas text-ink">
      <aside className="flex w-[230px] flex-none flex-col bg-sidebar p-3.5 text-sidebar-ink">
        <Link to="/dashboard" className="flex items-center gap-2.5 px-2 pb-4.5 pt-1">
          <LeafMark className="h-[26px] w-[26px] text-white" />
          <div className="font-display text-[18px] font-semibold text-white">
            Sakha
          </div>
        </Link>

        <div className="px-2.5 pb-1.5 pt-3.5 text-[10.5px] uppercase tracking-wider text-sidebar-ink-dim">
          Workspace
        </div>

        <Link
          to="/dashboard"
          className={`flex w-full items-center gap-2.5 rounded-lg px-2.5 py-2.5 text-[13.5px] font-medium ${
            onDashboard
              ? "bg-sidebar-active text-white shadow-[inset_2.5px_0_0_var(--color-accent)]"
              : "text-sidebar-ink hover:bg-sidebar-active/60"
          }`}
        >
          <span className="w-[17px] text-center">&#9635;</span> Dashboard
        </Link>

        <button
          type="button"
          disabled
          className="flex w-full cursor-default items-center gap-2.5 rounded-lg px-2.5 py-2.5 text-left text-[13.5px] font-medium text-sidebar-ink-dim"
        >
          <span className="w-[17px] text-center">&#9881;</span> Settings
          <span className="ml-auto rounded-full bg-white/5 px-1.5 py-0.5 font-mono text-[9.5px] tracking-wide text-sidebar-ink-dim">
            later
          </span>
        </button>

        <div className="mt-auto border-t border-white/10 pt-3.5">
          <div className="flex items-center gap-2.5 rounded-lg p-2">
            <div className="flex h-[30px] w-[30px] flex-none items-center justify-center rounded-full bg-accent-soft font-mono text-[12px] font-medium text-accent">
              {initialsFrom(email)}
            </div>
            <div className="min-w-0">
              <div className="truncate text-[12px] text-white">{email}</div>
            </div>
            <button
              type="button"
              onClick={handleLogout}
              className="ml-auto flex-none cursor-pointer text-[12px] text-sidebar-ink-dim hover:text-white"
            >
              Log out
            </button>
          </div>
        </div>
      </aside>

      <main className="min-w-0 flex-1">{children}</main>
    </div>
  );
};

export default Layout;
