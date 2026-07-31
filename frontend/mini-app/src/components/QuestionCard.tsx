import { Cell, Input, List, Radio, SegmentedControl, Textarea } from "@telegram-apps/telegram-ui";
import type { ExerciseKind, MatchPairDto } from "../api/types";
import { MatchingCard } from "./MatchingCard";

interface QuestionCardProps {
  exerciseKind: ExerciseKind;
  options?: string[] | null;
  matchPairs?: MatchPairDto[] | null;
  answer: string;
  onAnswerChange: (value: string) => void;
}

const SEGMENTED_KINDS: ExerciseKind[] = ["TRUE_FALSE", "ARTICLE_CHOICE"];
const RADIO_KINDS: ExerciseKind[] = ["MULTIPLE_CHOICE", "GRAMMAR_CHOICE"];

export function QuestionCard({ exerciseKind, options, matchPairs, answer, onAnswerChange }: QuestionCardProps) {
  if (exerciseKind === "WORD_MATCHING" && matchPairs && matchPairs.length > 0) {
    return (
      <MatchingCard
        pairs={matchPairs}
        onChange={(submittedPairs) => onAnswerChange(JSON.stringify(submittedPairs))}
      />
    );
  }

  if (SEGMENTED_KINDS.includes(exerciseKind) && options && options.length > 0) {
    return (
      <SegmentedControl>
        {options.map((option) => (
          <SegmentedControl.Item key={option} selected={answer === option} onClick={() => onAnswerChange(option)}>
            {option}
          </SegmentedControl.Item>
        ))}
      </SegmentedControl>
    );
  }

  if (RADIO_KINDS.includes(exerciseKind) && options && options.length > 0) {
    return (
      <List>
        {options.map((option) => (
          <Cell
            key={option}
            before={<Radio name="mc-option" checked={answer === option} onChange={() => onAnswerChange(option)} />}
            onClick={() => onAnswerChange(option)}
          >
            {option}
          </Cell>
        ))}
      </List>
    );
  }

  if (exerciseKind === "SENTENCE_CREATION") {
    return (
      <Textarea
        placeholder="Schreibe deine Antwort..."
        value={answer}
        onChange={(event) => onAnswerChange(event.target.value)}
      />
    );
  }

  return (
    <Input
      placeholder="Deine Antwort"
      value={answer}
      onChange={(event) => onAnswerChange(event.target.value)}
      autoFocus
    />
  );
}
