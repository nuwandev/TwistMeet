"use client";

import { useEffect, useRef, useState } from "react";

/**
 * 00 §7 "The 3D cube is a guide to moves, not sensor-based verification." 07 S07's move-by-move
 * controls come from cubing.js's own built-in control panel (play/pause/scrub/step) — this
 * component just wires the scramble notation into it and handles reduced motion.
 *
 * cubing.js (MPL-2.0, see DECISIONS.md OD01) ships `<twisty-player>` as a real custom element, so
 * it's driven imperatively via a ref rather than as JSX props — React doesn't know its property
 * names, and setting them as string attributes would stringify the Alg incorrectly for anything
 * but the simplest alg.
 */
export function TwistyGuide({ alg, label }: { alg: string; label?: string }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [reducedMotion, setReducedMotion] = useState(
    () => typeof window !== "undefined" && window.matchMedia("(prefers-reduced-motion: reduce)").matches,
  );
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    const query = window.matchMedia("(prefers-reduced-motion: reduce)");
    function onChange(e: MediaQueryListEvent) {
      setReducedMotion(e.matches);
    }
    query.addEventListener("change", onChange);
    return () => query.removeEventListener("change", onChange);
  }, []);

  useEffect(() => {
    let cancelled = false;
    let player: HTMLElement & Record<string, unknown>;
    const container = containerRef.current;

    import("cubing/twisty")
      .then(({ TwistyPlayer }) => {
        if (cancelled || !container) return;
        player = new TwistyPlayer({
          puzzle: "3x3x3",
          alg,
          background: "none",
          // 00 §12 / this app's own reduced-motion convention (see globals.css): rather than a
          // documented "instant jump" mode (cubing.js has none), scale the animation duration
          // down to near-zero when the OS requests less motion — tempoScale is a stable, public
          // property (see DECISIONS.md for the primary-source check of the available API).
          tempoScale: reducedMotion ? 25 : 1,
        }) as unknown as HTMLElement & Record<string, unknown>;
        container.innerHTML = "";
        container.appendChild(player);
      })
      .catch(() => {
        if (!cancelled) setLoadError("The 3D move guide could not be loaded.");
      });

    return () => {
      cancelled = true;
      if (container) container.innerHTML = "";
    };
  }, [alg, reducedMotion]);

  return (
    <div>
      <p className="status-badge" aria-hidden="true">
        3D guide — instruction only, not a physical cube check
      </p>
      <div
        ref={containerRef}
        role="img"
        aria-label={label ?? `3D move guide for scramble: ${alg}`}
        style={{ width: "100%", minHeight: 280 }}
      />
      {loadError && <p className="error-text">{loadError}</p>}
    </div>
  );
}
