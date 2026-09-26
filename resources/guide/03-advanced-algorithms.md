# 3 · Advanced algorithms for shared memory

> **What you will learn.** How to solve mutual exclusion for *n* processes
> using nothing but reads and writes of shared variables (Lamport's bakery
> algorithm, 1974); how to make the *common case* — no competition — fast
> (Lamport's fast mutual exclusion, 1987); and the sobering discovery that
> all these correct algorithms fail on modern hardware because processors are
> not sequentially consistent — plus the fix (memory barriers).
>
> As in chapter 2, every claim and counterexample here is machine-checked by
> exhaustive interleaving exploration (including a three-process
> verification of both algorithms).

## 3.1 Why "advanced" solutions

Chapter 2 solved the critical section problem *for two processes*. The
algorithms of this chapter add:

* solutions for an **arbitrary number of processes** (`n`), with
* refined statements of the concurrency abstraction and of the problem
  itself, and
* **no hardware or operating system support whatsoever**: only atomic reads
  and writes of shared memory.

Their practical utility is limited — real systems use the primitives of
chapter 4 — but their academic interest is enormous: they tell us exactly
what is *possible* with plain memory, they are the cleanest illustrations of
the critical section problem's structure, and (as you will see in §3.4) they
sharpen exactly the issues that make real hardware difficult.

## 3.2 The bakery algorithm (Lamport, 1974)

### 3.2.1 The idea

Every bakery has a ticket dispenser: each customer takes a number on
entering, and customers are served in number order; simultaneous takers of
the same number break the tie somehow (say, by name). Translated:

* A process that wants the CS **takes a number** (a positive integer,
  bigger than all current numbers).
* It then waits until **its number is the smallest** among all competing
  processes — processes not competing have number 0.
* **Ties** (two processes drawing the same number) are broken by **process
  identifier**: the smaller id wins.

The array `number[0..n−1]` holds each process's ticket (0 = not competing);
`max(number)` returns the largest current number; on entry, each process
scans *all* others and waits for each `j` whose pair beats its own.

### 3.2.2 Two processes, simplified (and why it is not enough)

For two processes the scheme collapses to two shared numbers `np`, `nq`
(0 = not competing; smaller wins; **tie → priority to p**), with the
number-taking *assumed atomic*:

```
shared: integer np = 0, nq = 0

      p                                q
while true do                     while true do
  NSC;                              NSC;
  p1: np <- nq + 1;                 q1: nq <- np + 1;
  p2: await (nq = 0 or np ≤ nq);    q2: await (np = 0 or nq < np);
  p3: CS;                           q3: CS;
  p4: np <- 0                       q4: nq <- 0
od                                od
```

This satisfies mutual exclusion and does not deadlock. But the atomicity of
`p1`/`q1` — "read the other's number *and* write mine" in one indivisible
step — is **not realistic**: those are two memory operations, and other
processes can interleave between them (chapter 1's LCR: the statement
contains two critical references). The full algorithm must tolerate a
process being *interrupted in the middle of taking its number*.

### 3.2.3 The full algorithm for n processes

The fix for the non-atomic number-taking is a second array, `choosing[]`,
announcing "I am in the middle of choosing a number — wait before comparing
with me":

```
shared: integer array number[0..n−1]  = all 0
        boolean array choosing[0..n−1] = all false

process i (id i, 0..n−1):
while true do
  NSC;
  b1: choosing[i] <- true;
  b2: number[i] <- max(number[0..n−1]) + 1;   -- NOT atomic: read max, then write
  b3: choosing[i] <- false;
  for j = 0 .. n−1, j ≠ i:
    b4: await choosing[j] = false;
    b5: await number[j] = 0
        or (number[i], i) << (number[j], j);
  CS;
  b6: number[i] <- 0
od
```

Notation (from the slides): `(number[i], i) << (number[j], j)` abbreviates

```
number[i] < number[j]   or   (number[i] = number[j] and i < j)
```

— lexicographic comparison of *(number, id)* pairs: smaller number first,
ties broken by smaller identifier. Process `i` waits for process `j` while
`j` is choosing a number (b4) or while `j`'s pair beats `i`'s (b5).

Notes:

* The **max is not atomic** — two processes taking numbers simultaneously can
  read the same maximum and draw the **same number**. That is precisely why
  the id tie-break exists. (Exercise 3.2.)
* If a process stays in the CS **forever while others keep coming**, numbers
  grow without bound: `max+1` can always be larger. Real implementations
  need unbounded integers (or the wraparound care of exercise 3.3).
* Every entry scans all `2n` cells and executes up to `2(n−1)` awaits —
  **even when nobody else is competing**. This cost motivates §3.3.

### 3.2.4 Why the naive version (without `choosing`) is wrong

Drop `choosing[]` and the algorithm *looks* fine — but the window between
reading `max` and storing `number[i]` is fatal. Machine-checked
counterexample (the slides' scenario, ids 0 and 1):

| step | statement | number[] | comment |
|---|---|---|---|
| 1 | p0 b2: reads max = 0 | [0, 0] | p0 about to write 1 — **interrupted here** |
| 2 | p1 b2: reads max = 0 | [0, 0] | nobody's number visible yet |
| 3 | p1 b2: writes number[1] = 1 | [0, 1] | |
| 4 | p1 b5 for j=0: number[0] = 0 → pass | [0, 1] | "p0 is not competing" — stale view |
| 5 | p1 enters CS | [0, 1] | |
| 6 | p0 b2: writes number[0] = 1 | [1, 1] | p0's number finally lands |
| 7 | p0 b5 for j=1: tie 1=1, id 0 < 1 → pass | [1, 1] | tie-break says *p0 wins* |
| 8 | p0 enters CS — **both in CS** ✘ | [1, 1] | |

p1 scanned p0 while p0 was mid-way through taking its number: `number[0]`
was still 0, so p1 concluded "not competing". The `choosing[]` array closes
exactly this window: at b4, process `i` first waits until `j` has *finished*
choosing. Then either:

* `j` had already finished before `i` started: then `number[j]` is final,
  and the comparison at b5 uses the true value; or
* `j` starts choosing after `i`'s b4 saw `choosing[j] = false`: then `j`'s
  max-read at b2 happens *after* `i` wrote `number[i]` (i wrote it before
  its scan), so `number[j] > number[i]` — `j` goes behind `i`.

Either way no two processes can pass each other symmetrically.

### 3.2.5 Properties

*(All machine-checked for n = 2 and n = 3: no reachable state violates
mutual exclusion; no deadlock.)*

* **Mutual exclusion.** Two processes in the CS together would have had to
  pass each other at b5 in both directions: `i` passed `j` (with
  `number[j] ≠ 0`) so `(number[i], i) << (number[j], j)`, and `j` passed
  `i` so `(number[j], j) << (number[i], i)`. Lexicographic order is a
  *total* order on distinct pairs, and the ids are distinct — both
  comparisons cannot hold. The `choosing` awaits guarantee the numbers each
  process compared against were final (previous section).
* **Deadlock-free.** Among the competing processes, the pair
  `(number, id)` has a minimum; the minimal process is delayed by nobody
  (everyone else either has number 0 or a larger pair, and nobody is
  `choosing` forever). The worst case is a tie at the top — resolved by the
  id, so again exactly one process is unblocked. (In fact the algorithm
  tolerates reads of `number` overlapping writes returning arbitrary
  garbage: Lamport's original paper proves ME survives even that — see
  Going further.)
* **Starvation-free (bypass ≤ n−1).** Numbers are granted in increasing
  order: a newcomer's number exceeds *all* numbers present when it reads
  the max. So among any set of waiting processes, service is
  first-come-first-served by ticket: a process that draws a number enters
  before any process that draws *later*. If `n` processes want to enter at
  once, each waits at most `n−1` turns. A process that leaves the CS and
  immediately returns gets a number **larger than all currently waiting** —
  it goes to the back of the queue.
* **Costs.** `2n` shared cells; `O(n)` reads per entry even without
  competition; unbounded number sizes. (The `O(n)` registers are in fact
  *optimal* for read/write-only solutions — Burns & Lynch, 1980; see
  chapter 2's Going further.)

## 3.3 Lamport's fast mutual exclusion algorithm (1987)

### 3.3.1 Motivation

The bakery's costs are paid on *every* entry: 2n cells of shared state and a
full scan of both arrays — even when the CS is completely free. Lamport's
1987 algorithm asks: can the **uncontended case be constant-time**, with a
*bounded* number of shared variables, while still needing only reads and
writes? (In real systems this "optimize the common case" idea is everywhere:
fast paths in locks, lock-free reads, read-copy-update.)

The course presents it with a **two-doors** metaphor, following Ben-Ari:
picture a corridor to the critical section with two doors. Each door is a
single shared variable — a slate that holds the *id* of a process (or 0 if
empty):

* `gate1` — the first door, by the entrance;
* `gate2` — the second door, just before the CS;
* `wantp`, `wantq` — flags saying "I am somewhere inside the entry protocol"
  (generalized to an array `want[]` for n processes).

### 3.3.2 The protocol, narrated

A process entering the protocol:

1. *(door 1)* writes **its own id** on `gate1` — "I came in through here".
2. looks at `gate2`: if **someone else's id is there** (door 2 is occupied —
   that process is closer to the CS), back off: leave the corridor entirely
   (lower `want`), wait for `gate2` to be erased, and start over.
3. *(door 2)* writes **its own id** on `gate2` — claiming the door just
   before the CS.
4. looks **back at door 1**: is my id still written there?
   * If **my id is still on `gate1`** — nobody came in behind me — proceed.
   * If **another process overwrote `gate1`** (someone is coming up behind
     me), there are two possibilities:
     * my id is still on `gate2` → the newcomer will find door 2 occupied
       and back off; I may proceed to the CS;
     * my id has been *replaced on `gate2`* — the newcomer overtook me — so
       I must yield: back off (lower `want`), wait for the corridor to clear,
       and start over.
5. Having entered and finished the CS: **erase my id from `gate2`** (and
   lower `want`).

The intuition for mutual exclusion: to reach the CS you must hold `gate2`
(your id is the last one written there), and the look-back at `gate1` plus
the back-off protocol ensures nobody can *replace* your id on `gate2` and
also reach the CS — whoever is behind is forced to back off at door 2, and
whoever overtook at door 2 made the other's look-back fail.

### 3.3.3 The algorithm (two processes; ids 1 and 2)

```
shared: integer gate1 = 0, gate2 = 0        -- doors: process id, 0 = empty
        boolean wantp = false, wantq = false

process p (id 1):
while true do
  NSC;
  p1:  wantp <- true;
  p2:  gate1 <- 1;                          -- door 1
  p3:  if gate2 ≠ 0 then                    -- door 2 occupied:
         p3a: wantp <- false;               --   back off entirely,
         p3b: await gate2 = 0;              --   wait for it to be freed,
         p3c: wantp <- true;                --   and compete again
         goto p2;
       end if;
  p4:  gate2 <- 1;                          -- door 2
  p5:  if gate1 ≠ 1 then                    -- look back at door 1:
         p5a: wantp <- false;               --   back off:
         p5b: await wantq = false;          --   wait until q is out of the game,
         p5c: if gate2 = 1 then goto CS;    --   my id still on door 2 -> enter
         p5d: await gate2 = 0;              --   q overtook me: wait, then retry
         p5e: wantp <- true;
         goto p2;
       end if;
  CS;
  p6:  gate2 <- 0;                          -- erase door 2
  p7:  wantp <- false
od
```

(`q` is symmetric with id 2.) Structure your reading around the three
scenarios of §3.3.2: no conflict (p2–p5 all pass — the **fast path**),
conflict at door 2 (p3's branch), conflict at door 1 (p5's branch, with its
two endings p5c / p5d).

**Generalization to n processes** (as in the slides): nothing changes in the
corridor — `gate1` and `gate2` stay *single* shared variables — but the
back-off wait of p5b must now wait for *all* other processes to be out of
the game:

```
p5b: for all other processes j: await want[j] = false;
```

That is the role of the `want` flags: they are the *signals that a process
has definitively backed off* (or finished), which is what makes the slow path
terminating rather than a blind spin on the gates. (A gates-only variant —
no `want` flags, losers simply re-looping through the doors — turns out
still to guarantee mutual exclusion for small n; machine-checked for
n ≤ 3. But its losers hammer the gates in a tight loop with no bound on
waiting, and the structured back-off is what makes the algorithm's proof and
its behavior manageable. The flags are the difference between "loitering in
the doorway" and "stepping out and waiting to be recalled".)

### 3.3.4 Properties

*(Machine-checked for n = 2 (563 states) and n = 3 (20 151 states): mutual
exclusion holds, no deadlock.)*

* **Mutual exclusion.** Sketch for the two direct fast-path entries: for p
  to enter at p5, `gate1 = 1` at p's check; for q to enter, `gate1 = 2` at
  q's check. `gate1` is a single variable — order the two checks in time;
  the later check requires the *other* process's door-1 write to have
  happened after the earlier process's write, which (chasing the program
  orders p2 < p4 < p5) forces the later process to have seen the earlier
  one's non-empty `gate2` at *its* door-2 check — contradiction. The mixed
  cases (one fast, one slow) are handled by the `want` back-off: whoever
  takes the slow branch waits for the other's `want` to drop, which happens
  only when that other has finished its CS.
* **Deadlock-free.** If both processes are at the awaits, either a gate is
  empty (a `p3b`-style wait ends) or one of them owns `gate2` and its
  opponent has lowered `want` (the `p5b` wait ends for the owner). No state
  is fully blocked (verified).
* **Bounded waiting: FAILS (by design).** A process can be repeatedly
  overtaken: each time it writes door 1, a fresh competitor may write door 2
  first. There is no bound on the number of times a process backs off while
  others enter. Lamport's paper trades fairness for speed of the common
  case. (The bakery of §3.2 is the fair one; the fast algorithm is the
  quick one.)
* **The fast path is genuinely fast:** one entry+exit with no competition
  costs a *constant* number of shared-memory accesses — write `want`, write
  `gate1`, read `gate2`, write `gate2`, read `gate1`, then on exit write
  `gate2 = 0` and `want = false` — **independent of n**, versus the
  bakery's O(n) scan over 2n cells.

## 3.4 Sequential consistency — the catch

> All the algorithms of chapters 2 and 3 are correct *and yet they do not
> work correctly on most modern processors*: measured runtimes blow up
> (excessive CPU time) and, worse, mutual exclusion can be violated.

How can a proven algorithm fail? Because the proofs assumed the interleaving
model of chapter 1 — in particular that memory behaves as a single
sequentially consistent store. Real machines do not.

### 3.4.1 The definition

> A multiprocessor is **sequentially consistent** (Lamport, 1979) if the
> result of any execution is the same as if the operations of all processors
> were executed in *some sequential order*, and the operations of each
> individual processor appear in that order in *program order*.

Informally: there is always a coherent story "everything happened one thing
at a time, and each process's own operations kept their order". The
interleaving model *is* sequential consistency (with the extra simplification
that atomic statements are indivisible). All our proofs implicitly said:
"the read of `wantq` returns the value of the *last write* in the global
interleaved order".

### 3.4.2 Why real machines are not sequentially consistent

Three culprits, in increasing order of difficulty:

1. **Compiler optimizations.** The compiler reorders, merges and eliminates
   memory accesses it considers independent (chapter 1's caching of `n` in a
   register is the mildest case). A `volatile` (Java/C qualifier) prevents
   *the compiler* from doing this to that variable. This is not theoretical:
   while testing this guide's code, clang at `-O2` deleted Peterson's `last`
   variable outright by forwarding its store to the following load (details
   in chapter 2.10's box) — atomics intrinsics on a *plain* variable are not
   enough; the variable itself must be `volatile`/`_Atomic`/atomic.
2. **Cache incoherence and visibility delay.** Each core has private
   caches/write buffers; a write by core 1 may be *visible to core 2 much
   later* than a write by core 2 that happened after it. Coherence hardware
   eventually makes writes visible, but *not simultaneously, and not in
   order across different variables* unless forced. This is also the source
   of the performance problem the slides mention: spinning on shared flags
   (`await`!) generates a storm of cache-coherence traffic that slows every
   core, even ones not involved.
3. **Out-of-order (dynamic) execution.** To avoid waiting for memory, the
   processor itself reorders loads and stores whenever no *single-thread*
>  semantics depend on the order — i.e., always, for accesses to *different*
>  variables, which is exactly our flags and turn variables.

### 3.4.3 The killer example: Dekker/Peterson on real hardware

The classic litmus test ("store buffering", the pattern at the heart of
Dekker's and Peterson's awaits):

```
core 1:                  core 2:
x <- 1                   y <- 1
r1 <- y                  r2 <- x
```

* On a **sequentially consistent** machine, the interleaving story forbids
  `r1 = 0 ∧ r2 = 0`: one of the two stores is first in the global order, and
  the later read must see a 1.
* On **real** x86 (which is *almost* sequential — it only relaxes
  store→load order): both stores may still sit in the cores' private store
  buffers when both loads execute. Each load hits the cache, sees the *old*
  0, and only afterwards do the stores flush. Result: `r1 = 0 ∧ r2 = 0` —
  observable on real hardware, impossible under SC.

Map this onto Peterson: p executes `wantp <- true; last <- 1;` then its
await reads `wantq` and `last`. If the *loads* of the await execute while
p's own *stores* are still buffered (store→load reordering), p can read a
**stale** `wantq = false` and enter; symmetrically for q. Both in the CS:
mutual exclusion gone — not because the algorithm is wrong, but because the
machine executed a "schedule" that does not exist in the interleaving model.

### 3.4.4 Memory barriers

The fix is a class of instructions called **memory barriers** (or *fences*):
`mfence` (x86), `dmb` (ARM), `membar` (SPARC), etc. Placed in the
instruction stream, a full barrier tells the processor:

* all reads/writes **before** the barrier must complete globally **before**
  any reads/writes **after** the barrier start.

The effect is to *re-establish* (part of) the program-order guarantee that
the hardware dropped. For Dekker/Peterson, a full barrier between the flag
stores and the await's loads restores correctness: each process now sees the
other's committed flags before deciding.

Barriers are expensive — they stall the pipeline, flush buffers, and
generate coherence traffic — so high-performance code uses the *minimum*
ordering needed (weaker barriers, acquire/release semantics: chapter 4's
Going further). All the convenience APIs you actually use compile down to
these fences:

* **Java:** accesses to `volatile` variables are sequentially consistent
  among themselves (total order over volatile accesses; each read sees the
  last volatile write in that order) — exactly the guarantee Peterson's
  proof needs. That is why the Java implementations in
  [`code/java/CounterPeterson.java`](code/java/CounterPeterson.java) mark
  every shared variable `volatile`.
* **C/C++:** C11 atomics / GCC's `__atomic_*` builtins with
  `__ATOMIC_SEQ_CST` emit the necessary fences; plain `volatile` in C is
  *not* sufficient (no inter-thread ordering — historical design).
* **Go:** `sync/atomic` loads/stores are sequentially consistent.

So the honest summary of chapters 2–3 is: *the algorithms are correct as
mathematics; to run them as software you must access their variables with
SC semantics (volatile/atomics/fences)* — or better, use the hardware
primitives of chapter 4, which build the fences in.

## 3.5 Summary

* **Bakery** (n processes, reads/writes only): tickets `number[i] =
  max+1`, wait for all `j` with `(number[j], j) << (number[i], i)`; the
  `choosing[]` array protects the non-atomic number-taking; ids break ties;
  ME + deadlock-free + FCFS (bypass ≤ n−1); costs 2n cells, O(n) per entry,
  unbounded numbers.
* **Fast mutual exclusion** (Lamport 1987): two "doors" (`gate1`, `gate2`)
  + `want[]` back-off flags; constant-size shared state, constant-time
  fast path when uncontended; ME + deadlock-free but **no bounded waiting**
  (overtaking by design).
* **Sequential consistency** is the assumption all these proofs rest on;
  compilers (fix: `volatile`), cache visibility, and out-of-order execution
  (store buffers) break it; **memory barriers** restore the needed
  ordering at a performance cost; language-level atomics/volatile give you
  the guarantee portably.

## 3.6 Exercises

**3.1** Write the interleaving table for the bakery *without* `choosing[]`
(simplified n-process version) in which both processes enter the CS, using
ids 0 and 1 and numbers 2 and 1 (this is the machine-checked trace found by
the verification: numbers end at `number = [2, 1]`, both in CS).

**3.2** Why exactly is the identifier tie-break indispensable? Analyze the
two symmetric policies: (a) on equal numbers, *both* pass; (b) on equal
numbers, *both* wait.

**3.3** An engineer "fixes" the unbounded-number issue by storing numbers in
2-bit fields (values clamp at 3). When we machine-checked that variant, the
checker found states with two processes simultaneously in the CS (for
example both holding number 3). Explain the failure and why clamping — as
opposed to proper modular wraparound handling — breaks the bakery's core
invariant.

**3.4** Count the shared-memory operations of one *uncontended*
entry+exit of the fast algorithm (§3.3.3). Then trace the *conflict at door
1* scenario in detail: q overwrites p's id on `gate1` after p wrote `gate2`;
show both endings (p5c and p5d).

**3.5** For the litmus program of §3.4.3 (x, y initially 0), enumerate the
outcomes possible under sequential consistency, and state which outcome
real x86 adds. Which single reordering (a pair "store X then load Y")
explains it?

**3.6** For each mechanism, say which of the three culprits of §3.4.2 it
addresses — compiler reordering (C), cache visibility/ordering (M for
memory), out-of-order execution (O) — and whether it establishes
*sequential consistency* or something weaker/stronger: (a) C `volatile`;
(b) Java `volatile`; (c) `__atomic_store_n(..., __ATOMIC_SEQ_CST)` /
`__atomic_load_n(..., __ATOMIC_SEQ_CST)`; (d) `mfence` between the stores
and loads.

## 3.7 Solutions

**3.1** Machine-checked trace (ids 0 and 1, `number` initially `[0,0]`):

| step | statement | number | comment |
|---|---|---|---|
| 1 | p0 b2: reads max = 0 → t0 = 2 | [0, 0] | p0 **interrupted** before storing |
| 2 | p1 b2: reads max = 0 → t1 = 1 | [0, 0] | p1 also **interrupted** before storing |
| 3 | p0 b2: writes number[0] = 2 | [2, 0] | |
| 4 | p0 b5 (j=1): number[1] = 0 → passes; p0 CS | [2, 0] | "p1 is not competing" — stale: p1 is mid-taking |
| 5 | p1 b2: writes number[1] = 1 | [2, 1] | |
| 6 | p1 b5 (j=0): number[0] = 2 ≠ 0, (1,1) << (2,0)? 1 < 2 → passes; p1 CS | [2, 1] | **both in CS** ✘ |

p0 scanned p1 while p1's number had not landed yet; p1 scanned p0 against
the *final* value and legitimately won the comparison. Every violating
interleaving of the choosing-free bakery has this shape: **at least one
process compares against a number that has not yet landed** — exactly the
window that `choosing[]` closes (b4 forces the comparison to wait until the
number is final).

**3.2** (a) both pass on ties: immediate ME violation whenever two
processes draw equal numbers (guaranteed possible since max-reading is not
atomic). (b) both wait on ties: the two processes deadlock in front of each
other (each waits for a strictly smaller pair that never comes). The
asymmetric rule (smaller id passes) makes the outcome *exactly one* — a
total order on (number, id) pairs can never have two minima nor zero
minima among the waiting set.

**3.3** The bakery invariant is: *a newcomer's number is strictly greater
than every number it observed* (it wrote `max+1` after reading `max`).
Clamping turns `max+1` into `min(max+1, 3)`: when the maximum is already 3,
the newcomer writes 3 — *equal*, not greater. Now two processes can both
hold 3; the tie-break sends the smaller id in regardless of who arrived
first — and the waiting one may already be inside (its number was also
clamped from 4 to 3): ME violation. (Exactly what the model checker found.)
Proper bounded solutions compare numbers *modulo* the range with wraparound
arithmetic (like sequence numbers in TCP), or use the hardware ticket of
chapter 4 where `get&add` makes taking a number atomic and monotonic.

**3.4** Fast path: `wantp <- true` (1 write), `gate1 <- 1` (write), read
`gate2` (read), `gate2 <- 1` (write), read `gate1` (read) → enter CS; exit:
`gate2 <- 0` (write), `wantp <- false` (write). **5 accesses to enter, 2 to
leave — 7 in total, independent of n.** Door-1 conflict: p has written
`gate1 = 1`, `gate2 = 1`; q writes `gate1 = 2` (q's door 1). p's p5 reads
`gate1 ≠ 1` → takes the slow branch: lowers `wantp`. Case p5c: q, coming
behind, reads `gate2 ≠ 0` at *its* door-2 check (p's 1 is there) → q backs
off to p3a, lowers `wantq`. p's p5b (`await wantq = false`) passes; p5c
reads `gate2 = 1` still — p enters the CS; q restarts at door 1 and will
bounce at door 2 until p's p6 erases it. Case p5d: q came *so fast* that it
already overwrote `gate2 = 2` (its p4) before p's p5c check; then p5c reads
`gate2 ≠ 1` → p5d awaits `gate2 = 0` (which q's exit will produce), then
p5e/p2: p retries from door 1. In both cases exactly one of the two is
inside at any moment.

**3.5** SC outcomes for `(r1, r2)`: (0,1), (1,0), (1,1) — not (0,0): if
both loads were preceded in the global order by both stores, both reads see
1; otherwise the first load in the order sees 0 and, by then, the *other*
store has committed (its process's store precedes its load in program
order), so the second read sees 1. Real x86 adds **(0,0)** via the single
relaxation **store→load**: each core's load executes before its own store
becomes globally visible. (Processors/buses that also relax store→store or
load→load/load→store add more outcomes on other architectures.)

**3.6** (a) C `volatile`: addresses C only (and cache *caching* by the
compiler); no M/O guarantees at all — much weaker than SC. (b) Java
`volatile`: C fully; M/O for volatile accesses — gives SC *among volatile
variables* (plus atomicity of long/double). (c) seq_cst atomics: C, M and O
— participate in a global total order with all other seq_cst operations:
SC. (d) `mfence` between stores and loads: forbids the store→load
reordering across it — precisely enough (together with x86's other
orderings) to make the litmus/Peterson pattern safe: effectively SC for
that program, at the cost of a pipeline stall each time.

## 3.8 Going further

* **Lamport's papers:** the bakery (CACM 1974) opens with the real bakery
  story and — remarkably — proves the algorithm tolerates reads that return
  arbitrary values while a write is in progress ("safe registers"); "On
  interprocess communication" (Distributed Computing, 1986) is the
  systematic study of safe/regular/atomic registers you will meet again in
  chapter 4; the fast algorithm (ACM TOCS 1987) discusses the cost model
  (reads/writes of shared variables) in depth.
* **Memory models:** P. McKenney, *Memory Barriers: a Hardware View for
  Software Hackers*
  ([PDF](http://www.rdrop.com/users/paulmck/scalability/paper/whymb.2010.07.23a.pdf))
  — store buffers, invalidate queues, and why each barrier type exists;
  his free book *Is Parallel Programming Hard* covers the same for
  software. For x86 TSO formally: Sewell et al., *x86-TSO*.
* **C11/C++11 memory model:** `memory_order_relaxed/acquire/release/
  acq_rel/seq_cst` — an entire spectrum between "no ordering" and SC;
  chapter 4's primitives map onto it. Herb Sutter's *atomic<> weapons*
  talks are the accessible version.
* **Why the bakery's O(n) is optimal:** Burns & Lynch (1980), mutual
  exclusion with reads/writes needs n registers; see also the *filter*
  algorithm (Peterson) and Aravind's bounded-ticket bakery for what
  bounded numbers *can* achieve.
* **Tools:** litmus tests and the `herd7` memory-model simulator (the
  academic standard for "what may this architecture do"); ThreadSanitizer
  and Go's race detector for finding these bugs in practice.

---

Next: [04-hardware-synchronization.md](04-hardware-synchronization.md) —
giving up on "reads and writes only": atomic read-modify-write instructions,
spinlocks and ticket locks, and the end of the story for our shared counter.
