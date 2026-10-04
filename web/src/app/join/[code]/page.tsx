import JoinForm from "../JoinForm";

export default async function JoinWithCodePage({ params }: { params: Promise<{ code: string }> }) {
  const { code } = await params;
  return (
    <main style={{ maxWidth: 420, margin: "0 auto", padding: "var(--space-4) var(--space-2)" }}>
      <h1>Join an event</h1>
      <JoinForm initialCode={code} />
    </main>
  );
}
