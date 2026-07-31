import { Button, Cell, List, Progress, Section, Title } from "@telegram-apps/telegram-ui";
import type { SessionSummaryDto } from "../../api/types";

interface SessionSummaryScreenProps {
  summary: SessionSummaryDto;
  onStartAnother: () => void;
}

export function SessionSummaryScreen({ summary, onStartAnother }: SessionSummaryScreenProps) {
  return (
    <div style={{ padding: 16 }}>
      <Title style={{ textAlign: "center", margin: "16px 0" }}>Session Complete</Title>

      <Section header="Accuracy">
        <div style={{ padding: "0 16px 12px" }}>
          <Progress value={summary.accuracyPercent} />
        </div>
        <List>
          <Cell subtitle="Accuracy">{summary.accuracyPercent}%</Cell>
        </List>
      </Section>

      <Section header="Stats">
        <List>
          <Cell subtitle="Duration">{summary.durationMinutes} minutes</Cell>
          <Cell subtitle="Questions asked">{summary.questionsAsked}</Cell>
          <Cell subtitle="Correct">{summary.correctCount}</Cell>
          <Cell subtitle="Incorrect">{summary.incorrectCount}</Cell>
          <Cell subtitle="Words mastered">{summary.wordsMastered}</Cell>
          <Cell subtitle="Words needing practice">{summary.wordsNeedingPractice}</Cell>
        </List>
      </Section>

      <div style={{ padding: 16 }}>
        <Button size="l" stretched onClick={onStartAnother}>
          Start another session
        </Button>
      </div>
    </div>
  );
}
