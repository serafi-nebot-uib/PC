# 5 · Semaphores

> **What you will learn.** The classical synchronization primitive: what a
> semaphore is (value + queue), what its two atomic operations mean, how
> binary and counting semaphores relate, how to build a counting semaphore
> out of binary ones (Bartz's algorithm), and how to use semaphores to solve
> the problems that pure mutual exclusion cannot express: enforcing
> *execution order* (barriers, the merge pattern), the
> **producer–consumer** problem (infinite and finite buffers, split
> semaphores), the **dining philosophers** (deadlock and its prevention), and
> the **bear and the honey pot**.
>
> The algorithmic centerpiece — Bartz's simulation — is machine-checked:
> exhaustive exploration confirms that it admits at most *k* processes for
> initial value *k*, never lets the count go negative, and never strands
> waiting processes with capacity available (N = 2, 3; k = 0, 1, 2).

## 5.1 Introduction

The **semaphore** was introduced by Edsger W. Dijkstra in the mid-1960s,
inspired by railway signaling. It is *the* classical synchronization
primitive, and it changes the game in one fundamental respect:

* Everything in chapters 2–4 was implemented **on the machine, without the
  operating system's help** — and all of it waits by *busy waiting* (`await`
  = spinning loop), burning CPU.
* A **semaphore is implemented as a service of the operating system kernel**.
  When a process must wait, the kernel *deschedules* it — it becomes
  **blocked**, consuming no CPU — and the kernel wakes it when the wait is
  over. Synchronization **without busy waiting**.

This is only possible with the OS's collaboration: someone must keep the
list of waiting processes and wake them at the right moment. That someone
is the kernel (or, for user-level threading libraries, the runtime).

## 5.2 Process states

To talk about blocking we need the OS's vocabulary for what a process is
doing:

* **Ready** — the process *wants to run*: its next instruction could execute
  as soon as the scheduler gives it a CPU.
* **Running** — it is executing (on some CPU).
* **Blocked** — it is *not* ready and is *not a candidate* to run, no matter
  how many CPUs are idle. It is parked in some kernel queue, waiting for an
  event.
* **Unblocked** (also *awakened* or *released*) — the event arrived; the
  kernel moves the process back to *ready*.

Typical life: `ready → running → blocked → ready → …`. The scheduler moves
processes between ready and running (preemption, time slices — chapter 1);
*blocking* operations (I/O, semaphores) move them to blocked and the
corresponding *release* operations bring them back. A blocked process
yields its CPU entirely — this is what "no busy waiting" means concretely.

## 5.3 Definition

A semaphore `S` has two components:

* `S.V` — a **non-negative integer**, the *value* of the semaphore;
* `S.L` — a **set of processes**: those blocked by this semaphore.

It is initialized with a value `k ≥ 0` and an empty set:

```
semaphore S ⇽ (k, Ø)
```

You should read `S.V` as "how many pass-throughs remain" and `S.L` as "who
is queued waiting for one". The invariant `S.V ≥ 0` is what makes the
blocking behavior well-defined: value 0 with a non-empty `S.L` means
"everyone who wanted a pass is queued".

## 5.4 The two atomic operations

Exactly two operations, both **atomic** (executed indivisibly — in the
kernel, with interrupts disabled or a kernel lock). The original notation is
`P(S)` (Dutch *prolaag*, "try to decrease") for **wait** and `V(S)`
(*verhoog*, "increase") for **signal**; `p` denotes the process executing
the operation, and `q` is an arbitrary element of `S.L`:

```
wait(S):                      signal(S):
  if S.V > 0                    if S.L = ∅
    S.V ⇽ S.V - 1                 S.V ⇽ S.V + 1
  else                          else
    S.L ⇽ S.L ∪ {p}               take some q ∈ S.L; S.L ⇽ S.L - {q}
    p.state ⇽ blocked             q.state ⇽ ready
```

Read them as decisions:

* **wait**: *if* a pass is available (`S.V > 0`), take it (decrement) and
  keep running. *Otherwise* queue yourself in `S.L` and become blocked — the
  process simply stops; the kernel will not schedule it again until released.
* **signal**: *if* nobody is queued, remember the pass (`S.V + 1`). *If
  someone is queued*, hand the pass over directly: release one arbitrary
  queued process (which becomes ready, and will enter as soon as it runs).
  **The state of the signaling process does not change** — signal never
  blocks.

Two remarks connecting this to earlier chapters:

* `wait(S)` is the kernel implementation of the abstract statement
  `await S.V > 0 then S.V ⇽ S.V - 1` from chapter 1 — the general
  await-with-effect — *minus* the busy waiting.
* Note the asymmetry: the released process's decrement is "absorbed" into
  the signal (the value is *not* incremented when someone was queued). A
  released process proceeds *as if* it had consumed the pass; `S.V` never
  rises above what was signaled. This is what makes semaphores *fair queues*
  rather than counters with races.

> **Naming soup.** `wait` / `P` / `down` / `acquire` / `lock`; `signal` /
> `V` / `up` / `release` / `post`. Alas, the word `wait` will collide with
> monitors' `waitC` in chapter 6 — context distinguishes them.

## 5.5 The general (counting) semaphore: a multiplex

The semaphore's value counts **available resources** (Java calls them
*permits*):

* The initial value `k` = how many `wait`s can succeed *without blocking*.
* `S.V` at any time = remaining passes; `|S.L|` = processes queued for one.

Used with `k = N` for `N` interchangeable resources (N connections, N
threads in a pool, N slots of a multiplex), it is a **counter**: at most `N`
processes can be "inside" simultaneously.

The **mutual exclusion** use is the special case `k = 1`: at most one
process in the critical section.

## 5.6 The binary semaphore (mutex)

A semaphore whose value is restricted to **0 or 1** — initialized `(1, Ø)`
(free) or `(0, Ø)` (already taken). `wait` is unchanged; `signal` is
redefined because a pass is never *stored* — it is *handed to a waiter* or,
if nobody waits, the value returns to 1:

```
signal(S):
  if S.V = 1          -- protocol error: signaling a semaphore nobody holds
    (undefined)
  else if S.L = ∅
    S.V ⇽ 1
  else                -- as before
    take q ∈ S.L; S.L ⇽ S.L - {q}; q.state ⇽ ready
```

The `S.V = 1` case is deliberately **undefined**: calling `signal` on a
binary semaphore that is not held means the protocol is broken (two signals
without a wait), and different implementations respond differently (error,
exception, or silent corruption). The term **mutex** (mutual exclusion) is
what `pthreads` and `java.util.concurrent` use for this object.

## 5.7 Bartz's algorithm: a counting semaphore from binary ones

Can a general semaphore be built from binary semaphores alone? Yes — with
two binary semaphores and an integer (Bartz's algorithm):

* `mutex` — a binary semaphore ensuring mutual exclusion on the access to
  `count`;
* `gate` — a binary semaphore used to block or release processes according
  to `count`;
* `count` — the remaining passes.

```
shared: binary semaphore mutex = 1
        binary semaphore gate = 1 if k > 0 else 0
        integer count = k

wait(S):                        signal(S):
  wait(gate)                      wait(mutex)
  wait(mutex)                     count ⇽ count + 1
  count ⇽ count - 1               if count = 1
  if count > 0                        signal(gate)
    signal(mutex)                 signal(mutex)
    signal(gate)
  else
    signal(mutex)
```

The logic, step by step:

* To `wait`: first pass the **gate** (more below), then take the pass:
  `count` decreases under `mutex`'s protection. If passes remain
  (`count > 0`), reopen the gate for the next in line; if you consumed the
  **last** pass (`count = 0`), *leave the gate closed* — every subsequent
  `wait` blocks on `gate`, which is exactly the queue `S.L` of the
  simulated semaphore. (If you are not the first to enter, you must wait.)
* To `signal`: increment `count`. If it went from 0 to 1 — a pass appeared
  where there was none — **open the gate**, releasing one blocked waiter,
  which will consume the new pass (decrementing `count` back to 0 and
  closing the gate behind it). If waiters were already flowing through,
  do nothing special.

So the invariant that makes it work: *the gate is open (1) exactly when
`count > 0`* — it is initially `1` iff `k > 0`, and every transition of
`count` across 0 is paired with a gate operation. The gate holds precisely
the difference between "passes handed out" and "passes consumed".

> **Machine-checked.** Exhaustive interleaving exploration (a
> semaphore-aware model checker, modeling each binary semaphore as a value
> plus a queue of blocked processes) confirms for N = 2, 3 processes and
> k = 0, 1, 2: (a) at most `k` processes are inside the section protected
> by the simulated semaphore (mutual exclusion with the right multiplex);
> (b) `count ≥ 0` in every reachable state; (c) no reachable state has all
> processes blocked while `count > 0` (no lost capacity — nobody is stuck
> when a pass exists).

(In practice you will never implement this yourself — the kernel does it —
but it is the cleanest illustration of what "blocking" buys you, and of how
one synchronization primitive can be built from a weaker one.)

## 5.8 Mutual exclusion with a semaphore

The critical section solution is now two lines. Initialize the semaphore to
`(1, Ø)`; the **pre-protocol** is `wait(S)` and the **post-protocol** is
`signal(S)`:

```
semaphore S ⇽ (1, Ø)

      p                        q
while true do              while true do
  NSC;                       NSC;
  wait(S);                   wait(S);
  CS;                        CS;
  signal(S)                  signal(S)
od                         od
```

Walk-through: p executes `wait(S)` with `S = (1, Ø)` → decrements, enters
the CS; the semaphore is now `(0, Ø)`. If q executes `wait(S)` before p
leaves, it finds `S.V = 0` and becomes blocked: the state is `(0, {q})`.
When p exits and executes `signal(S)`, the set is non-empty, so q is
released (ready) and enters the CS *without* the value ever having
increased.

Properties (compare chapter 2's checklist):

* **Mutual exclusion**: no reachable state has both processes past their
  waits — e.g. a state where both are at their `signal(S)` statements is
  unreachable, since that would require two successful decrements of a
  semaphore whose value never exceeded 1.
* **No deadlock**: there is no state with both processes blocked — a
  semaphore of value 1 can block at most one of the two.
* **No starvation**: when a process blocks on `wait`, the *only* thing that
  can release it is a `signal` — and the process inside the CS executes one
  on exit. A waiter enters after at most one CS visit of the holder
  (with a *strong* semaphore, §5.14).

Note what we got *for free*: no protocol design, no proofs about flags —
the kernel guarantees the atomicity and the blocking. The intellectual work
moves from *building* the mechanism to *using* it correctly.

## 5.9 Implementations

* **C, POSIX** (`<semaphore.h>`): `sem_init` / `sem_wait` / `sem_post` /
  `sem_destroy` for unnamed semaphores. Caveat (the slides note it):
  `sem_init` is **deprecated/unsupported on macOS** — use named semaphores
  (`sem_open`) or Apple's **GCD** `dispatch_semaphore_t`
  (`dispatch_semaphore_create` / `_wait` / `_signal`).
* **C, pthread mutex**: for pure mutual exclusion, `pthread_mutex_lock` /
  `pthread_mutex_unlock` — a mutex is the OS's binary semaphore with
  extras (ownership, error checking; see Going further).
* **Java**: [`java.util.concurrent.Semaphore`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Semaphore.html)
  — `new Semaphore(k)`; `acquire()` / `release()`.
* **Python**: `threading.Semaphore(k)` — `acquire()` / `release()`.
* **Go**: no semaphore in the standard library; the idiomatic form is a
  buffered channel of capacity k (§5.16's code), or the weighted
  [`golang.org/x/sync/semaphore`](https://pkg.go.dev/golang.org/x/sync/semaphore).

Code: [`code/c/mutex.c`](code/c/mutex.c) (pthread mutex),
[`code/c/semaphore_posix.c`](code/c/semaphore_posix.c) (POSIX sem, Linux),
[`code/c/semaphore_gcd.c`](code/c/semaphore_gcd.c) (GCD, Apple platforms),
[`code/java/CounterSemaphore.java`](code/java/CounterSemaphore.java),
[`code/python/countersemaphore.py`](code/python/countersemaphore.py),
[`code/go/countersemaphore.go`](code/go/countersemaphore.go).

## 5.10 Order-of-execution problems

Mutual exclusion abstracts processes **competing** for a resource. A second
family of synchronization problems is about **cooperation**: enforcing that
operations of different processes happen in a certain *order*.

The minimal example is the **merge (join)** pattern — process q must not
continue until process p has finished some phase:

```
semaphore S1 ⇽ (0, Ø)      -- initialized to 0: merge is blocked at S1

p: ...work...;  signal(S1)
q: wait(S1);  ...continuation...
```

A semaphore initialized to **0** is a *one-shot gate*: the first `wait`
always blocks, and only a `signal` can open it. Whatever p did before its
`signal` is guaranteed to have happened before whatever q does after its
`wait`. This "signal-then-wait" idiom is the building block of the rest of
the chapter.

## 5.11 Barriers

A **barrier** forces a set of concurrent processes to *all reach a given
point* before any of them continues past it. A barrier is thus a set of
synchronization points. Two-process version (a *rendezvous*):

```
semaphore aArrived ⇽ (0, Ø), bArrived ⇽ (0, Ø)

a: ...; signal(aArrived); wait(bArrived); ...    -- neither continues
b: ...; signal(bArrived); wait(aArrived); ...    -- until both arrived
```

In the **mergesort** example from the course, each recursive half ends by
signaling its own semaphore and waiting on the other's: the value of each
semaphore indicates whether that process has *arrived at the goal*, and the
merge phase starts only when both have.

A reusable barrier for *n* processes needs care: simply resetting counters
lets fast processes lap slow ones between phases. The standard solution is
**two turnstiles** (alternating phases):

```
shared: semaphore mutex = 1, turnstile1 = 0, turnstile2 = 1
        integer count = 0

barrier():                    -- executed by each of the n processes
  wait(mutex)
  count ⇽ count + 1
  if count = n:               -- last to arrive:
      wait(turnstile2)        --   make sure the previous phase fully closed
      signal(turnstile1)      --   open this phase's turnstile
  signal(mutex)
  wait(turnstile1)            -- pass through (n-1 signals cascade from the last)
  wait(mutex)
  count ⇽ count - 1
  if count = 0:               -- last to leave:
      wait(turnstile1)        --   close turnstile1 for the next phase
      signal(turnstile2)      --   re-arm turnstile2
  signal(mutex)
```

Phase 1 (`count` climbing) opens `turnstile1` exactly once for `n`
passages; phase 2 (`count` falling) closes it again and re-arms
`turnstile2` for the next use. See Going further for a full development.

## 5.12 The producer–consumer problem

> **Producers** produce elements and send them to **consumers**, which
> receive and consume them. This shape is everywhere: pipelines between
> processes and shell commands, device I/O, network communication…

Two flavors:

* **Synchronous**: when a producer generates an element it must be consumed
  before the producer continues (a rendezvous, §5.11).
* **Asynchronous**: the communication channel has storage capacity — a
  **buffer**. Producers append to the queue; consumers take from it.

The two synchronization constraints with a buffer:

1. a consumer **cannot take from an empty buffer**;
2. a producer **cannot add to a full buffer**.

**Infinite buffer.** If the buffer never fills (reasonable given the
relative speeds of producers and consumers), only constraint 1 remains. A
counting semaphore does the counting for us — its value is *exactly* the
number of buffered items:

```
semaphore notEmpty ⇽ (0, Ø)

producer:                       consumer:
while true do                   while true do
  produce item                    wait(notEmpty)
  deposit item in buffer          take item from buffer
  signal(notEmpty)                consume item
od                              od
```

The slides make one delicate assumption: the pairs *deposit +
`signal(notEmpty)`* (p2, p3) and *`wait(notEmpty)` + take* (q1, q2) execute
atomically. Without it, a consumer woken by `signal` could race another
consumer for an item that is not yet deposited, or two consumers could
empty the semaphore's count without items to match. The honest fix needs
one more semaphore — mutual exclusion on the buffer:

```
semaphore notEmpty ⇽ (0, Ø), mutex ⇽ (1, Ø)

producer:                       consumer:
  produce item                    wait(notEmpty)
  wait(mutex)                     wait(mutex)
  deposit item                    take item
  signal(mutex)                   signal(mutex)
  signal(notEmpty)                consume item
```

Code: [`code/python/producerconsumerinfinite.py`](code/python/producerconsumerinfinite.py).

## 5.13 Finite buffers: split semaphores

With a finite buffer of `N` slots, constraint 2 returns. Add a counting
semaphore **notFull** counting the *free* slots, initialized to `N`:

```
semaphore mutex ⇽ (1, Ø), notEmpty ⇽ (0, Ø), notFull ⇽ (N, Ø)

producer:                       consumer:
while true do                   while true do
  produce item                    wait(notEmpty)
  wait(notFull)                   wait(mutex)
  wait(mutex)                     take item
  deposit item                    signal(mutex)
  signal(mutex)                   consume item
  signal(notEmpty)
od                              od
```

Note the two counting semaphores are complementary: every slot is either
occupied or free, so at all times

```
notEmpty + notFull = N        -- (plus any process temporarily "between")
```

Such semaphores are called **split semaphores**: two or more semaphores
satisfying an invariant that their values sum to a fixed number `N`. It is
not a new *type* of semaphore — it is a term describing this mechanism (a
distributed count over the states of a shared structure).

> **Order matters.** Each process waits on its *counting* semaphore
> **before** the `mutex`. Swapping the order (e.g. consumer taking `mutex`
> then `notEmpty`) deadlocks: with an empty buffer the consumer enters,
> holds `mutex`, and blocks on `notEmpty`; the producer then blocks on
> `mutex` and can never `signal(notEmpty)`. Rule of thumb: *acquire
> resource-availability first, mutual exclusion last.*

Code: [`code/python/producerconsumer.py`](code/python/producerconsumer.py).

## 5.14 Strong and weak semaphores

Which queued process does `signal` release? The definition says *an
arbitrary* `q ∈ S.L` — but implementations differ:

* **Strong semaphore**: the blocked set is a **FIFO queue**; wakeups happen
  in arrival order. This guarantees **bounded waiting** and fairness: a
  waiting process enters before any process that arrived after it. Java:
  `new Semaphore(1, true)` (the boolean is "fair").
* **Weak semaphore**: no guaranteed release order (POSIX semaphores, most
  kernels). Correct *usage* must not depend on the order — a solution that
  only works if wakeups are FIFO is not a solution. (Starvation of an
  unlucky process is theoretically possible; bounded waiting must come from
  the algorithm, not the semaphore.)

The same distinction secretly haunted chapters 2–4: Dekker and Peterson are
starvation-free by *design*; a TAS spinlock is starvation-prone; the ticket
lock *builds* a FIFO — i.e., it implements a strong semaphore's queue by
hand.

## 5.15 The dining philosophers

> Five philosophers sit at a round table. They do only two things: think and
> eat. On the table: five plates and five forks (bastonets), and a central
> bowl of spaghetti that never empties. To eat, a philosopher needs **both**
> forks adjacent to their plate — and each fork can be picked up **one at a
> time**.

The problem is a showcase: the *same* problem is used to study and compare
every synchronization mechanism (semaphores here, monitors in chapter 6).
The requirements for any solution:

* a philosopher eats **only with two forks**;
* **mutual exclusion**: two philosophers never hold the same fork;
* **progress**: no deadlock;
* **bounded waiting**: no starvation;
* **efficiency**: absent competition, a fork must be usable (by a neighbor).

### 5.15.1 The naive solution — deadlock

Number philosophers and forks `0..4`; philosopher `i` has `fork[i]` on the
left and `fork[(i+1) mod 5]` on the right (so `phil4` has `fork4` left and
`fork0` right). Model each fork as a semaphore `(1, Ø)`: `wait` = take,
`signal` = put down.

```
philosopher i:
while true do
  think;
  wait(fork[i]);              -- take left
  wait(fork[(i+1) mod 5]);    -- take right
  eat;
  signal(fork[(i+1) mod 5]);
  signal(fork[i])
od
```

Mutual exclusion per fork is perfect — but suppose **all five** become
hungry simultaneously: each grabs the left fork and blocks waiting for the
right — which is the neighbor's left. All five blocked, each holding one
fork: **deadlock**.

The **Coffman conditions** — the four *necessary* conditions for deadlock
(any one of them failing prevents it):

1. **Mutual exclusion** — a resource can be assigned to at most one process.
2. **Hold and wait** — a process holds resources while waiting for more.
3. **No preemption** — a resource cannot be forcibly taken; the holder must
   release it voluntarily.
4. **Circular wait** — a closed cycle of processes, each waiting for a
   resource held by the next (derivable from 2).

Here all four hold: forks are exclusive; a philosopher holds the left while
waiting for the right; nobody can snatch a fork; and the round table *is*
the circle. **To prevent deadlock, break any one condition** — and the
cheapest to break is the circular wait.

### 5.15.2 The LR solution: order the requests

Make **all philosophers request the two forks in the same global order**
(ascending fork index). For philosophers 0–3 nothing changes (left `i`, then
right `i+1` — already ascending); `phil4` must take them in the *other*
order: `fork0` first, then `fork4`:

```
philosopher i (0 ≤ i ≤ 3):          philosopher 4:
  wait(fork[i])                       wait(fork[0])
  wait(fork[(i+1) mod 5])             wait(fork[4])
  eat;  release both                  eat;  release both
```

Now the circular wait is impossible: `phil4` competes for `fork0` *as a
left-neighbor would*, so every fork-request pair is ordered and no cycle of
"holding A, waiting for B" can close. Code:
[`code/python/philosopherslr.py`](code/python/philosopherslr.py).

LR is deadlock-free but not optimal: bad timings can serialize eating. The
slides' scenario: `phil4` blocks after taking `fork0`; `phil3` takes
`fork4` and eats — and for a while it may be the *only* one eating, because
`phil4` holds the fork `phil0` needs while itself waiting on `phil3`. No
deadlock, but concurrency is lost in exactly the situations the efficiency
requirement cares about.

### 5.15.3 Tanenbaum's solution: synchronize on the state

Reformulate as an order-of-execution problem: before eating, a philosopher
must **check that no neighbor is eating**; if they are, wait to be
*notified* when they put their forks down. This needs:

* `status[i]` ∈ {`THINKING`, `HUNGRY`, `EATING`} — the philosophers' states;
* `sync[i]` — one semaphore per philosopher (initialized `(0, Ø)`), for the
  "you may eat now" notifications;
* `mutex` — mutual exclusion whenever `status` is checked or modified.

```
semaphore mutex ⇽ (1, Ø)
semaphore sync[5] ⇽ all (0, Ø)
status[5] ⇽ all THINKING

take_forks(i):                    put_forks(i):
  wait(mutex)                       wait(mutex)
  status[i] ⇽ HUNGRY                status[i] ⇽ THINKING
  can_eat(i)                        can_eat((i-1) mod 5)
  signal(mutex)                     can_eat((i+1) mod 5)
  wait(sync[i])                     signal(mutex)
  -- eats here

can_eat(i):     -- called only while holding mutex
  if status[i] = HUNGRY
     and status[(i-1) mod 5] ≠ EATING
     and status[(i+1) mod 5] ≠ EATING:
    status[i] ⇽ EATING
    signal(sync[i])
```

How it plays out: `take_forks` marks the philosopher `HUNGRY` and calls
`can_eat`: if neither neighbor is eating, the status becomes `EATING` and
the semaphore `sync[i]` is signaled — so the subsequent `wait(sync[i])`
does not block. If a neighbor *is* eating, the philosopher blocks on
`sync[i]`. When a philosopher finishes (`put_forks`), it checks **both
neighbors**: any `HUNGRY` neighbor whose other side is also clear is
released via its `sync` semaphore. Every `can_eat` call happens inside the
critical section of `mutex`, so status checks and updates are consistent.

Why it works: two adjacent philosophers can never be `EATING` at once
(the check forbids it) — mutual exclusion per fork follows; no philosopher
ever holds one fork while blocked — it waits *before* taking any fork
(breaking hold-and-wait, hence no deadlock); and a hungry philosopher
enters as soon as both neighbors have finished their current meal, so the
wait is bounded by the neighbors' meals — no starvation. Code:
[`code/python/philosophers.py`](code/python/philosophers.py).

## 5.16 The bear, the honey pot and the bees (Andrews 2000)

> There is a bear that eats honey from a pot and then sleeps. `N` bees each
> carry one portion of honey to the pot, which holds `H` portions. The bees
> keep filling the pot; when it is **full, the last bee wakes the bear**.
> The bear eats the whole pot — while doing so the bees do not disturb it —
> and goes back to sleep; then the bees start again.

Three synchronization facts: bees must not overfill (the pot holds `H`);
the bear sleeps until the pot is full; and between "full" and "eaten" the
bees must be held back. One mutex plus one gate:

```
integer portions = 0
semaphore mutex ⇽ (1, Ø)        -- protects portions
semaphore potFull ⇽ (0, Ø)      -- the bear sleeps here
semaphore potEmpty ⇽ (1, Ø)     -- gate: bees enter the pot area one at a time

bee:                              bear:
while true do                     while true do
  wait(potEmpty)                    wait(potFull)
  wait(mutex)                       eat all H portions
  portions ⇽ portions + 1           wait(mutex)
  if portions = H:                  portions ⇽ 0
    signal(potFull)   -- wake bear  signal(mutex)
  else                              signal(potEmpty)   -- reopen the gate
    signal(potEmpty)
  signal(mutex)
od                                od
```

`potEmpty` is the bees' gate: each bee passes it (closing it behind), fills
one portion under `mutex`, and *reopens the gate* — unless it deposited the
**last** portion: that bee signals `potFull` (waking the bear) and **keeps
the gate closed**, so the remaining bees block and the bear eats undisturbed.
When the bear finishes it resets the pot and reopens the gate, and filling
resumes. Code: [`code/python/bearhoneypot.py`](code/python/bearhoneypot.py).

## 5.17 Summary

* A semaphore `S ⇽ (k, Ø)` = a non-negative value `S.V` plus a set `S.L` of
  blocked processes; `wait`/`signal` are atomic kernel operations; blocking
  replaces busy waiting via the OS (blocked processes consume no CPU).
* Counting semaphore = resource counter (multiplex); value-1 = mutex;
  binary semaphore's `signal` on a free semaphore is a protocol error.
* Bartz: a counting semaphore from two binary semaphores + `count` (the
  gate is open iff `count > 0`) — machine-checked.
* A 0-initialized semaphore is a one-shot gate: *execution-order*
  synchronization (merge, barriers, rendezvous).
* Producer–consumer: `notEmpty` counts items (infinite buffer); add
  `notFull` counting free slots for a finite buffer; `notEmpty + notFull =
  N` (split semaphores); mutex last, availability first.
* Strong (FIFO) vs weak (arbitrary wakeup) semaphores: fairness comes from
  the queue or from the algorithm.
* Dining philosophers: naive solution deadlocks (Coffman: ME, hold-and-
  wait, no preemption, circular wait); break the cycle by ordering requests
  (LR) or synchronize on philosopher *states* (Tanenbaum).
* Bear & pot: one gate + one mutex encode "wake the bear when full, hold
  the bees while it eats".

## 5.18 Exercises

**5.1** Explain precisely *why* the binary semaphore's `signal` leaves the
`S.V = 1` case undefined, and what a real implementation could reasonably do
there.

**5.2** Trace Bartz's algorithm with `k = 1` and two processes p, q: give
the interleaving in which both are eventually inside simultaneously…
…or show that it is impossible, and give the state in which q waits.

**5.3** In the finite-buffer producer–consumer (§5.13), swap the consumer's
first two waits (take `mutex` *before* `notEmpty`). Exhibit the interleaving
that deadlocks the system with an empty buffer.

**5.4** A classmate proposes initializing `notEmpty` to `N` and deleting
`notFull`, "because the producer can just check `notEmpty`". What breaks —
consider a full buffer?

**5.5** In Tanenbaum's solution, show (a) why two adjacent philosophers can
never eat simultaneously even though `can_eat` only *reads* neighbors'
statuses; (b) that a hungry philosopher's wait is bounded, despite a
neighbor eating indefinitely many times *in principle*.

**5.6** The bear's `signal(potEmpty)` comes *after* eating. What goes wrong
if it is moved *before* the eating (right after resetting `portions`)?
State which requirement of the story is violated.

**5.7** True or false, with one line of justification:
(a) A weak semaphore can lose updates if two processes `signal`
    simultaneously.
(b) A binary semaphore with initial value 0 used as a merge-gate
    (§5.10) can deadlock if the producer's `signal` comes before the
    consumer's `wait`.
(c) With split semaphores, `notEmpty` can momentarily exceed `N/2` while
    `notFull` is 0.
(d) In Bartz's algorithm, `gate` can be initialized to 1 regardless of `k`.

## 5.19 Solutions

**5.1** A binary semaphore's pass is *never stored* — it is handed to a
waiter or returned to the value. `signal` with `S.V = 1` means "release a
resource nobody holds": some path executed two `signal`s per `wait`, i.e.
the mutual-exclusion protocol is broken and continuing would corrupt the
invariant (two processes could later pass `wait`). A reasonable
implementation: report an error / raise (Java's `Semaphore.release()`
with a "max permits" would throw `IllegalStateException`), or count it as a
detectable protocol violation.

**5.2** Impossible. With `k = 1`: p passes the gate (gate 1→0), decrements
`count` 1→0, and since `count = 0` leaves the gate **closed**; q blocks on
`gate`. The steady state is `count = 0, gate = 0, p inside, q blocked on
gate`. Only p's `signal(S)` (increment 0→1, `count = 1` → open gate) lets
q in — after p is done. Mutual exclusion is exactly the gate's closed-open
alternation.

**5.3** Buffer empty, `notEmpty = 0`, `mutex = 1`:

| step | action | mutex | notEmpty |
|---|---|---|---|
| 1 | consumer: `wait(mutex)` | 0 | 0 |
| 2 | consumer: `wait(notEmpty)` — blocks | 0 | 0 |
| 3 | producer: `wait(mutex)` — **blocks** | 0 | 0 |

The consumer holds `mutex` forever while waiting for an item; the producer
cannot produce because it cannot take `mutex`. Classic hold-and-wait.

**5.4** With a full buffer, `notEmpty = N ≠ 0`, so the producer would pass
"checking `notEmpty`" and deposit into a **full** buffer — overwriting items
(something must also *bound* deposits). `notEmpty` counts *items*; nothing
counts *free slots*. Only `notFull` (initialized to `N`) encodes the
information "how many more deposits fit".

**5.5** (a) `can_eat(i)` sets `status[i] ⇽ EATING` only under `mutex`; a
neighbor can only enter `EATING` through its own `can_eat` — also under
`mutex`. Two adjacent philosophers cannot both satisfy "both neighbors not
EATING" *at the same instant*: if i is EATING, its neighbors' checks (when
they next run, under `mutex`) read `EATING` and fail. The serialization by
`mutex` makes the checks sequential, hence consistent. (b) Suppose i is
`HUNGRY` and blocked. Each neighbor may currently be `EATING`, but *its
next* `put_forks` runs `can_eat(i)`; i eats as soon as both neighbors are
simultaneously not `EATING` — which happens no later than the second of
their current meals ending. So i's wait is bounded by two meal durations,
regardless of how many meals happen "in principle".

**5.6** The gate would reopen while the bear is still eating: blocked bees
wake, pass `wait(potEmpty)`, block only on `mutex` (held… by no one — the
bear released it before eating), and start depositing into the pot *while
the bear eats* — violating "while it eats, the bees do not disturb it" (and
racing the bear's implicit consumption of the portions). The gate must stay
closed from *full* until *eaten*.

**5.7** (a) False — `signal` is atomic; two concurrent signals serialize
(e.g. both increment, or both release waiters); nothing is lost.
(b) False — with initial value 0, if the producer's `signal` comes first
the value becomes 1 and the consumer's `wait` passes without blocking: the
merge still enforces "p's work before q's continuation". Ordering with a
one-shot gate can only *delay*, never deadlock (deadlock needs a circular
wait; here the wait has a single, guaranteed releaser). (c) True — `notEmpty + notFull = N` only
bounds the *sum*: e.g. `N = 8`, 6 items buffered → `notEmpty = 6 > N/2`
while… `notFull = 2 ≠ 0`. For the *conjunction* "notEmpty > N/2 ∧ notFull
= 0" — impossible, since the sum would exceed N. (d) False — with `k = 0`
and `gate = 1`, the first `wait` passes the gate, decrements `count` to
`-1`, and closes the gate holding a *negative* count: the invariant
"gate open iff count > 0" is broken and an extra pass has been minted.

## 5.20 Going further

* **The Little Book of Semaphores** (A. B. Downey, free PDF) — builds up
  barriers, turnstiles, producers-consumers, readers-writers and "passing
  the baton" patterns purely with semaphores; the two-turnstile barrier of
  §5.11 is developed there step by step.
* **Dijkstra's originals**: "Cooperating Sequential Processes" (1965) and
  the THE multiprogramming system (1968) — where P and V were born.
* **Semaphore vs mutex in practice**: ownership (only the locker may
  unlock), recursion, priority inheritance — why kernels distinguish them;
  the slides' links: pthread mutex man page and the classic
  *pthreads mutex vs semaphore* StackOverflow discussion.
* **Platform notes**: POSIX `sem_init` deprecation on macOS and the GCD
  `dispatch_semaphore` alternative (links in §5.9); Go's
  `golang.org/x/sync/semaphore` for *weighted* (non-unit) permits.
* **Strong vs weak** and fairness guarantees in `java.util.concurrent`
  (the `fair` flag), and why kernels default to weak (cheaper, and correct
  programs must not care).
* **Next**: chapter 6 wraps data + operations + synchronization into one
  structured construct — the monitor — and tames exactly the "who forgot a
  wait/signal" fragility of this chapter.

---

Next: [06-monitors.md](06-monitors.md) — synchronization as a language
construct: mutual exclusion and condition queues built into the object
itself.
