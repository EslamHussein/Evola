// Mirrors presentation/mini-app-api/.../MiniAppDtos.kt exactly — keep these two in sync.

export type SessionBudgetType = "DURATION_MINUTES" | "WORD_COUNT";

export type ExerciseKind =
  | "TRANSLATE"
  | "MULTIPLE_CHOICE"
  | "CLOZE_SENTENCE"
  | "SCENARIO_CLOZE"
  | "SENTENCE_CREATION"
  | "GRAMMAR_CHOICE"
  | "SPEAKING_TURN"
  | "TRANSLATE_TO_ENGLISH"
  | "TRUE_FALSE"
  | "ARTICLE_CHOICE"
  | "WORD_MATCHING";

export type Difficulty = "EASY" | "MEDIUM" | "HARD" | "ADAPTIVE";

export interface MatchPairDto {
  left: string;
  right: string;
}

export interface StartSessionRequest {
  budgetType: SessionBudgetType;
  budgetValue: number;
  allowedKinds?: ExerciseKind[] | null;
  difficulty?: Difficulty | null;
}

export interface SessionPromptResponse {
  sessionRunId: string;
  tutoringSessionId: string;
  promptText: string;
  exerciseKind: ExerciseKind;
  options?: string[] | null;
  matchPairs?: MatchPairDto[] | null;
}

export interface SubmitSessionAnswerRequest {
  sessionRunId: string;
  tutoringSessionId: string;
  rawAnswer: string;
}

export interface SessionSummaryDto {
  durationMinutes: number;
  questionsAsked: number;
  correctCount: number;
  incorrectCount: number;
  accuracyPercent: number;
  wordsMastered: number;
  wordsNeedingPractice: number;
}

export interface SubmitSessionAnswerResponse {
  wasCorrect: boolean;
  feedback: string;
  nextPrompt?: string | null;
  nextTutoringSessionId?: string | null;
  sessionCompleted: boolean;
  summary?: SessionSummaryDto | null;
  nextExerciseKind?: ExerciseKind | null;
  nextOptions?: string[] | null;
  nextMatchPairs?: MatchPairDto[] | null;
}

export interface ErrorResponse {
  error: string;
}
