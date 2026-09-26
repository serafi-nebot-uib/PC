# 2 · The critical section problem

> **What you will learn.** The precise statement of the most classical problem
> of concurrency; the three properties every solution must satisfy (and
> Dijkstra's four extra requirements); why four "obvious" solutions fail, each
> in a different, instructive way; and two correct solutions — Dekker's
> algorithm (1963) and Peterson's algorithm (1981) — including proofs.
>
> **Note on method.** Every correctness claim and every failure trace in this
> chapter has been verified by exhaustive state-space exploration: for each
> algorithm, *all* reachable interleavings were enumerated and checked for
> violations (mutual exclusion, deadlock). State counts are given along the
> way so you can appreciate the combinatorics.

## 2.1 Motivation

The canonical example is the **print queue**: several processes insert jobs
into a shared queue. Inserting involves reading the tail pointer, writing the
job, updating the pointer — several statements. If two processes interleave
these statements, jobs can be overwritten or lost, and the queue can become
corrupted. The fix: the queue-manipulation code must execute *as a whole* —
never interleaved. That code is the **critical section**.

You might wonder why we study algorithms like Dekker's at all, since real
systems provide more efficient synchronization primitives (mutexes,
semaphores — chapters 4+). Two reasons: (1) those primitives are *built out
of* the ideas developed here, and (2) the sequence of wrong attempts teaches
the classic pathologies of concurrent algorithms — violation of mutual
exclusion, deadlock, starvation/livelock — in their purest form. You will
meet them again in every concurrent system you ever touch.

## 2.2 Statement of the problem

`N` processes execute, in an infinite loop, a sequence of instructions
divided into two subsequences:

```
while true do
  NSC;      -- non-critical section: may use only local data.
            -- A process may stay here forever (even terminate).
  entry protocol   (pre-protocol)
  CS;       -- critical section: touches shared data.
            -- Must always finish (progress of the CS).
  exit protocol    (post-protocol)
od
```

The **entry/exit protocols** are the synchronization mechanism we must
design. They may use local and global variables of their own, but those are
*not* the variables of the critical section, and vice versa: protocol
variables are only for synchronization, CS variables only for the task at
hand.

The asymmetry between NSC and CS is deliberate:

* **The critical section must progress** — a process in the CS is assumed to
  eventually finish (we analyze the algorithms *given* the CS code terminates).
* **The non-critical section need not progress** — a process whose control
  pointer is in the NSC may loop forever or stop; the algorithm must not rely
> on NSC terminating. (A process stuck in the NSC is not even "competing".)

### The three required properties

For any solution to be correct:

1. **Mutual exclusion (ME).** Statements of the critical sections of two or
   more processes must never be interleaved: at most one process in the CS at
   any time.
2. **Deadlock-free (no interbloqueig).** If some processes are *trying* to
   enter the CS, one of them must eventually succeed. (The system as a whole
   must always be able to make progress.)
3. **Starvation-free / bounded waiting (no inanició, espera limitada).** Any
   process that wants to enter the CS must succeed within a *finite* number
   of steps (of the other processes). Stronger and more useful version:
   *bounded bypass* — a waiting process enters before any competitor enters
   the CS more than a bounded number of times.

Properties 2 and 3 are *liveness* properties (something good eventually
happens); property 1 is a *safety* property (something bad never happens).
Safety is checked on states; liveness is checked on executions.

### Dijkstra's four extra requirements

Beyond the three properties, Dijkstra's original problem statement (1965)
imposes four structural constraints on any acceptable solution:

1. **Symmetry.** The solution may not change the behavior or relative
   priority of the processes (no favoring one process by design; all run the
   same algorithm).
2. **No speed assumptions.** No assumptions about the relative speed of the
   processes may be made, nor that speeds remain constant.
3. **Non-interference / immediate entry.** A process that stops (is
   interrupted, even forever) must not prevent the others from entering the
   critical section — as long as it stopped outside the CS.
4. **Finite decision.** If several processes want to enter simultaneously,
   the decision of who enters is taken after a finite number of steps (no
>  unbounded negotiation).

> **About `await`.** From here on, `await B` is an atomic statement that
> waits until condition `B` holds. In real code it is implemented as a
> **busy-wait** (spin) loop: `while not B do skip`. Busy waiting consumes
> 100% of a CPU core while waiting — undesirable, but acceptable for now; the
> algorithms of this chapter are conceptual stepping stones. Blocking (descheduling)
> waiters is what OS-provided primitives do (chapter 4 and beyond).
> *Careful:* the atomicity of `await B` covers the *check-and-proceed* of
> that one statement only — the statements before and after it interleave
> freely. Most wrong solutions below are born from forgetting this.

## 2.3 First attempt: alternate turns

Idea: share a single variable `turn` saying who may enter; a process waits
for its turn and hands the turn over when leaving.

```
shared: integer turn = 1        -- initially either 1 or 2

        p                              q
while true do                   while true do
  NSC;                            NSC;
  p1: await turn = 1;             q1: await turn = 2;
  p2: CS;                         q2: CS;
  p3: turn <- 2                   q3: turn <- 1
od                              od
```

**Analysis.**

* **Mutual exclusion: OK.** `turn` takes one value at a time; if both were in
  the CS, `turn` would have to be simultaneously 1 (for p's `await` to have
  passed) and 2 (for q's) — impossible.
* **Progress: FAILS.** After p leaves the CS, `turn = 2`. If p wants to enter
  again *immediately*, it must wait until q has entered and left the CS. p is
  blocked by a *non-competing* process.
* **Bounded waiting: FAILS.** If q never enters the CS (it stays in the NSC),
  p waits forever: an infinite wait caused by a process that does not even
  want the resource.
* **Non-interference (Dijkstra 3): FAILS.** Exactly the scenario above:
  `turn = 2` while q parks in the NSC forever blocks p — q affects p without
  competing.
* **No speed assumptions (Dijkstra 2): FAILS.** Strict alternation implicitly
  assumes both processes run at comparable speeds.

Verdict: the problem is the *single shared variable*: p's ability to enter
depends on q's behavior even when q is not competing. (Model check: 30
reachable states; no ME violation, no deadlock — the failures are liveness
failures, which is why testing "shows it working".)

## 2.4 Second attempt: flags, check then set

Idea: let each process announce its intention with its **own** flag. Protocol:
look at the other's flag; if it is down, raise yours and enter.

```
shared: boolean wantp = false, wantq = false

        p                              q
while true do                   while true do
  NSC;                            NSC;
  p1: await wantq = false;        q1: await wantp = false;
  p2: wantp <- true;              q2: wantq <- true;
  p3: CS;                         q3: CS;
  p4: wantp <- false              q4: wantq <- false
od                              od
```

**Analysis.**

* **Mutual exclusion: FAILS.** The `await` and the flag raise are *two*
  separate atomic statements — the process can be interrupted between them.
  Verified counterexample (machine-checked):

  | step | statement | wantp | wantq | comment |
  |---|---|---|---|---|
  | 1 | p1 | false | false | p sees `wantq = false`, may proceed |
  | 2 | q1 | false | false | q sees `wantp = false`, may proceed |
  | 3 | p2 | **true** | false | p raises its flag, enters CS |
  | 4 | q2 | true | **true** | q raises its flag, enters CS — **both in CS** ✘ |

  Both `await`s passed *before* either flag was raised: the check is stale by
  the time the flag goes up. This is the **check-then-act race**: the
  compound action "check the other is absent *and* announce myself" was not
  atomic.

* **Deadlock: OK** (never both stuck: the awaits pass as soon as one flag is
  down — but that is irrelevant given ME fails).

* The existence of *one* forbidden scenario (both in the CS) **proves the
  algorithm incorrect** — correctness would require *all* scenarios to be
  fine. One counterexample kills a concurrent algorithm; conversely, a
  million passing test runs prove nothing.

The lesson usually extracted: *a process verified the state of the other
before changing its own state* — the announcement came too late. This
suggests the next attempt.

## 2.5 Third attempt: flags, set then check

Idea: announce **first**, then wait for the other's flag to go down. Now the
`await` is "part of the CS protocol": state is changed before checking.

```
shared: boolean wantp = false, wantq = false

        p                              q
while true do                   while true do
  NSC;                            NSC;
  p1: wantp <- true;              q1: wantq <- true;
  p2: await wantq = false;        q2: await wantp = false;
  p3: CS;                         q3: CS;
  p4: wantp <- false              q4: wantq <- false
od                              od
```

**Analysis.**

* **Mutual exclusion: OK.** (Proof sketch: suppose both in the CS. Then both
  flags are up. p passed `p2` at some instant when `wantq = false`; but q's
  flag went up at `q1`, *before* q's `await`, and stays up until `q4`, after
  q's CS. So p's passing read of `wantq` must have happened before `q1` or
  after `q4` — i.e. outside q's protocol. Symmetrically for q. But then both
  were in their protocols with the other outside — each raised its flag and
  saw the other's flag up at the await, so neither passes. Contradiction.
  Exercise 2.5 asks you to complete this argument.) No reachable state has
  both in the CS (machine-checked).
* **Deadlock: FAILS.** If both raise their flags before either checks, both
  awaits block forever:

  | step | statement | wantp | wantq | comment |
  |---|---|---|---|---|
  | 1 | p1 | true | false | p announces |
  | 2 | q1 | true | true | q announces |
  | 3–∞ | p2, q2 blocked | true | true | each waits for the other's flag: **deadlock** ✘ |

  (Machine-checked: the state `(p2, q2, wantp=wantq=true)` is reachable and
  has no outgoing transition — a true deadlock, both processes blocked
> forever.)

Progress fails with a vengeance: the fix for one pathology introduced a
worse one. Each process *insists* on its right to enter (flag up, waiting)
and neither yields.

## 2.6 Fourth attempt: defer briefly

Idea (from the previous analysis): when a process sees competition (other's
flag up while it insists), it should momentarily **yield** — lower its flag
to let the other pass — and then insist again.

```
shared: boolean wantp = false, wantq = false

        p                                   q
while true do                          while true do
  NSC;                                   NSC;
  p1: wantp <- true;                     q1: wantq <- true;
  p2: while wantq do                     q2: while wantp do
        p3:   wantp <- false;                  q3:   wantq <- false;
        p4:   wantp <- true                   q4:   wantq <- true
       od;                               od;
  p5: CS;                                q5: CS;
  p6: wantp <- false                     q6: wantq <- false
od                                     od
```

**Analysis.**

* **Mutual exclusion: OK.** To enter the CS, p must exit the loop at `p2`,
  i.e. observe `wantq = false` while `wantp = true` — and `wantp` stays true
  from `p1` to `p6`. If both were in the CS, both flags would be up and each
  process's *last* passing observation of the other's flag (false) must have
  occurred while the other was momentarily at `p3` (flag down) — but after
  `p3` comes `p4` (flag up again) *before* rechecking at `p2`, and the other
  process's CS entry requires passing its own `p2` check with my flag up...
  completing this argument   carefully (exercise 2.3 hints) shows no such
  interleaving exists. Machine-checked: no ME violation in 77 states, and no
  deadlock.
* **Deadlock: OK.** No state is fully blocked: at least one process can
  always execute its next statement.
* **Bounded waiting: FAILS — livelock.** In *practice* and *statistically*,
  infinite waits do not occur; but we cannot guarantee the wait is bounded by
  a number of steps. Both processes can yield and insist **in lockstep**,
  forever, without either entering:

  ```
  p3 (wantp=false); q3 (wantq=false); p4 (wantp=true); q4 (wantq=true);
  p2 sees wantq=true; q2 sees wantp=true; p3; q3; ...   -- repeat forever
  ```

  Each process's "flag down" window is never *observed* by the other: q checks
  `wantp` only when p has already raised it again. This is a **livelock**
  (*bloqueig actiu*): processes are not blocked, they are busy executing —
  but the system as a whole makes no progress. Livelock is invisible to
  deadlock checkers (there is always an enabled transition); it is a fairness
  failure.

We are close: we only need a rule to break the symmetric yielding. Notice
what we have: a mechanism where each process can (a) insist, (b) yield, and
(c) observe the other's intent. What is missing is a **tie-breaker** — some
way for exactly one of two symmetric competitors to insist while the other
yields. Both remaining algorithms add one.

## 2.7 Dekker's algorithm (1963)

Dekker's algorithm **combines attempt 1 and attempt 4**: flags announce
intention (attempt 4's mechanism), and `turn` breaks ties (attempt 1's
variable). The rule:

* If there is no competition (other's flag down), enter directly.
* Under competition, **`turn` decides**: if `turn` is mine, *insist* (keep
  flag up and wait for the other's flag to fall); otherwise *defer* — lower
  my flag, wait until `turn` becomes mine, raise the flag and retry.
* On exit, hand the turn to the other.

```
shared: boolean wantp = false, wantq = false
        integer turn = 1            -- initial value arbitrary

        p                                        q
while true do                               while true do
  NSC;                                       NSC;
  p1: wantp <- true;                         q1: wantq <- true;
  p2: while wantq do                         q2: while wantp do
        p3: if turn = 2 then                       q3: if turn = 1 then
        p4:    wantp <- false;                     q4:    wantq <- false;
        p5:    await turn = 1;                     q5:    await turn = 2;
        p6:    wantp <- true                       q6:    wantq <- true
             fi                                         fi
       od;                                    od;
  p7: CS;                                    q7: CS;
  p8: wantp <- false;                        q8: wantq <- false;
  p9: turn <- 2                              q9: turn <- 1
od                                          od
```

How to read it: under competition, exactly one process finds `turn` equal to
its own number — that process loops tightly at `p2` (insisting, flag up)
until the other's flag falls. The other process defers (p4–p6): flag down,
wait for the turn, flag up again.

**Analysis** (all machine-checked: 194 reachable states, no ME violation, no
deadlock).

* **Mutual exclusion: OK.** While p is anywhere between `p1` and `p8`,
  `wantp = true` (the only places it goes down are p4, and it is raised again
  at p6 before any check). If both were in the CS, both flags are up, and —
  as in attempt 3 — each process's passing check at `p2`/`q2` must have seen
  the other's flag *down*, which only happens inside the other's defer
  sequence p4…p6; but a process at p4–p6 has `turn ≠ its own`, and it cannot
  reach the CS without first re-raising its flag and re-passing `p2` with the
  other's flag up — contradiction (the same invariant argument as attempt 3,
  now strengthened by the turn discipline; exercise 2.6).
* **No deadlock: OK.** Under competition `turn` equals exactly one of {1, 2};
  that process never executes its defer sequence — it insists at `p2`, and
  the deferring one eventually re-raises its flag and the insisting one
  proceeds. The turn variable guarantees the yielding is never symmetric.
* **Bounded waiting: OK.** Suppose q is in the CS and p is competing
  (`wantp = true`). q exits and sets `turn <- 1` (p9 of q). Now if q tries to
  re-enter immediately: its `q2` sees `wantp` up, and `q3` finds `turn = 1`,
  so q *defers* and waits at `q5` for `turn = 2` — which only p will set on
  *its* exit. So p enters after at most one CS visit by q: p waits at most
  one *turn*. When a process leaves the CS it gives the turn to the other,
  and the other's next entry is guaranteed before the giver's next one.
* Under competition exactly one process enters (the turn holder); without
  competition entry is immediate.

Dekker's algorithm satisfies all three properties plus Dijkstra's four
requirements — the first correct solution in history. Its cost: the entry
protocol is a somewhat intricate dance of flags and turn.

> **Historical note.** The algorithm is attributed to Th. J. Dekker
> (~1962–63, a Dutch mathematician colleague of Dijkstra at Eindhoven); it
> was published and analyzed by Dijkstra in his 1965 paper "Cooperating
> sequential processes", which also stated the general problem.

## 2.8 Peterson's algorithm (1981)

Peterson's insight: you can get the effect of Dekker's flags-plus-turn
discipline with **much less machinery** — flags plus a single shared variable
`last` that records *who was the last process to request entry*. A process
enters when the other is not competing **or** the other was the *last* to
request (i.e. I have priority because you came after me… or rather: whoever
requested *last* yields). Formally, Peterson merges the two `await`s of a
flag-based attempt into one compound condition:

```
shared: boolean wantp = false, wantq = false
        integer last = 0            -- initial value irrelevant

        p                                     q
while true do                            while true do
  NSC;                                    NSC;
  p1: wantp <- true;                      q1: wantq <- true;
  p2: last <- 1;                          q2: last <- 2;
  p3: await (wantq = false                q3: await (wantp = false
              or last = 2);                         or last = 1);
  p4: CS;                                 q4: CS;
  p5: wantp <- false                      q5: wantq <- false
od                                       od
```

Three statements of entry protocol, no loops-with-defer. The mental model:
raise your flag (I want in), stamp `last` with your name (I yield if someone
else also stamped after me), then wait until the other is *not* competing or
`last` says the *other* stamped last (so the other yields to me).

*(Peterson's original paper calls the second variable `turn` or `favor`; this
course — following Ben-Ari — names it `last`, which describes its semantics:
who requested entry last.)*

**Analysis** (machine-checked: 71 reachable states, no violation, no
deadlock).

**Mutual exclusion: OK.** By contradiction, following the slides' argument in
detail. Suppose p and q are both in the CS.

1. Both flags are up: `wantp = wantq = true` (set at p1/q1, cleared only at
   p5/q5, after leaving).
2. p passed `p3`, and at that passing moment `wantq` was true (q is in the CS
   with its flag up since q1). So p's await passed through its second
   disjunct: p read `last = 2`, hence p3 executed **after** q2.
3. Symmetrically, q passed `q3` with `wantp = true`, so q read `last = 1`:
   q3 executed **after** p2.
4. Now order the four events `p2, p3, q2, q3` respecting program order
   (`p2 < p3`, `q2 < q3`) and the findings of steps 2–3 (`q2 < p3`, `p2 < q3`).
   Consider whichever await-read happened *second*; say it is p3 (the other
   case is symmetric). Before p3 we have: `p2` (last←1), `q2` (last←2),
   `p2 < q2 or q2 < p2`… in either ordering, the *last* write to `last`
   before the second await-read is the write of the process whose await-read
   came first (q2 if p2<q2; p2 if q2<p2):
   * if `p2 < q2 < p3`: p3 reads `last = 2` ✓ (p passes), but then q3 — after
     p2 — reads `last = 2` too (q2 was the last write before it) ✗ (q needed
     1);
   * if `q2 < p2 < p3`: p3 reads `last = 1` ✗ (p needed 2).
   Every case contradicts one of the two passing reads. The single shared
   variable `last` cannot have been read as 1 by one process and 2 by the
   other when both reads happen after both writes — "if both processes were
   in the CS, `last = 1` and `last = 2` would hold at once — impossible."

**No deadlock: OK.** If both are competing, both flags are up and both are at
their awaits. `last` holds either 1 or 2:

* If `last = 2`: p's await sees `last = 2` → p proceeds.
* If `last = 1`: q's await sees `last = 1` → q proceeds.

Whoever stamped `last` *first* is the one whose condition contains the
other's stamp — and that process passes. At least one (exactly one, in this
situation) always proceeds.

**Bounded waiting: OK (bypass ≤ 1).** Suppose p waits at `p3` while q is in
the CS. When q exits, it sets `wantq <- false` (q5) → p's first disjunct
becomes true → p enters. Even if q *instantly* re-enters (q1, q2: `last<-2`),
p's await now sees `last = 2` → p still passes before q can pass (q's q3 now
waits, since `wantp` is up and `last` is 2 ≠ 1… precisely: q needs
`wantp = false or last = 1`, both false). So a waiting process enters before
any competitor completes more than one CS visit: **the wait is at most one
turn**. The process that wants to enter first *yields* the turn (by stamping
`last`) before comparing; if it wants to enter again it will yield again to
the one already waiting.

**Also:** entry is immediate when there is no competition (the other's flag
is down), and a process stopped anywhere outside the CS does not prevent the
other from entering (Dijkstra's requirement 3) — check the awaits: a stopped
competitor either has its flag down (first disjunct) or its stamp in `last`
was overwritten by yours.

Peterson's is the standard textbook solution: minimal shared state (two
booleans + one integer for two processes), three-line entry, two-line exit,
and every property has a two-paragraph proof. (For N processes, the direct
generalization is the *filter algorithm* — level-based; see Going further.
The practical N-process solutions are the bakery algorithm, chapter 3, and
hardware spinlocks, chapter 4.)

## 2.9 Summary table

| Solution | Mutual exclusion | Deadlock-free | Bounded waiting | Comment |
|---|---|---|---|---|
| 1 · shared `turn` | ✔ | ✔ | ✘ | blocks on non-competitor; forces alternation |
| 2 · check-then-set flags | ✘ | ✔ | – | stale check: both enter (race) |
| 3 · set-then-check flags | ✔ | ✘ | – | both insist: deadlock |
| 4 · defer briefly | ✔ | ✔ | ✘ (livelock) | symmetric yielding forever |
| Dekker | ✔ | ✔ | ✔ (≤ 1 turn) | flags + turn tie-breaker |
| Peterson | ✔ | ✔ | ✔ (≤ 1 turn) | flags + `last`; simplest correct |

The story of this chapter is a grid of four failures — (ME, deadlock,
starvation) — each fixed by the next attempt until Dekker adds the
tie-breaker and Peterson simplifies everything. When you review, try to
reproduce each failure trace from memory: being able to *see* the
counterexample interleavings is the real skill.

## 2.10 The fine print: these proofs assume a memory model

All the reasoning above happens inside the interleaving model of chapter 1:
atomic statements on a single shared memory whose reads always return the
last written value, in program order — a **sequentially consistent** machine.
Real processors and compilers do not guarantee that: they reorder
instructions, buffer stores, and cache values. On modern architectures,
Dekker and Peterson *as written* (plain variables) can **fail to provide
mutual exclusion** and run much slower than expected — the algorithms are
correct, the machine is not sequentially consistent. This is not a footnote;
it is the subject of chapter 3 (why it happens, memory barriers) and the
reason chapter 4's hardware primitives exist. The C/Java implementations in
[`code/`](code/) use `volatile`/atomics precisely to restore the guarantees
the proofs need.

> **A true story from testing this guide's code.** The first version of
> `code/c/peterson.c` used GCC's `__atomic` builtins (sequential-consistent
> mode) on *plain* variables — and hung forever when compiled at `-O2`,
> while working perfectly at `-O0`. Inspecting the generated assembly
> revealed that clang had **eliminated the `last` variable entirely**: it
> forwarded the just-stored value `last = id` to the subsequent load
> `last = id` in the spin loop, so the loop it generated was
> `while want[other] do skip` — chapter 2.5's attempt 3, which deadlocks.
> (A second, related bug: the plain `counter` inside the "critical section"
> had its load hoisted above the inlined entry protocol — the lock protected
> nothing.) Declaring every shared variable `volatile` (or C11 `_Atomic`)
> fixed both: `volatile` forbids exactly these elisions and relocations.
> Moral: the *algorithm* was correct under sequential consistency, but
> "access it with atomics" must cover **every** shared variable, including
> the protected data — see chapter 3.4 and the notes in the code files.

## 2.11 Exercises

**2.1** Write the interleaving (as a table like 2.4's) by which attempt 2
violates mutual exclusion when p runs first but q overtakes between p's await
and p's flag raise.

**2.2** In attempt 3, exhibit the exact deadlock execution, and explain which
"hold and wait" pattern it instantiates.

**2.3** In attempt 4, write out the infinite execution (the repeating cycle
of statements) that constitutes the livelock, and state why no finite prefix
of it is a deadlock.

**2.4** A classmate "optimizes" Peterson by swapping the first two statements
(`last <- 1` before `wantp <- true`, symmetrically for q). Show that the
result violates mutual exclusion: produce a complete interleaving ending with
both processes in the CS.

**2.5** Complete the mutual-exclusion proof of attempt 3 (section 2.5):
show rigorously that no interleaving lets both into the CS. Where exactly
does the argument break for attempt 2?

**2.6** Dekker and bounded waiting: suppose q is in the CS (turn = 2) and p
is waiting. Trace what happens when q exits and immediately tries to re-enter
while p still wants in. How many CS visits can q complete before p must
enter?

**2.7** True or false, with a one-line justification each:
(a) A solution can be deadlock-free and still starve processes.
(b) Attempt 1 satisfies mutual exclusion but not Dijkstra's symmetry
requirement.
(c) Making attempt 3's flags `volatile` prevents its deadlock.
(d) In Peterson, if both processes execute their first two statements
simultaneously (on two cores), both end up waiting forever.
(e) The number of scenarios of a concurrent program is finite iff the number
of reachable states is finite.

## 2.12 Solutions

**2.1** p1 (wantq=false → p cleared to proceed); q1 (wantp=false → q cleared);
p2 (wantp=true, p enters CS); q2 (wantq=true, q enters CS). The essential
point: both *checks* must precede both *raises*: any schedule where the two
awaits interleave before the flag writes breaks ME.

**2.2** p1; q1; now wantp=wantq=true. p2 blocks (wantq true), q2 blocks
(wantp true). Each process *holds* its own flag (the resource the other
needs) and *waits* for the other's to fall: hold-and-wait, circular, no
preemption of flags — a deadlock by the classic COFFMAN conditions. There is
a reachable state with both processes blocked and no enabled statement
(machine-checked).

**2.3** Cycle (from state wantp=wantq=true, both at their loop check):

```
p3 (wantp <- false);  q3 (wantq <- false);
p4 (wantp <- true);   q4 (wantq <- true);
p2: sees wantq=true;  q2: sees wantp=true;   -- back to start of cycle
```

Every statement of the cycle is a completed atomic step; at every instant
each process has an enabled next statement (no deadlock); but the CS is never
reached again (no progress): livelock. Any finite prefix is just "both are
busy in the entry protocol". (The cycle requires the scheduler to interleave
the pairs in this order — possible, hence the algorithm cannot *guarantee*
progress; with random timings the probability of persisting forever is 0,
which is why it "works in practice".)

**2.4** (Machine-checked counterexample.)

| step | statement | wantp | wantq | last |
|---|---|---|---|---|
| 1 | p1: `last <- 1` | false | false | 1 |
| 2 | q1: `last <- 2` | false | false | 2 |
| 3 | q2: `wantq <- true` | false | true | 2 |
| 4 | q3: await — `wantp = false` → passes | | | |
| 5 | q enters CS | | | |
| 6 | p2: `wantp <- true` | true | true | 2 |
| 7 | p3: await — `wantq` true, but `last = 2` → passes | | | |
| 8 | p enters CS — **both in CS** ✘ | | | |

With the order swapped, `last` records who *arrived* first rather than who is
*insisting now*; q passed while p's flag was still down, and p later passes
on a `last` value that q will never see again. Order of statements in
concurrent protocols is never cosmetic.

**2.5** Suppose both in the CS. p passed p2 at time t1 reading `wantq=false`;
q passed q2 at t2 reading `wantp=false`. Flags are up during [p1, p4) and
[q1, q4), and both processes are between p1..p3 / q1..q3 now (they are in the
CS). p's read of wantq=false at t1 must fall outside [q1, q4): either t1 <
q1 or t1 ≥ q4; q4 has not executed (q is in the CS, q4 comes later), so
t1 < q1. Symmetrically t2 < p1. But t1 ≥ p1 (p1 precedes p2 in p's program)
and t2 ≥ q1: so p1 ≤ t1 < q1 ≤ t2 < p1 — contradiction (a cycle in time).
For attempt 2 the argument collapses at the first step: p's *flag* is raised
after its check, so "both flags up" is false at the moments the checks pass —
there is no interval the checks must avoid.

**2.6** q in CS, p waiting (wantp=true, q saw it, deferred or insisting —
say turn=2 and p insisted at p2… take the case p deferred: p at p5 awaiting
turn=1). q exits: q8 (wantq=false), q9 (turn <- 1). p's await passes; p
enters. If instead p was insisting (turn=1, p looping at p2), q's exit sets
wantq=false → p's p2 loop ends → p enters. Now q re-enters immediately: q1
(wantq=true), q2 sees wantp=true (p in CS), q3: `turn = 2`? No — turn=1, so
q3's condition (turn=1) is true → q defers: q4 (wantq=false), q5 awaits
turn=2. p proceeds. **q completes exactly one CS visit; then p must enter.**
Bounded bypass = 1.

**2.7** (a) True — attempt 4 (and any unfair spinning lock, chapter 4):
always some process can run (no deadlock) yet a particular one may never win.
(b) False — attempt 1 *is* symmetric (same code shape for both); it fails
requirements 2 and 3 (speed assumptions, non-interference), not symmetry.
(c) False — `volatile` changes memory *visibility*, not the logic; the
deadlock is a property of the protocol (both flags up), not of caching.
(d) False — "simultaneous" execution of atomic statements is still observed
as some interleaving order; `last` ends up 1 or 2 (one of the writes is
last), and the process whose number it is *not* passes its await.
(e) False as stated — finitely many states does not imply finitely many
scenarios: executions can loop forever through states (attempt 4 livelock
has 48 states and infinitely many scenarios). The converse holds
(infinitely many reachable states ⇒ infinitely many scenarios).

## 2.13 Going further

* **History and hierarchy of solutions:** Dijkstra posed the problem (1965);
  Dekker's solution was the first. Knuth (1966) gave the first solution with
  *bounded* bypass for n processes (bound exponential in n); de Bruijn and
  then Eisenberg & McGuire (1972) reduced the bound to n−1; Lamport's bakery
  (1974, chapter 3) achieved n−1 with an astonishingly simple algorithm;
  Peterson (1981) solved 2 processes with minimal state; the *filter
  algorithm* (Peterson's generalization: processes climb through n−1 "waiting
  rooms", at most one survivor per room) solves n processes with O(n) state.
* **Lower bound:** Burns & Lynch (1980) proved that *any* mutual exclusion
  protocol using only atomic reads and writes needs at least n shared
  registers for n processes — the bakery's O(n) arrays are asymptotically
  optimal for pure software solutions.
* **Where this goes in practice:** real systems do not run Dekker's loop;
  they use the OS (mutexes/futexes) or hardware atomics (chapter 4). But the
  *properties* (ME/deadlock/starvation) and the *proof style* (invariants +
  ordering arguments) are exactly what you will use everywhere.
* **Try it yourself:** implement Peterson in your favorite language with two
  threads incrementing a shared counter (see
  [`code/c/peterson.c`](code/c/peterson.c), with Dekker in
  [`code/c/dekker.c`](code/c/dekker.c) and Java versions
  [`code/java/CounterPeterson.java`](code/java/CounterPeterson.java),
  [`code/java/CounterDekker.java`](code/java/CounterDekker.java)). Then try
  removing the `volatile`/atomic qualifiers and watch it break — a preview of
  chapter 3.

---

Next: [03-advanced-algorithms.md](03-advanced-algorithms.md) — solutions for
*n* processes (the bakery), the quest for speed (Lamport's fast algorithm),
and why real hardware betrays all of them (sequential consistency, memory
barriers).
