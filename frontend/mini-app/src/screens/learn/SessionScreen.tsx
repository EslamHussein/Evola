import { Button, Cell, List, Progress, Section, Subheadline, Text } from "@telegram-apps/telegram-ui";
import { QuestionCard } from "../../components/QuestionCard";
import type { ExerciseKind, MatchPairDto } from "../../api/types";

interface Feedback {
  text: string;
  wasCorrect: boolean;
}

interface SessionScreenProps {
  questionNumber: number;
  promptText: string;
  exerciseKind: ExerciseKind;
  options?: string[] | null;
  matchPairs?: MatchPairDto[] | null;
  answer: string;
  onAnswerChange: (value: string) => void;
  feedback: Feedback | null;
  onSubmit: () => void;
  onContinue: () => void;
  isSubmitting: boolean;
}

export function SessionScreen({
  questionNumber,
  promptText,
  exerciseKind,
  options,
  matchPairs,
  answer,
  onAnswerChange,
  feedback,
  onSubmit,
  onContinue,
  isSubmitting,
}: SessionScreenProps) {
  return (
    <div style={{ padding: 16 }}>
      <Section>
        <div style={{ padding: "0 16px 12px" }}>
          <Subheadline>Question {questionNumber}</Subheadline>
          <Progress value={Math.min(100, questionNumber * 10)} />
        </div>
      </Section>

      <Section header="Question">
        <List>
          <Cell multiline>
            <Text>{promptText}</Text>
          </Cell>
        </List>
      </Section>

      {!feedback && (
        <Section>
          <QuestionCard exerciseKind={exerciseKind} options={options} matchPairs={matchPairs} answer={answer} onAnswerChange={onAnswerChange} />
        </Section>
      )}

      {feedback && (
        <Section header={feedback.wasCorrect ? "Correct!" : "Not quite"}>
          <Cell multiline>{feedback.text}</Cell>
        </Section>
      )}

      <div style={{ padding: 16 }}>
        {feedback ? (
          <Button size="l" stretched onClick={onContinue}>
            Continue
          </Button>
        ) : (
          <Button size="l" stretched loading={isSubmitting} disabled={isSubmitting || answer.length === 0} onClick={onSubmit}>
            Submit
          </Button>
        )}
      </div>
    </div>
  );
}
