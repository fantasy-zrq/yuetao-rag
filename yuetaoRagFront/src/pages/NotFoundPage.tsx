import { Link } from "react-router-dom";

import { Button } from "@/components/Button";

export function NotFoundPage() {
  return (
    <main className="not-found">
      <h1>404</h1>
      <p>页面不存在或该模块当前后端暂未提供接口。</p>
      <Link to="/chat">
        <Button>返回问答</Button>
      </Link>
    </main>
  );
}
