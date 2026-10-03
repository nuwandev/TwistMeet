import type { Metadata } from "next";
import { APP_NAME } from "@/lib/branding";
import "./globals.css";

export const metadata: Metadata = {
  title: APP_NAME,
  description:
    "Run a small-club speedcubing event: registration, judge-entered results, and live standings.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
