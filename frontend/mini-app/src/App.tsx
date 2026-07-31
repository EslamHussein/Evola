import { Tabbar } from "@telegram-apps/telegram-ui";
import { Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { LearnScreen } from "./screens/learn/LearnScreen";
import { PlaceholderScreen } from "./screens/PlaceholderScreen";
import { useSyncInitData } from "./telegram/useSyncInitData";

interface TabDefinition {
  path: string;
  label: string;
  icon: string;
}

const TABS: TabDefinition[] = [
  { path: "/learn", label: "Learn", icon: "📚" },
  { path: "/review", label: "Review", icon: "🔁" },
  { path: "/library", label: "Library", icon: "📁" },
  { path: "/progress", label: "Progress", icon: "📈" },
  { path: "/settings", label: "Settings", icon: "⚙️" },
];

export default function App() {
  useSyncInitData();
  const location = useLocation();
  const navigate = useNavigate();
  const activePath = TABS.find((tab) => location.pathname.startsWith(tab.path))?.path ?? TABS[0].path;

  return (
    <div style={{ height: "100vh" }}>
      {/* Tabbar renders via FixedLayout (position: fixed) — it doesn't participate in flex sizing,
          so the scroll container needs enough bottom padding to clear it itself. */}
      <div style={{ height: "100%", overflowY: "auto", paddingBottom: 96, boxSizing: "border-box" }}>
        <Routes>
          <Route path="/learn" element={<LearnScreen />} />
          <Route
            path="/review"
            element={<PlaceholderScreen title="Review" description="Coming soon: your due-review queue, right here as interactive cards." />}
          />
          <Route
            path="/library"
            element={<PlaceholderScreen title="Library" description="Coming soon: upload resources, track processing status, and start learning from them." />}
          />
          <Route
            path="/progress"
            element={<PlaceholderScreen title="Progress" description="Coming soon: words learned, streaks, accuracy, and session history." />}
          />
          <Route
            path="/settings"
            element={<PlaceholderScreen title="Settings" description="Coming soon: learning mode and preferences." />}
          />
          <Route path="*" element={<LearnScreen />} />
        </Routes>
      </div>
      <Tabbar>
        {TABS.map((tab) => (
          <Tabbar.Item key={tab.path} text={tab.label} selected={activePath === tab.path} onClick={() => navigate(tab.path)}>
            <span style={{ fontSize: 20 }}>{tab.icon}</span>
          </Tabbar.Item>
        ))}
      </Tabbar>
    </div>
  );
}
