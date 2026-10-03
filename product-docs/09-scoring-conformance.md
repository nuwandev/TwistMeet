# Scoring and rules conformance

The implementation must have a pure scoring module independent of UI and persistence. Inputs are a ruleset version and ordered raw attempt values/statuses. Output includes derived result, displayed average, exact comparison key, discarded attempts, rank and explanation. Sort using integer milliseconds; round only for display.

## Units and penalty rules

Raw time is a positive integer count of milliseconds. `NONE` adjusted value equals raw value. `PLUS_TWO` adjusted value is raw + 2000 ms. `DNF` and `DNS` have no adjusted numerical value and rank worse than any valid time. They must remain distinct in history. An attempt is `PENDING` until an authorized status is entered; blank is never silently converted to DNF/DNS before round close.

## Formats

- `BO1`, `BO2`, `BO3`: lowest valid adjusted single is the result. If all attempts are DNF/DNS, result DNF; if some are valid, DNF/DNS are ignored as unsuccessful worst attempts. Preserve DNS count separately.
- `MO3`: all three adjusted values are averaged. Any DNF or DNS makes the mean DNF. Arithmetic uses integer sum and exact division; display uses configured precision (default hundredth, half-up); comparison uses unrounded rational result to avoid display-rounding ties.
- `AO5`: adjusted valid results sorted ascending. Discard exactly one fastest and one slowest. If there are 4 valid results and one DNF/DNS, discard one fastest and one worst DNF/DNS; average the other three valid values. If fewer than four valid results, average is DNF. When equal values occur at boundaries, discard one attempt only, deterministic tie by attempt number; disclose discarded attempt IDs.

## Ranking

Within a round, valid results rank before DNF. Primary key is exact format result ascending. Tie-break 1 is best valid single ascending. If still identical, shared rank. For Mo3, use exact average first, then best single. DNF ties use best valid single if there is one, then shared rank. DNS-only and DNF-only can share last rank unless event rules explicitly separate them; default equal last rank. Rank numbering uses competition ranking (`1, 2, 2, 4`). Display order within a shared rank is entrant display name, but this does not change rank.

## Advancement

`TOP_N`: select first N rank positions, then include all entrants tied at Nth rank by default. Example rank 1,2,2 with N=2 advances all three. If organizer selected a tie-break attempt, show the tied names and create a special one-attempt attempt with an unused scramble; winner policy is the lower valid time, DNF last, then shared advancement if still tied. This option must be selected before registration opens.

`TOP_PERCENT`: compute target `ceil(eligibleCount × percentage / 100)`; then include all tied at the target rank. Show target and actual number advancing before commit. `EVERYONE` advances all eligible entrants. Eligibility is not withdrawn and has at least one result status. An organizer cannot silently exclude a competitor; withdrawal and reason remain in audit.

## Conformance vectors

All times below are adjusted seconds for readability. Production tests use integer milliseconds.

| Format | Attempts | Expected result | Expected note |
|---|---|---|---|
| Ao5 | 14.21, 12.84, 18.91, 13.05, 12.10 | 13.366… → displayed 13.37 | discard 12.10 and 18.91 |
| Ao5 | 14.20, 13.80, DNF, 15.10, 13.40 | 14.37 | discard 13.40 and DNF; mean remaining 13.80,14.20,15.10 |
| Ao5 | 10.00, 11.00, 12.00, DNF, DNS | DNF | fewer than four valid attempts |
| Ao5 | 10.00, 10.00, 12.00, 13.00, 14.00 | 11.67 | discard one 10.00 (tie by attempt number) and 14.00; average 10,12,13 |
| Mo3 | 10.00, 11.00, 12.00 | 11.00 | arithmetic mean |
| Mo3 | 10.00, 11.00, DNS | DNF | any DNS makes mean DNF |
| Bo3 | 11.00, 10.00, DNF | 10.00 | best valid single |
| Any | raw 12.345 +2 | 14.345 | store raw=12345 ms, penalty=2000 ms, adjusted=14345 ms |
| Rank | Ao5 12.00 vs 12.00; best singles 9.00 vs 9.50 | first competitor rank 1, second rank 2 | average tie breaks on best |
| Rank | identical average and best | shared rank 1; next rank 3 | competition ranking |
| Top N | ranks 1,2,2,4 and N=2 | first three advance | include boundary tie by default |
| Percent | 11 eligible, 25% | target 3, include cutoff ties | ceil(2.75)=3 before ties |

NOTE: The initial DNF sample in the first draft had an arithmetic mistake. This conformance table is authoritative: for 14.20, 13.80, DNF, 15.10, 13.40, discard DNF and 13.40; average the other three = 14.366… (14.37 at hundredth precision). All narrative examples and tests must use this corrected value.

## Rounding

Display default two decimals with round-half-up. Numeric source remains integer milliseconds; average is a rational number (sum / count) and sort key must preserve exact rational value. CSV exports raw milliseconds and formatted result. Do not use language-default floating-point rounding.

## Property and regression tests

- Attempt input order does not change score, except deterministic discarded-attempt tie selection.
- Adding an attempt outside the retained Ao5 three cannot change average unless it changes which extreme is discarded.
- +2 affects comparisons and averages exactly 2000 ms.
- DNF/DNS never outrank valid times.
- Same inputs + same ruleset version always produce byte-equivalent derived values.
- Revisions recalculate result and downstream standings, with before/after snapshots.
- Empty/pending attempt set never generates a final result.
