import { useState } from "react";
import { Button, Cell, List, Section, SegmentedControl, Checkbox } from "@telegram-apps/telegram-ui";
import type { Difficulty, ExerciseKind, SessionBudgetType, StartSessionRequest } from "../../api/types";

interface SessionConfigScreenProps {
  onStart: (request: StartSessionRequest) => void;
  isStarting: boolean;
  errorMessage?: string | null;
}

const DURATION_VALUES = [10, 20, 30];
const WORD_COUNT_VALUES = [10, 20, 50];

const QUESTION_TYPE_CATEGORIES: { label: string; kinds: ExerciseKind[] }[] = [
  { label: "Vocabulary", kinds: ["TRANSLATE", "TRANSLATE_TO_ENGLISH", "MULTIPLE_CHOICE", "TRUE_FALSE"] },
  { label: "Articles", kinds: ["ARTICLE_CHOICE"] },
  { label: "Fill in the blanks", kinds: ["CLOZE_SENTENCE", "SCENARIO_CLOZE"] },
  { label: "Sentence creation", kinds: ["SENTENCE_CREATION"] },
  { label: "Word matching", kinds: ["WORD_MATCHING"] },
];

const DIFFICULTY_OPTIONS: { value: Difficulty; label: string }[] = [
  { value: "EASY", label: "Easy" },
  { value: "MEDIUM", label: "Medium" },
  { value: "HARD", label: "Hard" },
  { value: "ADAPTIVE", label: "Auto" },
];

export function SessionConfigScreen({ onStart, isStarting, errorMessage }: SessionConfigScreenProps) {
  const [budgetType, setBudgetType] = useState<SessionBudgetType>("WORD_COUNT");
  const [budgetValue, setBudgetValue] = useState(10);
  const [selectedCategories, setSelectedCategories] = useState<Set<string>>(new Set());
  const [difficulty, setDifficulty] = useState<Difficulty>("ADAPTIVE");

  const budgetValues = budgetType === "WORD_COUNT" ? WORD_COUNT_VALUES : DURATION_VALUES;

  function toggleCategory(label: string) {
    const next = new Set(selectedCategories);
    if (next.has(label)) next.delete(label);
    else next.add(label);
    setSelectedCategories(next);
  }

  function handleStart() {
    const allowedKinds =
      selectedCategories.size === 0
        ? null
        : QUESTION_TYPE_CATEGORIES.filter((category) => selectedCategories.has(category.label)).flatMap((category) => category.kinds);

    onStart({ budgetType, budgetValue, allowedKinds, difficulty });
  }

  return (
    <div style={{ padding: 16 }}>
      <Section header="How do you want to learn?">
        <List>
          <Cell>
            <SegmentedControl>
              <SegmentedControl.Item
                selected={budgetType === "WORD_COUNT"}
                onClick={() => {
                  setBudgetType("WORD_COUNT");
                  setBudgetValue(WORD_COUNT_VALUES[0]);
                }}
              >
                Words
              </SegmentedControl.Item>
              <SegmentedControl.Item
                selected={budgetType === "DURATION_MINUTES"}
                onClick={() => {
                  setBudgetType("DURATION_MINUTES");
                  setBudgetValue(DURATION_VALUES[0]);
                }}
              >
                Duration
              </SegmentedControl.Item>
            </SegmentedControl>
          </Cell>
          <Cell subhead={budgetType === "WORD_COUNT" ? "Number of words" : "Minutes"}>
            <SegmentedControl>
              {budgetValues.map((value) => (
                <SegmentedControl.Item key={value} selected={budgetValue === value} onClick={() => setBudgetValue(value)}>
                  {value}
                </SegmentedControl.Item>
              ))}
            </SegmentedControl>
          </Cell>
        </List>
      </Section>

      <Section header="Question types (leave blank for Mixed)">
        <List>
          {QUESTION_TYPE_CATEGORIES.map((category) => (
            <Cell
              key={category.label}
              before={
                <Checkbox
                  checked={selectedCategories.has(category.label)}
                  onChange={() => toggleCategory(category.label)}
                />
              }
              onClick={() => toggleCategory(category.label)}
            >
              {category.label}
            </Cell>
          ))}
        </List>
      </Section>

      <Section header="Difficulty">
        <List>
          <Cell>
            <SegmentedControl>
              {DIFFICULTY_OPTIONS.map((option) => (
                <SegmentedControl.Item key={option.value} selected={difficulty === option.value} onClick={() => setDifficulty(option.value)}>
                  {option.label}
                </SegmentedControl.Item>
              ))}
            </SegmentedControl>
          </Cell>
        </List>
      </Section>

      {errorMessage && (
        <Section>
          <Cell subtitle={errorMessage}>Couldn't start session</Cell>
        </Section>
      )}

      <div style={{ padding: "16px" }}>
        <Button size="l" stretched loading={isStarting} disabled={isStarting} onClick={handleStart}>
          Start Session
        </Button>
      </div>
    </div>
  );
}
