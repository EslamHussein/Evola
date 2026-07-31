import { useEffect } from "react";
import { useRawInitData } from "@telegram-apps/sdk-react";
import { setRawInitData } from "../api/client";

/** useRawInitData() throws outside of a real Telegram launch (e.g. plain browser preview). */
function useOptionalRawInitData(): string | undefined {
  try {
    // eslint-disable-next-line react-hooks/rules-of-hooks
    return useRawInitData();
  } catch {
    return undefined;
  }
}

/** Keeps the API client's Authorization header in sync with the current Telegram initData. */
export function useSyncInitData(): void {
  const rawInitData = useOptionalRawInitData();
  useEffect(() => {
    setRawInitData(rawInitData);
  }, [rawInitData]);
}
