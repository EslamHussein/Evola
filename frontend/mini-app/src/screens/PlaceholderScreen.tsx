import { Placeholder } from "@telegram-apps/telegram-ui";

interface PlaceholderScreenProps {
  title: string;
  description: string;
}

export function PlaceholderScreen({ title, description }: PlaceholderScreenProps) {
  return (
    <Placeholder header={title} description={description}>
      <span style={{ fontSize: 48 }}>🚧</span>
    </Placeholder>
  );
}
