# AGENTS.md — guide/

Instructions for AI coding agents working in `resources/guide/`.

## What this directory is

`guide/` is a standalone, self-study rewrite of the Concurrent Programming
course material (course 21720, UIB; the source PDFs live in the parent
`resources/` directory). The user is a CS student writing it in English for
personal study; the course itself is taught in Catalan.

Structure:

* `README.md` — index, notation conventions, English↔Catalan glossary,
  code map. Update the chapter table when adding chapters.
* `01-introduction.md` … `06-monitors.md` — numbered chapters.
* `code/{c,java,go,python,ada}/` — runnable examples referenced from the
  chapters by relative links.

## Chapters and their sources

| Chapter | Source PDF | Topics |
|---|---|---|
| 01 | `01_Introduccio.pdf` | interleaving model, threads (Java/Python/C/Go/Ada), GIL, atomicity, LCR, volatile, races |
| 02 | `02_RegioCritica.pdf` | critical section problem, attempts 1–4, Dekker, Peterson |
| 03 | `03_AlgorismesAvançats.pdf` | bakery, Lamport fast mutual exclusion, sequential consistency, memory barriers |
| 04 | `04_SolucionsPerHardware.pdf` | RMW primitives, spinlocks, ticket lock, CAS, lock-free |
| 05 | `05_Semafors.pdf` | semaphores (wait/signal, binary/counting, Bartz), barriers, producer–consumer, split semaphores, philosophers (LR, Tanenbaum), bear & honey pot |
| 06 | `06_Monitors.pdf` | monitors (Hoare/Java/Ada), condition variables, priority schemes & IRR, semaphore simulation, producer–consumer, readers–writers, philosophers, honey pot |

More slide sets are expected later (channels/messages, consensus). When
they arrive: create `NN-topic.md` following
the same chapter skeleton (overview → sections → summary → exercises with
solutions → going further), add the row to the README table, and only then
delete/replace source material claims.

## Conventions

* **Language:** English text; keep the slides' notation. Key mappings:
  SC/NSC → CS/non-CS, `intercalat` → interleaving, `escenari` → scenario.
* **Pseudocode:** Ben-Ari style (`await B`, `<-` assignment, `p1/p2…` labels,
  `NSC`/`CS`, processes `p`/`q` or ids `0..n−1`). Compound atomic statements
  are written `<...>`.
* **Style:** explanations before formalism; proofs as short informal
  arguments; correct the slides silently (no "the slide says…"), but a
  labeled correction is fine when the slides are factually wrong.
* **Claims must be verified.** Every mutual-exclusion / deadlock /
  counterexample claim in the chapters was machine-checked by exhaustive
  interleaving exploration. Don't add or modify correctness claims without
  re-verifying (a ~200-line Python state-space checker is enough; it found
  real bugs in naive variants of the algorithms).
* **Code must be compiled and run** before committing it to a chapter. All
  `guide/code` examples were tested at `-O2` (C), JIT-warmed (Java), etc.
* **No comments in code** except a short file header (what it demonstrates +
  build command). Explanations belong in the chapters, which link to files.

## Hard-won pitfalls (do not regress these)

These were real bugs found while writing/testing this guide; the chapters
document them (02 §2.10, 03 §3.4.2, README notes):

1. **C:** every shared variable needs `volatile` (or C11 `_Atomic`), *including
   the protected data*. With plain variables + `__atomic` builtins, clang `-O2`
   deleted Peterson's `last` (store→load forwarding) and hoisted the plain
   counter out of the inlined lock. Verified fixed: `cc -O2 -pthread *.c`.
2. **Java:** `volatile boolean[] arr` makes only the *reference* volatile, not
   elements — broke Dekker. Use scalar `volatile` fields (`wantp`/`wantq`) or
   `AtomicIntegerArray`.
3. **Python (CPython ≥3.11):** the GIL only switches threads at back-edges and
   calls, so `n = n + 1` loops hide their race; `counter.py` forces the window
   with `time.sleep(0)` inside the read-modify-write.
4. **Algorithms:** clamping bakery ticket numbers breaks ME (found by the
   checker, exercise 3.3); the naive doors-only variant of Lamport's fast
   algorithm is ME-safe for small n but unbounded-waiting — the `want[]`
   back-off flags are structural, keep them.

## Build & test commands

```sh
# C (all files except semaphore_posix.c are macOS-runnable; that one needs Linux)
cd guide/code/c
for f in *.c; do cc -O2 -pthread "$f" -o /tmp/$(basename $f .c); done

# Java
javac -d /tmp/jclasses guide/code/java/*.java && java -cp /tmp/jclasses CounterUltimate

# Go (files are separate `package main` programs)
go run guide/code/go/gocounter_ultimate.go

# Python
python3 guide/code/python/counter.py

# Ada (needs gnat, e.g. via alire)
gnatmake -q guide/code/ada/counter.adb
```

Expected behavior: the race demos (`counter.c` at `-O0`, `counter.go`,
`Counter.java`, `counter.py`, `counter.adb`) print a **wrong** count; every
other program prints the **exact** expected count (the interactive
producer–consumer / philosophers / bear demos print `done` after all
operations complete). If a "correct" program drifts from exact, treat it as
a regression and investigate before blaming scheduling — every algorithm
here is deterministic-correct under its documented memory guarantees.

Two Python-specific notes: run demo scripts with `python3 -u` when piping
(stdout is block-buffered, which masks output of killed runs), and remember
CPython only switches threads at back-edges/calls (pitfall 3).
