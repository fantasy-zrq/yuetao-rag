import { Navigate, createBrowserRouter } from "react-router-dom";

import { AppShell } from "@/layouts/AppShell";
import { ChatPage } from "@/pages/ChatPage";
import { DocumentManagementPage } from "@/pages/DocumentManagementPage";
import { DocumentDetailPage } from "@/pages/DocumentDetailPage";
import { KnowledgeDocumentsPage } from "@/pages/KnowledgeDocumentsPage";
import { KnowledgePage } from "@/pages/KnowledgePage";
import { LoginPage } from "@/pages/LoginPage";
import { NotFoundPage } from "@/pages/NotFoundPage";
import { useAuthStore } from "@/stores/authStore";

function RequireAuth({ children }: { children: JSX.Element }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  return isAuthenticated ? children : <Navigate to="/login" replace />;
}

export const router = createBrowserRouter([
  { path: "/login", element: <LoginPage /> },
  {
    path: "/chat",
    element: (
      <RequireAuth>
        <ChatPage />
      </RequireAuth>
    )
  },
  {
    path: "/chat/:sessionId",
    element: (
      <RequireAuth>
        <ChatPage />
      </RequireAuth>
    )
  },
  {
    path: "/admin",
    element: (
      <RequireAuth>
        <AppShell />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <Navigate to="/admin/knowledge" replace /> },
      { path: "knowledge", element: <KnowledgePage /> },
      { path: "documents", element: <DocumentManagementPage /> },
      { path: "knowledge/:kbId", element: <KnowledgeDocumentsPage /> },
      { path: "knowledge/:kbId/docs/:docId", element: <DocumentDetailPage /> }
    ]
  },
  { path: "/", element: <Navigate to="/chat" replace /> },
  { path: "*", element: <NotFoundPage /> }
]);
