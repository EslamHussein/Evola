import { init } from "@telegram-apps/sdk-react";

/**
 * Only succeeds when actually running inside Telegram (real launch params present). Outside of
 * Telegram — e.g. previewing in a plain desktop browser during development — this is a no-op
 * rather than a thrown error, so the app still renders for visual/manual testing.
 */
export function initTelegramSdk(): void {
  try {
    init();
  } catch {
    // Not running inside Telegram — expected during local browser preview.
  }
}
