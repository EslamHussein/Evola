import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { api } from "../../api/client";
import type {
  ExerciseKind,
  MatchPairDto,
  SessionPromptResponse,
  SessionSummaryDto,
  StartSessionRequest,
  SubmitSessionAnswerRequest,
  SubmitSessionAnswerResponse,
} from "../../api/types";
import { SessionConfigScreen } from "./SessionConfigScreen";
import { SessionScreen } from "./SessionScreen";
import { SessionSummaryScreen } from "./SessionSummaryScreen";

type Phase = "config" | "session" | "summary";

interface ActiveQuestion {
  sessionRunId: string;
  tutoringSessionId: string;
  promptText: string;
  exerciseKind: ExerciseKind;
  options?: string[] | null;
  matchPairs?: MatchPairDto[] | null;
}

interface Feedback {
  text: string;
  wasCorrect: boolean;
}

export function LearnScreen() {
  const [phase, setPhase] = useState<Phase>("config");
  const [question, setQuestion] = useState<ActiveQuestion | null>(null);
  const [pendingNext, setPendingNext] = useState<Omit<ActiveQuestion, "sessionRunId"> | null>(null);
  const [feedback, setFeedback] = useState<Feedback | null>(null);
  const [answer, setAnswer] = useState("");
  const [questionNumber, setQuestionNumber] = useState(1);
  const [summary, setSummary] = useState<SessionSummaryDto | null>(null);

  const startMutation = useMutation({
    mutationFn: (request: StartSessionRequest) => api.post<SessionPromptResponse>("/session/learn/start", request),
    onSuccess: (response) => {
      setQuestion({
        sessionRunId: response.sessionRunId,
        tutoringSessionId: response.tutoringSessionId,
        promptText: response.promptText,
        exerciseKind: response.exerciseKind,
        options: response.options,
        matchPairs: response.matchPairs,
      });
      setQuestionNumber(1);
      setPhase("session");
    },
  });

  const answerMutation = useMutation({
    mutationFn: (rawAnswer: string) => {
      if (!question) throw new Error("No active question");
      const request: SubmitSessionAnswerRequest = {
        sessionRunId: question.sessionRunId,
        tutoringSessionId: question.tutoringSessionId,
        rawAnswer,
      };
      return api.post<SubmitSessionAnswerResponse>("/session/learn/answer", request);
    },
    onSuccess: (response) => {
      if (response.sessionCompleted) {
        setSummary(response.summary ?? null);
        setPhase("summary");
        return;
      }
      setFeedback({ text: response.feedback, wasCorrect: response.wasCorrect });
      setPendingNext({
        tutoringSessionId: response.nextTutoringSessionId ?? "",
        promptText: response.nextPrompt ?? "",
        exerciseKind: response.nextExerciseKind ?? "TRANSLATE",
        options: response.nextOptions,
        matchPairs: response.nextMatchPairs,
      });
    },
  });

  function handleContinue() {
    setQuestion((current) => (current && pendingNext ? { ...current, ...pendingNext } : current));
    setPendingNext(null);
    setFeedback(null);
    setAnswer("");
    setQuestionNumber((n) => n + 1);
  }

  function handleStartAnother() {
    setPhase("config");
    setQuestion(null);
    setSummary(null);
    setFeedback(null);
    setAnswer("");
  }

  if (phase === "summary" && summary) {
    return <SessionSummaryScreen summary={summary} onStartAnother={handleStartAnother} />;
  }

  if (phase === "session" && question) {
    return (
      <SessionScreen
        questionNumber={questionNumber}
        promptText={question.promptText}
        exerciseKind={question.exerciseKind}
        options={question.options}
        matchPairs={question.matchPairs}
        answer={answer}
        onAnswerChange={setAnswer}
        feedback={feedback}
        onSubmit={() => answerMutation.mutate(answer)}
        onContinue={handleContinue}
        isSubmitting={answerMutation.isPending}
      />
    );
  }

  return (
    <SessionConfigScreen
      onStart={(request) => startMutation.mutate(request)}
      isStarting={startMutation.isPending}
      errorMessage={startMutation.error instanceof Error ? startMutation.error.message : null}
    />
  );
}
