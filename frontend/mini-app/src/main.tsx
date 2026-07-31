import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { AppRoot } from "@telegram-apps/telegram-ui";
import "@telegram-apps/telegram-ui/dist/styles.css";
import "./index.css";
import App from "./App.tsx";
import { initTelegramSdk } from "./telegram/init";

initTelegramSdk();

const queryClient = new QueryClient();

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <AppRoot>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </QueryClientProvider>
    </AppRoot>
  </StrictMode>,
);
