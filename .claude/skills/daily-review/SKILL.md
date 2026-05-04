---
name: daily-review
description: Use when running the scheduled daily code review of master. Inspects every commit landed in the last 24 hours, classifies findings by severity, and files a single GitHub issue summarising the review. Read-only — never modifies source or opens PRs.
---

# Daily Automated Review

You are performing the daily automated code review of `master`. The workflow that invoked this skill has already verified that at least one commit landed in the last 24 hours; your job is to inspect that window and report findings.

## Scope

- **Branch:** `master`
- **Window:** `git log --since="24 hours ago"` on `master` at the moment the workflow ran.
- **Mode:** read-only. Do not edit files, do not push, do not open or merge PRs.
- **Output:** exactly one GitHub issue, even if there are no findings (so the audit trail is preserved).

## Procedure

Execute these steps in order. Do not skip steps.

### 1. Enumerate the in-scope commits

```bash
git log --since="24 hours ago" --pretty=format:'%H %s (%an, %ar)' master
```

If the list is empty, abort and file an issue titled `Daily review YYYY-MM-DD: no activity` with body `No commits landed on master in the last 24 hours.` This branch should not normally trigger because the calling workflow gates on commit count, but it is a safe no-op.

### 2. Inspect every commit's diff

For each commit hash from step 1, run:

```bash
git show --stat <hash>          # high-level: which files, how many lines
git show <hash>                 # full diff
```

If a commit is large (> 500 changed lines), also run:

```bash
git show <hash> -- '<path>'     # focused per-file inspection
```

### 3. Review against the checklist

For every commit, evaluate against **all** the categories below. Record findings as you go; do not defer.

| Category | What to look for |
|---|---|
| **Correctness** | logic errors, off-by-one, wrong operator, swapped arguments, incorrect error handling, swallowed exceptions, unreachable branches, broken null/empty handling |
| **Regressions** | removed behaviour, changed contracts (return types, exceptions thrown, idempotency), broken backwards compatibility, deleted tests |
| **Performance** | N+1 queries, blocking I/O on coroutine dispatchers, unbounded collections / channels, hot-loop allocations, missing pagination, synchronous calls in request paths |
| **Concurrency** | race conditions, missing happens-before, shared mutable state without synchronisation, `runBlocking` in suspending code, leaked coroutine scopes, missing structured-concurrency boundaries |
| **Security** | unvalidated input, SQL/command/header injection, path traversal, unsafe deserialization, hardcoded secrets, broken auth/authz, missing rate limiting on auth endpoints, leaked PII in logs |
| **Reliability** | missing timeouts, unbounded retries, no backpressure, ignored failures, fire-and-forget without observability, resource leaks (files, sockets, threads) |
| **Test coverage** | new logic without tests, removed tests, tests that only assert mock interactions, tests that pass without exercising the new code path |
| **Code quality** | dead code, contradictory comments vs. behaviour (a comment claiming "exponential" while code is linear is a real bug), public API added without docs |

### 4. Severity classification

Assign each finding exactly one severity. Use these definitions verbatim — do not invent intermediate tiers.

- **Critical** — security exploit, data loss, data corruption, prod outage on the next deploy. Must be fixed before the next release.
- **High** — wrong behaviour observable to users, silent dropping of work, stats/metrics that mislead operators, race conditions reachable under normal load.
- **Medium** — wrong-but-currently-masked behaviour (relies on a coincidence elsewhere), accuracy issues in non-critical paths, missing tests for new logic, performance issues that scale poorly but tolerate today's load.
- **Low** — code-quality, naming, dead code, comment/code mismatch with no functional impact, cosmetic.

If you cannot decide between two tiers, pick the higher one and explain the doubt in the finding body.

### 5. File a single GitHub issue

Use `gh issue create`. Format **exactly** as below.

**Title:**

```
Daily review YYYY-MM-DD: <N> finding(s)
```

- `YYYY-MM-DD` — UTC date of the run (`date -u +%Y-%m-%d`).
- `<N>` — total findings across all severities. If zero, use `Daily review YYYY-MM-DD: clean` instead.

**Body:**

```markdown
## Scope

- Window: last 24 hours on `master`
- Commits reviewed: <N_COMMITS>
- Range: `<oldest_hash>^..<newest_hash>`

## Commits

<bulleted list of `hash short_subject (author)` from step 1>

## Findings

### Critical
<entries — or `_None._` if empty>

### High
<entries — or `_None._` if empty>

### Medium
<entries — or `_None._` if empty>

### Low
<entries — or `_None._` if empty>
```

**Each finding entry follows this template:**

```markdown
- **<one-line title>** — `<path>:<line>` (commit `<hash>`)

  <what is wrong, in 1–3 sentences>

  **Reproduction / evidence:** <how a reader can verify, e.g. a code snippet or a runtime scenario>

  **Suggested fix:** <one sentence; do not write code unless trivial>
```

If there are no findings, replace the four severity sections with a single line:

```markdown
Review clean — no issues found in <N_COMMITS> commit(s).
```

**Label:** apply `automated-review`. Create the label with `gh label create automated-review --color BFD4F2 --description "Filed by the daily automated review workflow"` if it does not already exist (`gh label list | grep -q automated-review || gh label create ...`).

## What you must NOT do

- Do not edit any source file.
- Do not open or modify any pull request.
- Do not run the test suite or build (the workflow's purpose is review, not validation; CI handles validation on the original PRs).
- Do not file more than one issue per run, even if findings span unrelated commits.
- Do not include findings about commits outside the 24-hour window, even if you notice something while navigating history.
- Do not speculate about commits that aren't in the diff (e.g. "this might break X if combined with Y from last week"). Stick to what's observable in the in-scope commits.

## When unsure

If a commit's intent is unclear from the diff and message, read the surrounding files with `git show <hash>:<path>` to confirm context before writing a finding. A finding based on a misread of the diff is worse than no finding.

If you find what looks like a critical issue but cannot fully verify it within the runtime budget, file it as **High** with a `**Verification needed:**` line in the entry, rather than escalating to Critical or staying silent.
