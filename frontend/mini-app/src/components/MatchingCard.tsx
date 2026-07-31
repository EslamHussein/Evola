import { useMemo, useState } from "react";
import { Cell, List, Section } from "@telegram-apps/telegram-ui";
import type { MatchPairDto } from "../api/types";

interface MatchingCardProps {
  pairs: MatchPairDto[];
  onChange: (submittedPairs: MatchPairDto[]) => void;
}

function shuffled<T>(items: T[]): T[] {
  const copy = [...items];
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}

/** Tap-to-select-a-pair matching UI — more reliable on mobile touch than true drag-and-drop. */
export function MatchingCard({ pairs, onChange }: MatchingCardProps) {
  const rightOptions = useMemo(() => shuffled(pairs.map((p) => p.right)), [pairs]);

  const [selectedLeft, setSelectedLeft] = useState<number | null>(null);
  // matched[leftIndex] = right text once paired
  const [matched, setMatched] = useState<Record<number, string>>({});

  const matchedRightTexts = new Set(Object.values(matched));

  function handleLeftTap(index: number) {
    if (matched[index] !== undefined) return;
    setSelectedLeft(index === selectedLeft ? null : index);
  }

  function handleRightTap(rightText: string) {
    if (matchedRightTexts.has(rightText) || selectedLeft === null) return;
    const next = { ...matched, [selectedLeft]: rightText };
    setMatched(next);
    setSelectedLeft(null);
    onChange(Object.entries(next).map(([leftIndex, right]) => ({ left: pairs[Number(leftIndex)].left, right })));
  }

  return (
    <div style={{ display: "flex", gap: 12 }}>
      <Section header="Deutsch" style={{ flex: 1 }}>
        <List>
          {pairs.map((pair, index) => (
            <Cell
              key={pair.left}
              onClick={() => handleLeftTap(index)}
              after={matched[index] ? "✓" : undefined}
              style={{
                opacity: matched[index] !== undefined ? 0.5 : 1,
                background: selectedLeft === index ? "var(--tgui--link_color)" : undefined,
              }}
            >
              {pair.left}
            </Cell>
          ))}
        </List>
      </Section>
      <Section header="English" style={{ flex: 1 }}>
        <List>
          {rightOptions.map((right) => (
            <Cell key={right} onClick={() => handleRightTap(right)} style={{ opacity: matchedRightTexts.has(right) ? 0.5 : 1 }}>
              {right}
            </Cell>
          ))}
        </List>
      </Section>
    </div>
  );
}
