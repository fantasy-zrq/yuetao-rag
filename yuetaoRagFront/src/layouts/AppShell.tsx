import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { BookOpen, ChevronLeft, LogOut, MessageSquareText, Search, UserRound, Workflow } from "lucide-react";

import { Button } from "@/components/Button";
import { useAuthStore } from "@/stores/authStore";

export function AppShell() {
  const navigate = useNavigate();
  const { user, logout } = useAuthStore();

  async function handleLogout() {
    await logout();
    navigate("/login", { replace: true });
  }

  return (
    <div className="admin-layout">
      <aside className="admin-sidebar">
        <div className="admin-sidebar__brand">
          <div className="admin-brand-row">
            <span className="admin-sidebar__logo">YT</span>
            <span>
              <strong className="admin-sidebar__title">YueTao RAG</strong>
              <small className="admin-sidebar__subtitle">知识问答工作台</small>
            </span>
          </div>
        </div>
        <div className="admin-sidebar__user">
          <span className="admin-sidebar__avatar">
            <UserRound size={16} />
          </span>
          <span className="admin-user-meta">
            <strong>{user?.displayName || user?.username || "未登录用户"}</strong>
            <small>{user?.role || "user"}</small>
          </span>
        </div>
        <nav className="admin-nav">
          <p className="admin-sidebar__group-title">导航</p>
          <NavLink to="/chat" className="admin-sidebar__item">
            <span className="admin-sidebar__item-indicator" />
            <MessageSquareText className="admin-sidebar__item-icon" size={18} />
            智能问答
          </NavLink>
          <NavLink
            to="/admin/knowledge"
            className={({ isActive }) => `admin-sidebar__item ${isActive ? "admin-sidebar__item--active" : ""}`}
          >
            <span className="admin-sidebar__item-indicator" />
            <BookOpen className="admin-sidebar__item-icon" size={18} />
            知识库与文档管理
          </NavLink>
          <NavLink
            to="/admin/traces"
            className={({ isActive }) => `admin-sidebar__item ${isActive ? "admin-sidebar__item--active" : ""}`}
          >
            <span className="admin-sidebar__item-indicator" />
            <Workflow className="admin-sidebar__item-icon" size={18} />
            链路追踪
          </NavLink>
        </nav>
        <div className="admin-sidebar__footer">
          <Button variant="ghost" className="admin-sidebar__logout" onClick={handleLogout}>
            <LogOut size={16} />
            退出登录
          </Button>
          <button className="admin-sidebar__collapse" type="button" aria-label="折叠侧栏">
            <ChevronLeft size={14} />
            收起
          </button>
        </div>
      </aside>
      <div className="admin-main">
        <header className="admin-topbar">
          <div className="admin-topbar-inner">
            <div className="admin-topbar-search">
              <Search size={16} />
              <input placeholder="搜索知识库 / 文档" />
              <span className="admin-topbar-kbd">⌘K</span>
            </div>
            <Button variant="secondary" onClick={() => navigate("/chat")}>
              <MessageSquareText size={16} />
              返回问答
            </Button>
          </div>
        </header>
        <main className="admin-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
