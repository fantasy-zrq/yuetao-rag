import { FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Eye, EyeOff, Lock, User } from "lucide-react";

import { Button } from "@/components/Button";
import { useAuthStore } from "@/stores/authStore";

export function LoginPage() {
  const navigate = useNavigate();
  const { login, isLoading, isAuthenticated } = useAuthStore();
  const [username, setUsername] = useState("yuetao_admin");
  const [password, setPassword] = useState("admin");
  const [showPassword, setShowPassword] = useState(false);

  if (isAuthenticated) {
    navigate("/chat", { replace: true });
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    await login(username, password);
    navigate("/chat", { replace: true });
  }

  return (
    <main className="login-page">
      <section className="login-card">
        <div className="login-card-header">
          <h1>欢迎回来</h1>
          <p>登录后继续你的检索增强对话。</p>
        </div>
        <form className="form-stack" onSubmit={handleSubmit}>
          <label>
            用户名
            <span className="input-icon-wrap">
              <User size={16} />
              <input value={username} onChange={(event) => setUsername(event.target.value)} placeholder="yuetao_admin" required />
            </span>
          </label>
          <label>
            密码
            <span className="input-icon-wrap">
              <Lock size={16} />
              <input
                type={showPassword ? "text" : "password"}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="请输入密码"
                required
              />
              <button type="button" onClick={() => setShowPassword((value) => !value)} aria-label="显示或隐藏密码">
                {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
              </button>
            </span>
          </label>
          <div className="login-meta">
            <label>
              <input type="checkbox" defaultChecked />
              记住我
            </label>
            <span>账号由管理员初始化</span>
          </div>
          <Button className="full-width" type="submit" disabled={isLoading}>
            {isLoading ? "登录中..." : "登录"}
          </Button>
        </form>
      </section>
    </main>
  );
}
