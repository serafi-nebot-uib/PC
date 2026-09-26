# 4 · Hardware solutions: atomic read-modify-write

> **What you will learn.** Why the software algorithms of chapters 2–3 are
> not how anyone actually does it; what the hardware offers instead — atomic
> **read-modify-write** instructions (`test&set`, `get&set`/exchange,
> `get&add`, `swap`, `compare&swap`) — and how each yields a working lock in
> a few lines; how to classify shared registers (safe/regular/atomic); the
> strengths and weaknesses of spinlocks; and the final twist: for our
> shared counter, no lock is needed at all.

## 4.1 Why software solutions are not enough

The pure-software mutual exclusion algorithms suffer from three problems:

* **Size of shared state and cache coherence.** They use `O(n)` shared
  variables (the bakery's two arrays, the fast algorithm's `want[]`), and
  they *spin* on them: every waiting process continuously re-reads shared
  memory, generating a flood of cache-coherence traffic that slows every
  core in the machine — even the one making progress.
* **Unpredictable interleaving.** Their correctness arguments are delicate
>  orderings of ordinary reads and writes — and as chapter 3 showed, real
>  compilers and processors do not honor the sequential consistency these
>  proofs assume. Making them work on real hardware requires barriers or
>  atomics anyway.
* **Busy waiting.** All of them wait by spinning (`await` = `while not B do
> skip`), burning CPU.

The classic "obvious" fix — **disable interrupts** around the critical
section, so the scheduler cannot preempt — is a non-starter:

* It is **not available to user processes** (interrupt control is a
  privileged operation, for the kernel only — imagine any program freezing
  your keyboard and timer).
* On a **multiprocessor**, disabling interrupts on *one* CPU does not stop
  the other CPUs from interleaving; you would have to disable interrupts on
  **all** processors simultaneously — an expensive, global operation that
  also ruins interrupt latency for everything else.

The real solution: the processor provides special instructions that are
atomic *by construction*.

## 4.2 Hardware synchronization instructions

Modern architectures offer instructions that **read a memory location and
modify it in one indivisible hardware step**: they execute without being
interrupted (from other cores' point of view), maintain cache coherence
(the cache line is obtained exclusively), and act as memory barriers
(chapter 3). All the synchronization machinery of operating systems and
language runtimes is built on a handful of such primitives.

### 4.2.1 Shared registers, classified

To talk precisely about these instructions it helps to view a shared
variable as an **object** with read and write methods, and to classify how
strong its guarantees are (Lamport's taxonomy, 1986). Suppose a register
holding 3 is overwritten with 5:

| Register type | A read that does not overlap the write | A read that overlaps the write |
|---|---|---|
| **safe** | returns the last written value (5) | may return **any value at all** (garbage, even never-written bit patterns) |
| **regular** | returns 5 | returns only the **old value (3) or the new value (5)** — never garbage |
| **atomic** | returns 5 | as regular, *and* the reads/writes of all processes appear to occur in some single total order (once a read sees 5, no later read sees 3) |

Atomic registers are what we have implicitly assumed all along ("a read
returns the value of the last write in the interleaving"). Safe registers
are the honest description of a plain memory cell during a concurrent
write. Remarkably (chapter 3's Going further), Lamport's bakery works even
with *safe* `number` registers.

### 4.2.2 The read-modify-write (RMW) primitives

An RMW instruction is an **atomic** operation on a register: it reads the
value, computes a new one, and writes it back — nobody can observe or
intervene in between. The classic five, expressed as functions (this is the
course's notation; think of `r` as a shared memory cell):

```
get&set(r, v):      old <- r; r <- v; return old       -- aka exchange

get&add(r, v):      old <- r; r <- old + v; return old

test&set(r):        old <- r; if r = 0 then r <- 1; return old
                    -- "acquire" succeeds iff it returns 0

swap(r1, r2):       tmp <- r1; r1 <- r2; r2 <- tmp      -- atomic exchange
                    -- of two registers (in practice: a register and memory)

compare&swap(r,e,n):old <- r; if r = e then r <- n; return old
                    -- "succeeds" iff it returns e
```

All appear in real instruction sets under various names: `xchg`, `bts`
(x86), `lock add`, `cmpxchg`, LL/SC pairs (`ldrex/strex`, `lwarx/stwcx` on
ARM/Power — a different but equivalent mechanism). There is **no single
universal instruction**: each architecture provides its own subset, and
programming languages historically had no standard API either (C11/C++11
`stdatomic` and Java's `java.util.concurrent.atomic` are the modern,
portable covers).

## 4.3 Mutual exclusion with RMW instructions

Throughout: `CS` is the critical section, `mutex` is a shared integer, 0 =
free, 1 = held. All the protocols below work for **any number of processes**
and any number of processors.

### 4.3.1 `get&set` (exchange) spinlock

```
shared: integer mutex = 0

entry: while get&set(mutex, 1) = 1 do skip;    -- spin until we see it 0
       CS;
exit:  mutex <- 0
```

If `mutex` was 0, `get&set` returns 0 (we acquired, leaving a 1 behind);
every later competitor gets 1 returned and spins. Simple, correct — the
textbook spinlock.

* C: `__atomic_exchange_n(&mutex, 1, __ATOMIC_SEQ_CST)` —
  [`code/c/counter_get_and_set.c`](code/c/counter_get_and_set.c)
* Java: `AtomicReference.getAndSet(...)` (as in the slides) or, more
  idiomatically, `AtomicBoolean.getAndSet(true)` —
  [`code/java/CounterGetAndSet.java`](code/java/CounterGetAndSet.java)

### 4.3.2 `test&set` spinlock

`test&set` is the specialized ancestor of the above: single operand, sets
to 1, returns 0 exactly when the lock was free:

```
entry: while test&set(mutex) ≠ 0 do skip;
       CS;
exit:  mutex <- 0
```

* C: `__atomic_test_and_set(&mutex, __ATOMIC_SEQ_CST)` —
  [`code/c/counter_test_and_set.c`](code/c/counter_test_and_set.c)
* Java: `AtomicBoolean.compareAndSet(false, true)` is the standard
  equivalent (there is no direct TAS in the JDK).

### 4.3.3 `swap` spinlock

`swap` atomically exchanges a **local** variable with the shared one; the
acquisition test happens on the local copy afterwards — "the difference
from TAS is that the previous value of `mutex` is checked in the local
variable used for the exchange":

```
local: integer loc
entry: do
         loc <- 1;
         swap(mutex, loc);            -- mutex gets 1; loc gets the old mutex
       while loc = 1;                 -- old value 1 => we did not acquire
       CS;
exit:  mutex <- 0
```

* C: `__atomic_exchange` — [`code/c/counter_swap.c`](code/c/counter_swap.c)
* Go: `atomic.SwapInt32(&mutex, 1)` —
  [`code/go/gocounter_swap.go`](code/go/gocounter_swap.go)

### 4.3.4 `get&add`: the ticket lock

Here the RMW instruction rebuilds the *bakery* — with one decisive
difference: taking a number (`max+1`) is replaced by an **atomic
increment**, so numbers are unique and strictly increasing, and no arrays
or `choosing` flags are needed. This is the fair, FIFO lock:

```
shared: integer number = 0, turn = 0

local:  integer myticket
entry: myticket <- get&add(number, 1);    -- take next number (atomic!)
       await turn = myticket;             -- spin until my number is called
       CS;
exit:  get&add(turn, 1)                  -- call the next number
```

* Every process gets a distinct ticket; entry happens **in ticket order**:
  first-come-first-served, **no starvation** (in stark contrast to the
  previous three locks — see §4.4).
* C: `__atomic_fetch_add` —
  [`code/c/counter_get_and_add.c`](code/c/counter_get_and_add.c)
* Go: `atomic.AddUint32` —
  [`code/go/gocounter_get_and_add.go`](code/go/gocounter_get_and_add.go)
* Java: `AtomicInteger.getAndAdd` /
  `getAndIncrement` — [`code/java/CounterGetAndAdd.java`](code/java/CounterGetAndAdd.java)

### 4.3.5 `compare&swap` (CAS)

The most powerful and most used primitive of modern concurrency. Three
operands: the register, an *expected* value, and a *new* value; the new
value is written **only if** the register still equals the expected one;
the old value (equivalently, a success flag) is returned:

```
entry: while not compare&swap(mutex, 0, 1) do skip;   -- try: "if free, make it mine"
       CS;
exit:  mutex <- 0
```

* C: `__atomic_compare_exchange_n` —
  [`code/c/counter_compare_and_swap.c`](code/c/counter_compare_and_swap.c)
* Go: `atomic.CompareAndSwapInt32` (returns a bool)
* Java: `AtomicInteger.compareAndSet` /
  `AtomicBoolean.compareAndSet` —
  [`code/java/CounterCompareAndSwap.java`](code/java/CounterCompareAndSwap.java)

CAS goes far beyond locks (§4.5): it is the workhorse of **lock-free**
programming, because it can make *any* "read, compute, write" sequence
conditional on nothing having changed in between.

## 4.4 Evaluation of hardware spinlocks

**Strengths** (as the slides summarize):

* They work for **n processes on n processors**, with a *constant* amount
  of shared state (one or two variables — compare with the bakery's 2n
  cells).
* The protocols are **simple and easy to verify** — one line of entry, one
  of exit (the ticket lock needs two plus an await).
* You can define **as many independent critical sections as you like**,
  each with its own variable (fine-grained locking), instead of one global
>  algorithm instance.

**Weaknesses:**

* **No hardware unification:** each architecture has its own instructions;
* **No (historical) language standardization:** portable code uses library
  or language atomics rather than raw instructions;
* **Busy waiting burns CPU:** a spinning process consumes its full quantum
  doing nothing; on a single core with the lock holder preempted, the
  system can livelock on spin (this is why real locks *spin briefly, then
  block* — OS mutexes/futexes; see Going further);
* **Arbitrary selection ⇒ starvation:** for `get&set`/`test&set`/`swap`/
  CAS spinlocks, the "winner" of the next acquisition is whoever's RMW
  lands first — an unlucky process can be overtaken forever (**no bounded
  waiting**; exercise 4.2). The **ticket lock is the exception**: FIFO
  order, wait bounded by the number of contenders.

| Lock | Primitive | Fairness | State |
|---|---|---|---|
| exchange / TAS / swap | `get&set` / `test&set` / `swap` | none (starvation possible) | 1 variable |
| ticket | `get&add` | **FIFO** (bounded waiting) | 2 variables |
| CAS | `compare&swap` | none (as used here) | 1 variable |

## 4.5 Beyond locks: the RMW *is* the critical section

Look back at every example of this chapter and chapter 2: the critical
section was always the same single statement — increment a shared counter.
For such a CS, taking a lock is overkill: the increment itself can be the
atomic RMW:

```
-- no lock at all:
get&add(counter, 1)          -- atomic increment
```

* C: `__atomic_add_fetch(&counter, 1, __ATOMIC_RELAXED)` —
  [`code/c/counter_ultimate.c`](code/c/counter_ultimate.c)
* Go: `atomic.AddInt64` — [`code/go/gocounter_ultimate.go`](code/go/gocounter_ultimate.go)
* Java: `AtomicInteger.incrementAndGet()` —
  [`code/java/CounterUltimate.java`](code/java/CounterUltimate.java)

The counter programs of chapters 1–2, with their lost updates, are solved
by *one instruction*. Generalizing this observation — implementing whole
data structures with CAS-style updates so that no lock is ever held — is
**lock-free programming**. A taste:

```
-- atomic counter via CAS (any RMW-computable function, not just +1):
do
  old <- counter; new <- f(old)
while compare&swap(counter, old, new) ≠ old
```

If the CAS fails, somebody changed the counter in between; retry with the
fresh value. This "optimistic" loop is the shape of virtually all lock-free
algorithms. Two things to be aware of: (1) **ABA** — CAS only checks the
*value*, so "changed away and changed back" goes undetected (harmless for a
counter, deadly for a stack/pointer — exercise 4.4); (2) lock-freedom
guarantees *some* process makes progress, not that *your* process does —
the ultimate fairness question is *wait-freedom* (see Going further).

## 4.6 Summary

* Disabling interrupts is unavailable to user code and useless on
  multiprocessors; hardware provides **atomic read-modify-write**
  instructions instead: indivisible, cache-coherent, barrier-carrying.
* Shared registers come in three strengths: **safe** (garbage during
  overlap), **regular** (old-or-new), **atomic** (total order illusion).
* Five classic RMW primitives: `get&set`, `get&add`, `test&set`, `swap`,
  `compare&swap`; each gives a one-line spinlock; `get&add` gives the
  fair **ticket lock** (bakery with atomic ticket dispenser).
* Spinlocks: simple, n-process, fine-grained — but they burn CPU while
  waiting and (except the ticket lock) allow starvation.
* If the critical section is a single RMW-expressible statement, **skip the
  lock**: `get&add`/CAS directly (the "ultimate counter"), the gateway to
  lock-free programming.

## 4.7 Exercises

**4.1** (a) Implement `test&set(r)` using one `compare&swap`. (b) Implement
`compare&swap(r, e, n)` using one `test&set`-like exchange `get&set` plus
local variables — can it be done with a *single* `get&set`? Discuss what
"atomic" has to cover for your construction to be correct.

**4.2** Starvation of the TAS lock: with processes p, q, r all spinning on
`test&set`, describe the schedule under which p never acquires the lock
even though it is *always running*. Which lock from this chapter forbids
this, and by what mechanism?

**4.3** Ticket-lock arithmetic: tickets and `turn` are 32-bit unsigned
counters that never decrease (until wraparound). A machine holds a lock
`2^31` times. (a) Why can `await turn = myticket` fail to match ever again
after wraparound if compared with `=`? (b) Show that comparing the *signed
difference* `(int32)(myticket - turn) > 0` restores correctness provided
fewer than `2^31` processes hold live tickets at once. Why is that
assumption free in practice?

**4.4** The ABA problem: a lock-free stack has top → A → B. Process 1 is
about to `CAS(top, A, B)` but is paused; meanwhile process 2 pops A, pops
B, and pushes A again (top is A *again*, but A now points elsewhere).
Complete the story: what does process 1's CAS do, and what harm results?
Name two standard remedies.

**4.5** For each requirement, choose the most appropriate primitive and
write the entry/exit protocol: (a) a global lock with strict FIFO fairness
for 100 threads; (b) an atomic counter of events; (c) a lock you want to
be able to *inspect* ("is it held, by whom?") cheaply without acquiring;
(d) atomically replacing a shared pointer if it still equals an observed
value.

**4.6** Explain, in at most three sentences each, why: (a) disabling
interrupts fails on multiprocessors; (b) the ticket lock never starves a
process that is spinning (assuming the CS always completes); (c) a
spinlock held by a preempted process on a *single*-core system is
catastrophic, and what real OSes do about it.

## 4.8 Solutions

**4.1** (a) `test&set(r) ≡ compare&swap(r, 0, 1)`: returns old value
(0 on success) and writes 1 iff it was 0 — exactly TAS. (b) With a single
`get&set` you can atomically *read-and-overwrite* `r`, but you cannot make
the write *conditional* on the observed value: after `old <- get&set(r, n)`
the write already happened even if `old ≠ e`. You can build CAS from a
`get&set`-based lock (acquire a lock, compare, write, release) — but then
your "atomic" operation contains a critical section and can block; with
*one unconditional* RMW it is impossible: the primitive must itself be
conditional. (That is why CAS is strictly stronger — see consensus numbers
in Going further.)

**4.2** Suppose q holds the lock and, on releasing it, immediately re-runs
its acquisition loop; if q's (and r's) TAS always lands between p's two
successive TAS attempts — schedulers with a fast re-acquirer make this
likely, since spinning processes hammer the same cache line — then p always
reads `mutex = 1` forever: p runs, tries, fails, infinitely often (no
blocking, pure starvation). The **ticket lock** forbids it: tickets are
unique and served in order, so p's ticket fixes p's position in the queue;
at most `n−1` acquisitions can precede p's entry.

**4.3** (a) With wraparound, `myticket = 2^32 − 5` can coexist with
`turn = 3`: they are semantically adjacent (turn has almost reached
myticket) but numerically unequal — and when `turn` eventually equals
`2^32 − 5` numerically it is *that* moment's turn, not this one's…
equality on wrapped values aliases distinct epochs. (b) The signed
difference `d = (int32)(myticket − turn)` measures distance forward *modulo
2^32*: it is positive, small (≤ number of live tickets) while p must still
wait, zero exactly when called, and never crosses the ambiguity boundary
`±2^31` while fewer than `2^31` tickets are in flight. Free in practice
because the number of *concurrently waiting* processes is bounded by the
thread count (thousands), nowhere near 2^31. (The same trick underlies TCP
sequence numbers.)

**4.4** Process 1's `CAS(top, A, B)` sees `top = A` (value matches),
succeeds, and sets `top <- B`. But B was popped and (say) freed/reused in
between: `top` now points to freed memory and the popped A's successor
relationship no longer holds — the stack is corrupted, potentially leaking
or cycling nodes. Remedy 1: **tagged pointers** — pack a monotonically
increasing counter with the pointer, so "A again" is a *different* CAS
value (used in IBM's original CAS paper era; Java does it invisibly with
`AtomicStampedReference`). Remedy 2: **hazard pointers**/epochs — delay
reuse of freed nodes while any process may hold a reference to them.

**4.5** (a) ticket lock (`get&add`); entry `myticket <- get&add(number,1)`,
await `turn = myticket`, exit `get&add(turn,1)`. (b) no lock:
`get&add(counter, 1)` (or `fetch_add`) per event. (c) an exchange-style
lock storing the owner id: acquire with `old <- get&set(owner, myid)` —
`old = 0` means acquired, otherwise `old` *is* the current holder's id;
release by writing 0 (better with CAS for safety). (d) `compare&swap` —
literally its semantics: `compare&swap(ptr, observed, new)`.

**4.6** (a) Interrupts are per-CPU: disabling them on one processor leaves
the others running; you'd need a global "disable all interrupts" broadcast,
which does not exist as a cheap user-level operation (and would wreck
system-wide interrupt latency). (b) Because acquisition order is fixed the
instant tickets are drawn (atomic increment gives a total order), and the
exit protocol advances `turn` monotonically to each waiter's ticket in
turn; a spinning process's ticket becomes the minimum unserved one after at
most n−1 exits. (c) The spinners consume the core forever while the holder
sits in the run queue unable to run and release: the lock is only released
by *running* the holder. Real OSes make locks hybrid: spin a few
iterations, then block (yield the core) — e.g. futexes on Linux,
`critical`-section aware schedulers, or parking (Go, JVM).

## 4.9 Going further

* **Better spinlocks:** test-and-test-and-set (TTAS) — spin with plain
  reads, TAS only on change: cache-friendly, the first optimization
  everyone applies; then exponential **backoff** locks, and **queue locks**
  (MCS, CLH): each waiter spins on a *private* flag, eliminating coherence
  storms and giving FIFO — the ticket lock's and the queue lock's ideas
  meet production quality.
* **Spin vs. block:** futexes (Linux `futex(2)`, Frank Dinig's and Go's
  runtime mutexes): fast path = userspace CAS; slow path = kernel sleep.
  This chapter's spinlocks are the fast-path half of every real mutex.
* **Memory ordering for real:** the last argument of GCC's `__atomic_*`
  builtins and C11's `memory_order_*`: `relaxed` (atomicity only — perfect
  for the ultimate counter), `acquire/release` (chapter 3's partial
  barriers, the lock idiom), `seq_cst` (the default, full SC). The
  *ultimate* counter of §4.5 needs only `relaxed`; the spinlocks need at
  least acquire/release.
* **The power hierarchy:** Herlihy's **consensus numbers** — how many
  processes can agree atomically using a primitive: reads/writes: 1;
  `test&set`, `swap`, `get&add`: 2; `compare&swap`: ∞. This *proves* CAS
  is strictly more powerful than TAS (exercise 4.1's impossibility is a
  theorem!) and explains why CAS is the universal substrate.
* **Lock-free and wait-free:** definitions (some thread progresses / every
>  thread progresses in bounded steps), the ABA problem in depth, and
  libraries built entirely on CAS: `java.util.concurrent` (Doug Lea),
  `stdatomic`, crossbeam/`sync/atomic`.
* **Reading:** Herlihy & Shavit, *The Art of Multiprocessor Programming*
  (chapters on locks and CAS — the natural continuation of this chapter);
  McKenney's papers (chapter 3) for the barrier/cache side.

---

*End of available chapters. When new slide sets arrive (semaphores,
monitors, producer–consumer, readers–writers, consensus…), new chapters
will be added here following the same structure: concepts → algorithms →
proofs → code → exercises → going further.*
