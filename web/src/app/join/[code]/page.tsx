import JoinForm from "../JoinForm";

export default function JoinWithCodePage({ params }: { params: { code: string } }) {
  return (
    <main style={{ maxWidth: 420, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      <h1>Join an event</h1>
      <JoinForm initialCode={params.code} />
    </main>
  );
}
