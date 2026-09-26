# Concurrent Programming — A Self-Study Guide

Course 21720 · Programació Concurrent (UIB)

This is a standalone, corrected and expanded rewrite of the course material.
It assumes you are comfortable with programming (end of a CS degree) but no
prior concurrency knowledge. Every concept is explained from first principles,
following the notation and procedures of the course slides, but written to be
readable on its own — as if the slides did not exist.

**Every correctness claim and every counterexample in chapters 2 and 3 was
verified by exhaustive state-space exploration (model checking of all possible
interleavings).** Where the slides contain errors or confusing statements, this
guide silently presents the correct version and (occasionally) notes what the
common confusion is.

## Chapters

| # | File | Topic | Source |
|---|------|-------|--------|
| 1 | [01-introduction.md](01-introduction.md) | What concurrency is; threads in Java, Python, C, Go, Ada; the interleaving model; atomicity; critical references; volatile; race conditions | `01_Introduccio.pdf` |
| 2 | [02-critical-section.md](02-critical-section.md) | The critical section problem: requirements, four failed attempts, Dekker's algorithm, Peterson's algorithm | `02_RegioCritica.pdf` |
| 3 | [03-advanced-algorithms.md](03-advanced-algorithms.md) | Lamport's bakery algorithm, Lamport's fast mutual exclusion, sequential consistency and memory barriers | `03_AlgorismesAvançats.pdf` |
| 4 | [04-hardware-synchronization.md](04-hardware-synchronization.md) | Hardware synchronization: atomic read-modify-write instructions, spinlocks, ticket locks, compare-and-swap | `04_SolucionsPerHardware.pdf` |

More chapters will be added as new slide sets arrive (expected, per the course
syllabus: semaphores, monitors, channels/messages, producer–consumer,
readers–writers, consensus).

Each chapter ends with: a **summary**, **exercises with solutions**, and a
**going further** section with literature and advanced topics.

## Notation

The guide uses the pseudocode notation of the course (Ben-Ari style):

```
        p                                   q
  while true do                       while true do
    NSC;          non-critical part     NSC;
    wantp <- true;                      wantq <- true;
    await wantq = false;                await wantp = false;
    CS;           critical section      CS;
    wantp <- false                      wantq <- false
  od                                   od
```

* `p`, `q` (or numbered processes) — the sequential processes that make up the
  concurrent program.
* `NSC` — *non-critical section*: process code where no shared state is touched.
  A process may stay here forever (it may even terminate).
* `CS` — *critical section*: the code that must not be interleaved with another
  process's critical section. It must always finish (progress).
* `x <- e` — assignment.
* `await B` — waits until condition `B` becomes true, then proceeds. It is
  *one atomic statement*: the check and the proceed happen together. Implemented
  in practice as a busy-wait loop `while not B do skip`, but conceptually a
  single indivisible step.
* `await B then S` — waits for `B`, and when it holds, executes `S` atomically
  (used from chapter 3 onward).
* Labels `p1, p2, …` / `q1, q2, …` — statement identifiers. The **control
  pointer** (program counter) of each process points at the label of its next
  statement. The *state* of a concurrent program is the tuple of all control
  pointers plus the values of all variables.
* `<s1; s2>` — angle brackets mark a compound statement executed *atomically*,
  when we want to explicitly assume or demand atomicity of several operations.

## English ↔ Catalan glossary

The course is taught in Catalan; exams will use the Catalan terms. Keep this
table handy.

| English (this guide) | Catalan (slides/exams) |
|---|---|
| concurrency / concurrent programming | concurrència / programació concurrent |
| parallelism | paral·lelisme |
| process / thread | procés / fil |
| task (Ada) | tasca |
| interleaving | intercalat (intercalat exclusiu) |
| computation, scenario | càlcul, escenari |
| control pointer | apuntador de control |
| state diagram | diagrama d'estats |
| atomic statement | sentència atòmica |
| critical section (CS) | regió crítica / secció crítica (SC) |
| non-critical section (NSC) | regió no crítica (NSC) |
| mutual exclusion | exclusió mútua |
| deadlock | interbloqueig |
| starvation | inanició |
| bounded waiting | espera limitada |
| progress | progrés |
| busy wait (spin) | espera activa |
| race condition | situació de competició |
| critical reference | referència crítica |
| limited critical reference (LCR) | restricció de referència crítica limitada |
| shared memory | memòria compartida |
| shared variable | variable compartida |
| volatile variable | variable volàtil |
| semaphore | semàfor |
| sequential consistency | consistència seqüencial |
| memory barrier / fence | barrera de memòria |
| cache coherence | coherència de caché |
| read-modify-write | lectura-modificació-escriptura (RMW) |
| spinlock | bloqueig giratori (spinlock) |
| ticket lock | (lock de torns; algorisme del forn amb get&add) |
| scheduler | planificador |
| context switch | canvi de context |
| preemption | preempció |
| livelock | bloqueig actiu |

## Code examples

Runnable implementations live in [`code/`](code/), one folder per language,
mirroring the examples referenced by the slides (plus new ones written for this
guide). Chapter text links to them.

```
code/
├── c/        POSIX threads + GCC __atomic builtins   (gcc -O2 -pthread x.c)
├── java/     Thread, Runnable, java.util.concurrent  (javac X.java && java X)
├── go/       goroutines + sync/atomic               (go run x.go)
├── python/   threading, the GIL                     (python3 x.py)
└── ada/      tasks, pragma Atomic/Volatile          (gnatmake x.adb)
```

Notes:

* Dekker/Peterson/bakery/fast in C and Java are written with **sequentially
  consistent** atomic/volatile accesses so that they are actually correct on
  modern hardware (see chapter 3 for why plain variables are not enough).
  Every shared variable — *including the protected counter* — is qualified:
  in C as `volatile` + `__atomic` builtins (plain variables get optimized
  even around atomic operations; chapter 2.10 documents a real deadlock this
  caused), in Java as `volatile` (scalar fields — a `volatile` *array* only
  covers the reference, not the elements) or `AtomicIntegerArray`.
* `counter*.?*` files demonstrate the race condition and each lock variant.
  `*_ultimate.*` show the "no lock needed" atomic-counter ending of chapter 4.
* `counter.c`, `counter.go`, `Counter.java` and `counter.adb` are *supposed*
  to print a wrong final value (that is the demo); everything else prints
  exact counts. `counter.py` needs the in-window GIL release explained in
  chapter 1.6.2 to show its race on modern CPython.

## Sources and further reading

* M. Ben-Ari, *Principles of Concurrent and Distributed Programming* (2nd ed.,
  Addison-Wesley, 2006) — the course's underlying textbook; the notation
  (`await`, attempts 1–4, Dekker, Peterson) comes from here.
* E. W. Dijkstra, "Cooperating sequential processes" (1965) — the origin of the
  critical section problem.
* G. L. Peterson, "Myths about the mutual exclusion problem", *IPL* 12(3), 1981.
* L. Lamport, "A new solution of Dijkstra's concurrent programming problem",
  *CACM* 17(8), 1974 (bakery); "How to make a multiprocessor computer that
  correctly executes multiprocess programs", *IEEE TC* 28(9), 1979 (sequential
  consistency); "A fast mutual exclusion algorithm", *ACM TOCS* 5(1), 1987.
* P. E. McKenney, *Memory Barriers: a Hardware View for Software Hackers* (2010)
  — [linux.org.rdrop link in chapter 3](http://www.rdrop.com/users/paulmck/scalability/paper/whymb.2010.07.23a.pdf).
* Rob Pike, *Concurrency is not parallelism* (talk, 2012) —
  <https://www.youtube.com/watch?v=oV9rvDtoKEg>.
* GCC atomic builtins:
  <https://gcc.gnu.org/onlinedocs/gcc/_005f_005fatomic-Builtins.html>
