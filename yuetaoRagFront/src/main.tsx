import React from "react";
import ReactDOM from "react-dom/client";
import { RouterProvider } from "react-router-dom";
import { Toaster } from "sonner";

import { router } from "@/router";
import { useAuthStore } from "@/stores/authStore";
import "@/style.css";

useAuthStore.getState().checkAuth().finally(() => {
  ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
    <React.StrictMode>
      <RouterProvider router={router} />
      <Toaster richColors position="top-right" />
    </React.StrictMode>
  );
});
