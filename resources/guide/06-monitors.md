# 6 · Monitors

> **What you will learn.** Why raw semaphores are error-prone and how the
> **monitor** packages shared data, the operations on it, and the
> synchronization into a single structured, language-supported construct;
> condition variables and their three flavors (explicit, implicit/Java,
> Ada's protected objects); the three monitor priority schemes (Hoare, Mesa,
> Java) and the immediate-resumption requirement (IRR); and the classic
> problems — semaphore simulation, bounded-buffer producer–consumer,
> readers–writers, dining philosophers, the bear and the honey pot — in
> monitor form.

## 6.1 From semaphores to monitors

Semaphores (chapter 5) are *primitives without busy waiting* — a huge step
up from spin loops — but they are **low-level and unstructured**:

* Synchronization is by *convention*: nothing ties a `wait` to the data it
  protects or to the `signal` that balances it. One forgotten `signal`, one
  `wait` on the wrong semaphore, one swapped order (remember §5.13's rule)
  — and the whole protocol collapses, usually far from the bug's cause.
* Every process must *cooperate correctly*: the mutual exclusion exists only
  because all users follow the same discipline.

A **monitor** is the structured answer: a construct that **encapsulates the
shared data together with the operations that access it**, centralizing all
access and guaranteeing the synchronization **automatically**, with the
support of the compiler and the OS. The slides' definition:

> Monitors are structured primitives that encapsulate the shared data and
> the operations accessing it, centralizing access and guaranteeing
> synchronization between concurrent processes with the support of the
> operating system.

You already know the shape: it is a **generalization of an object** in OO
programming — a class encapsulates data and operations; at runtime you
create objects and invoke operations. The monitor adds exactly one rule:

> **Only one process may execute an operation of a monitor (object) at any
> moment.**

That single rule is the entire mutual-exclusion protocol, applied
mechanically by the implementation. Nobody can forget it; nobody can get it
wrong.

## 6.2 Hoare's formalization

The monitor was formalized by **Tony Hoare (1974, "Monitors: an operating
system structuring concept")** — with Brinch Hansen's Concurrent Pascal
proving it in a real language — as the way to **structure operating systems
in high-level languages**:

* The OS is seen as a set of modules — *schedulers* — that assign shared
  resources among processes.
* The **monitor** is, for each scheduler, the set of data and the
  procedures that manage it.
* The monitor must guarantee **mutual exclusion** in the execution of its
  procedures; the monitor's variables can **only** be accessed from those
  procedures (encapsulation enforced by the language).

Early languages with monitors: Concurrent Pascal, Mesa, Ada, Java.

## 6.3 Declaration and use

The running example — the shared counter of chapters 1–2 — as a monitor:

```
monitor Counter is
  integer n ⇽ 0                    -- private: not accessible outside

  procedure increment()            -- executed under mutual exclusion
    n ⇽ n + 1
```

Processes `p` and `q` both call `increment()`. By definition only one call
executes at a time, so the lost-update race of chapter 1 is impossible.

What the monitor buys compared to chapter 2's algorithms:

* **It solves the critical section problem** — but the synchronization is
  **implicit**: the programmer cannot misplace the `wait`/`signal` pair,
  because there is no pair to misplace.
* The monitor is a **static entity, not a dynamic process**: the process
  *enters* the monitor, the monitor stays *closed* until the process
  leaves (finishes the procedure). Picture a phone booth: one occupant,
  door locked while occupied.
* As with semaphores, if several processes try to enter, only one succeeds.
  But there is **no (guaranteed) queue** for the entry — processes blocked
  at the door are woken arbitrarily — so **starvation is possible** in
  principle.

## 6.4 Monitors and objects in Java

Java has **no special monitor syntax** — instead, *every object has a
lock* (an entry queue for it included). The lock is used in two forms:

* **Synchronized block** — `synchronized (obj) { … }`: the thread takes
  `obj`'s lock at the `{` and releases it at the `}` (added automatically —
  including on exceptions).
* **Synchronized method** — `synchronized void f() { … }`: the lock is the
  *instance's* lock (or the class's, for `static` methods).

A class **all of whose public methods are synchronized** is called a *Java
monitor* — it is exactly the monitor of §6.3 with Java's object lock as the
door. Inside, the usual discipline: fields private, methods the only access
path.

Code: [`code/java/CounterObject.java`](code/java/CounterObject.java)
(synchronized block),
[`code/java/CounterMethod.java`](code/java/CounterMethod.java)
(synchronized method),
[`code/java/SynchronizedCounter.java`](code/java/SynchronizedCounter.java)
(a proper encapsulated monitor object).

## 6.5 Condition variables

Mutual exclusion alone is **not enough** for general synchronization: often
a process inside the monitor must *wait until some condition on the data
holds* (a producer must wait until the buffer is not full) — and while
waiting it must *release* the monitor, or nobody could ever change the data
it waits on. Monitors therefore add, for each monitor, **condition
variables** with two operations:

* **`waitC(c)`** — atomically: release the monitor (door opens) and block
  the caller *on condition `c`*.
* **`signalC(c)`** — if any process waits on `c`, release one of them (it
  must re-take the monitor); if none, do nothing. Unlike a semaphore's
  `signal`, this operation **has no memory**: signaling an empty condition
  stores nothing. (A condition variable is a *queue*, not a counter.)

Three implementation styles, by how the "condition" is expressed:

* **Explicit condition variables** — you declare variables that receive
  `waitC`/`signalC`, each with its own queue of blocked processes; the
  program checks the condition with ordinary `if`/`while` and calls the
  operations. (Concurrent Pascal, C/pthreads, Python, Ruby, Go's
  `sync.Cond`.)
* **Implicit (boolean) conditions** — the wait/signal operations are not
  tied to any variable: there is a *single* queue per monitor, and the
  program expresses conditions through ordinary **state variables**
  re-checked after waking. (Java's built-in `wait`/`notify`/`notifyAll`.)
* **Protected objects** — blocking and releasing are **automatic**, driven
  by boolean guard expressions (*barriers*) attached to operations
  (Ada's entries; §6.7).

## 6.6 Simulating a semaphore with condition variables

Inside one monitor, a semaphore is four lines:

```
monitor Sem is
  integer count ⇽ k
  condition c

  procedure wait()
    count ⇽ count - 1
    if count < 0: waitC(c)

  procedure signal()
    count ⇽ count + 1
    if count ≤ 0: signalC(c)
```

`count` plays the role of `S.V` (allowed to go negative — it then equals
minus the number of waiters); the condition variable's queue is `S.L`. The
`signalC` has an effect only when someone waits (`count ≤ 0`), mirroring
the semaphore's "wake q if someone is queued".

> This simulation is only correct under **IRR** — see §6.8. Under Mesa/Java
> semantics the `if`s must become `while`s (§6.9).

## 6.7 Implementations

**C (POSIX threads).** A semaphore simulated with a mutex + condition
variable:

```c
typedef struct {
    int value;
    pthread_mutex_t mutex;
    pthread_cond_t cond;
} sem_t_sim;

void wait_sim(sem_t_sim *s) {
    pthread_mutex_lock(&s->mutex);
    while (s->value == 0)
        pthread_cond_wait(&s->cond, &s->mutex);   // unlocks while waiting!
    s->value--;
    pthread_mutex_unlock(&s->mutex);
}

void signal_sim(sem_t_sim *s) {
    pthread_mutex_lock(&s->mutex);
    s->value++;
    pthread_cond_signal(&s->cond);                // harmless if nobody waits
    pthread_mutex_unlock(&s->mutex);
}
```

Note `pthread_cond_wait`'s signature: it takes the mutex and releases it
while blocked, re-taking it before returning — exactly the `waitC`
semantics. Both operations follow the **Mesa discipline** (§6.9): the
waiter re-checks its condition in a `while` (defending against spurious
wakeups, which POSIX explicitly allows, and against competing threads that
re-took the monitor first), and the signaler signals *unconditionally* — a
signal with no waiter is simply harmless. Code:
[`code/c/simulation_semaphore.c`](code/c/simulation_semaphore.c).

**Java.** Built-in: `synchronized` + `wait()` / `notify()` / `notifyAll()`
— implicit conditions with a single queue; a state variable
(`count`) controls the logic; and because Java is Mesa-style, the check
must be a `while`. Code:
[`code/java/CounterMutex.java`](code/java/CounterMutex.java).

**Ada: protected objects.** Ada encapsulates data in a *protected unit*
(declared as a type, with a specification and a body), accessible only
through **protected subprograms**, executed under mutual exclusion. A
protected type's interface can contain functions, procedures and
**entries**. An **entry** is like a procedure except it is guarded by a
boolean expression — the **barrier**. If the barrier is false, the calling
task is suspended (queued on that entry) and cannot access the object;
barriers are re-evaluated whenever any task leaves the object. This is the
protected-object flavor of condition synchronization — the guard *is* the
condition:

```ada
protected type Semaphore (Initial : Natural) is
   entry Wait;                 -- barrier on Count > 0
   procedure Signal;
private
   Count : Integer := Initial;
end Semaphore;

protected body Semaphore is
   entry Wait when Count > 0 is
   begin
      Count := Count - 1;
   end Wait;
   procedure Signal is
   begin
      Count := Count + 1;
   end Signal;
end Semaphore;
```

No `wait`/`signal` calls at all: the entry's barrier *is* the condition,
checked by the runtime. Code:
[`code/ada/def_semafors.ads`](code/ada/def_semafors.ads),
[`code/ada/def_semafors.adb`](code/ada/def_semafors.adb),
[`code/ada/semaphore_simulation.adb`](code/ada/semaphore_simulation.adb)
(a runnable simulation), following the
[Ada style guide](https://www.adaic.org/resources/add_content/docs/95style/html/sec_6/6-1-1.html).

## 6.8 Who runs next? The three priority schemes

When several processes interact with a monitor, three roles compete for it:

* **S** — the process executing a `signalC`;
* **W** — processes blocked on condition variables (`waitC`);
* **E** — processes blocked at the entry (waiting for the door).

The monitor must specify the priority among them, and there are exactly
three classic schemes (diagrams in the slides: the traditional monitor
schema and the Java monitor schema):

| Scheme | Priority | What happens on `signalC` |
|---|---|---|
| **1. Traditional (Hoare)** | **E < S < W** | The woken waiter **resumes immediately**, *inside* the monitor (the **IRR**: *immediate resumption requirement*); the signaler suspends until the waiter leaves |
| **2. Mesa** | **E < W < S** | The signaler **continues** (typically exits); the woken waiter merely becomes ready and must re-take the monitor like anyone at the entry |
| **3. Java** | **E = W < S** | The woken waiter goes to the **same queue as newcomers** (no priority for having been signaled) |

In the Java monitor schema, processes released by `notify`/`notifyAll` are
placed in the monitor's entry queue together with processes that never
waited — being notified grants no priority.

**Why IRR matters.** The elegant algorithms of this chapter (the semaphore
simulation, the readers–writers of §6.10) are written with `if` — "check
once; if the condition fails, wait; when woken, the condition *holds*".
That reasoning is only valid if the woken process runs **immediately**
after the signal, before anyone else can modify the state — the immediate
resumption requirement (Hoare's scheme). Hoare's scheme is also the
reason `signalC` in Hoare monitors *must* have a waiter (or be an error):
the signaler hands the monitor over.

**Java, Python and C implement Mesa-style semantics** (signaler continues;
woken process re-enters through the door, competing with everyone). A
signaled process must therefore **re-verify the wake-up condition** — the
rule of the chapter:

> Under Mesa/Java semantics, **change every `if` guarding a `waitC` into a
> `while`.** The condition was true when someone signaled; by the time you
> re-take the monitor, it may be false again. (See the course handout
> "Models de monitors" for the priority diagrams.)

## 6.9 Producer–consumer with a monitor

The bounded buffer of §5.13, monitor-style: the buffer *is* the monitor,
with two condition variables:

* **notFull** — blocks producers while the buffer is full;
* **notEmpty** — blocks consumers while it is empty.

The buffer state is **encapsulated** — invisible to producer and consumer
processes, which just call `deposit`/`fetch`:

```
monitor BoundedBuffer is
  constant N
  buffer[0..N-1]; integer count ⇽ 0, front ⇽ 0, rear ⇽ 0
  condition notFull, notEmpty

  procedure deposit(item)
    while count = N: waitC(notFull)     -- if (Hoare/IRR)
    buffer[rear] ⇽ item; rear ⇽ (rear + 1) mod N; count ⇽ count + 1
    signalC(notEmpty)

  function fetch() returns item
    while count = 0: waitC(notEmpty)
    item ⇽ buffer[front]; front ⇽ (front + 1) mod N; count ⇽ count - 1
    signalC(notFull)
```

Compare with §5.13: no split semaphores to initialize, no ordering rule to
remember ("availability before mutex") — the monitor *is* the mutex, and
the conditions are named after what they await. Note the deposit signals
`notEmpty` (a new item for consumers) and the fetch signals `notFull` (a
new free slot for producers) — each operation wakes the *other* kind of
waiter.

Code: [`code/python/producerconsumermonitor.py`](code/python/producerconsumermonitor.py)
(Python: **two** condition variables, one for producers, one for
consumers), and in Java
[`code/java/PCMonitor.java`](code/java/PCMonitor.java) +
[`code/java/ProducerConsumerMonitor.java`](code/java/ProducerConsumerMonitor.java).

**The Java twist** (slides 25–27): with Java's *implicit* conditions there
are no independent variables — producers and consumers share **one queue**,
so the code uses `notifyAll`: every woken thread (producer *and* consumer)
re-checks whether *it* can continue, and all but one go back to waiting.
Waking everyone when only one can enter is the price of the single queue.
And the failure example from the slides: with a **buffer of size 1**, using
`notify` instead of `notifyAll` can deadlock — `notify` may wake a thread
of the *same kind* (a producer waking a producer), which finds the buffer
full and waits again, while the compatible waiter (a consumer) sleeps
forever. Classic reference: the *Java notify() vs notifyAll()* Stack
Overflow discussion.

## 6.10 The readers–writers problem

A refinement of mutual exclusion: processes compete to access a shared
"database", but not all accesses conflict:

* **Readers** — must exclude *writers*, but not other readers (concurrent
  reads are harmless);
* **Writers** — must exclude **both** readers and other writers (a write
  must see a consistent state).

This abstracts database/file access: many concurrent readers are fine;
writes need exclusivity. The monitor uses four variables:

* `readers` — number of readers *in* the database (after `StartRead`,
  before `EndRead`);
* `writers` — number of writers in it (after `StartWrite`, before
  `EndWrite`);
* `OKtoRead` — condition variable blocking readers until it is their turn;
* `OKtoWrite` — condition variable blocking writers until it is their turn.

```
monitor ReadersWriter is
  integer readers ⇽ 0, writers ⇽ 0
  condition OKtoRead, OKtoWrite

  procedure StartRead()
    -- a reader suspends if a process is writing, or if some writer waits
    if writers > 0 or not empty(OKtoWrite): waitC(OKtoRead)
    readers ⇽ readers + 1
    signalC(OKtoRead)                       -- cascade! (see below)

  procedure EndRead()
    readers ⇽ readers - 1
    if readers = 0 and not empty(OKtoWrite):
      signalC(OKtoWrite)                    -- last reader out wakes a writer

  procedure StartWrite()
    -- a writer blocks only if processes are reading or writing
    if readers > 0 or writers > 0: waitC(OKtoWrite)
    writers ⇽ writers + 1

  procedure EndWrite()
    writers ⇽ writers - 1
    if not empty(OKtoRead): signalC(OKtoRead)   -- prefer a waiting reader
    else: signalC(OKtoWrite)
```

Reading the rules (all from the slides):

* A **reader** suspends not only while a writer is *writing* but also while
  a writer merely **waits** (`not empty(OKtoWrite)`) — this gives waiting
  writers priority over *newly arriving* readers and prevents writer
  starvation.
* A **writer** blocks only if someone is reading or writing.
* **EndRead**: the *last* reader out signals `OKtoWrite` — a blocked writer
  is released and runs `StartWrite`.
* The `signalC(OKtoRead)` at the end of `StartRead` produces a **cascade
  unlock** of blocked readers: when a writer finishes, one reader is woken
  by `EndWrite`; that reader increments `readers` and signals the *next*
  reader; and so on — the whole queue of readers flows in, one signal per
  link. A writer finishing thus gives precedence to readers over the
  waiting writers.
* All `if`s assume **IRR** (Hoare semantics); under Mesa they become
  `while`s, and the cascade becomes natural: each re-checking reader
  admits itself.

Code: [`code/python/readerswriters.py`](code/python/readerswriters.py)
(readers read a shared counter; writers increment it), and in Java
[`code/java/ReadersWritersMonitor.java`](code/java/ReadersWritersMonitor.java).

> **Two real-world notes from testing this guide's code.** (1) The Python
> version maintains a `waiting_writers` counter — the executable analogue of
> the Hoare monitor's `not empty(OKtoWrite)` test, which a plain condition
> variable cannot perform (its queue is not inspectable). (2) Hoare's
> `EndWrite` says: wake a reader **else** wake a writer — an *if/else* over
> queue emptiness. With untestable queues, the faithful translation is to
> `notify` **both** conditions and let the woken processes re-check their
> conditions: waking a "wrong" waiter is harmless, but a *missed* wakeup
> killed the first version of the demo — with two writers, the first
> writer's exit woke the readers queue, and the second writer slept
> forever. Edge-triggered notifications have no memory: when in doubt, wake
> everyone who could possibly make progress.

The **Java version** (slide 34) cannot use two condition variables — one
queue, so `notifyAll` wakes the *entire* mixed crowd: readers blocked by a
writer re-check and let each other in (cascading by re-checking), writers
also wake, find readers present, and go back to wait. It is impossible to
know a priori whether the next entrant will be a reader or a writer — the
fairness/priority policy of the Hoare version is lost, correctness is not.

**Ada** expresses the whole policy in the barriers of a protected object —
no queues, no signals, just guards
([`code/ada/def_monitor.ads`](code/ada/def_monitor.ads),
[`code/ada/def_monitor.adb`](code/ada/def_monitor.adb)):

```ada
protected type ReadersWriter is
   entry StartRead;
   procedure EndRead;
   entry StartWrite;
   procedure EndWrite;
private
   Readers : Natural := 0;
   Writing : Boolean := False;
end ReadersWriter;

protected body ReadersWriter is
   entry StartRead when not Writing is            -- readers excluded only while writing
   begin
      Readers := Readers + 1;
   end StartRead;

   procedure EndRead is
   begin
      Readers := Readers - 1;
   end EndRead;

   entry StartWrite when Readers = 0 and not Writing is   -- exclude everyone
   begin
      Writing := True;
   end StartWrite;

   procedure EndWrite is
   begin
      Writing := False;
   end EndWrite;
end ReadersWriter;
```

(Bear in mind Ada's entry queues re-evaluate barriers on every state
change; queuing discipline per entry is FIFO — entries are *strong*. The
writers-arrival-priority of the Hoare monitor would need an explicit
`WaitingWriters` counter in the barriers; exercise 6.5.)

## 6.11 Dining philosophers with a monitor

"The solution with monitors is simpler and less error-prone": mutual
exclusion between the monitor's methods makes checking and manipulating
shared variables trivial — no protocol around the checks, because the
monitor *is* the protocol.

State: an array `fork[i]` = the **number of forks currently free for
philosopher i** (its two adjacent forks; initially 2 for everyone), plus
one condition variable per philosopher:

```
monitor Philosophers is
  integer fork[5] ⇽ {2, 2, 2, 2, 2}
  condition OKtoEat[5]

  procedure takeForks(i)
    if fork[i] ≠ 2: waitC(OKtoEat[i])       -- wait for BOTH forks
    fork[i] ⇽ 0                             -- mine are taken now
    fork[(i-1) mod 5] ⇽ fork[(i-1) mod 5] - 1   -- left fork gone for my neighbor
    fork[(i+1) mod 5] ⇽ fork[(i+1) mod 5] - 1   -- right fork gone for my neighbor

  procedure releaseForks(i)
    fork[i] ⇽ 2
    fork[(i-1) mod 5] ⇽ fork[(i-1) mod 5] + 1
    fork[(i+1) mod 5] ⇽ fork[(i+1) mod 5] + 1
    if fork[(i-1) mod 5] = 2: signalC(OKtoEat[(i-1) mod 5])
    if fork[(i+1) mod 5] = 2: signalC(OKtoEat[(i+1) mod 5])
```

* `takeForks` waits until **both** adjacent forks are available (`fork[i] =
  2`); before leaving the monitor it decrements the *neighbors'* counts
  (my taking a fork removes it from their availability).
* After eating, `releaseForks` restores the counts and signals any neighbor
  whose count returned to 2.
* Deadlock is structurally impossible: a philosopher never holds one fork
  while blocked for the other — it blocks *inside* `takeForks` holding
  nothing but its place in a queue (breaks hold-and-wait); and the
  "eat only if `fork[i] = 2`" rule makes adjacent simultaneous eating
  impossible (mutual exclusion per fork follows).
* The `if`s are Hoare-style; under Mesa they become `while fork[i] ≠ 2`.

Code: [`code/python/philosophers_monitor.py`](code/python/philosophers_monitor.py).

**Java's `Condition` interface**
([`java.util.concurrent.locks`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/locks/Condition.html)):
Java's built-in single-queue scheme is often too coarse, so `j.u.c.locks`
offers explicit condition variables in combination with explicit
`Lock`s — the lock replaces `synchronized` (`lock()`/`unlock()`), and each
`Condition` (`await()`/`signal()`) is an independent wait-set **per lock**:
"multiple waits per object", exactly the explicit-condition-variable flavor
of §6.5. Code:
[`code/java/PhilosopherConditions.java`](code/java/PhilosopherConditions.java)
— the philosophers with a `ReentrantLock` and an array of five `Condition`s,
behaving like the monitor above (Mesa style: `while fork[i] != 2`).

## 6.12 The bear and the honey pot, monitor edition

The same story as §5.16, now with the monitor doing the heavy lifting:

```
monitor HoneyPot is
  constant H
  integer portions ⇽ 0
  condition potFull, potEmpty

  procedure fillOne()          -- a bee deposits one portion
    if portions = H: waitC(potEmpty)     -- pot full: don't disturb the bear
    portions ⇽ portions + 1
    if portions = H: signalC(potFull)    -- last portion wakes the bear

  procedure eatAll()           -- the bear
    if portions < H: waitC(potFull)
    portions ⇽ 0                          -- ... om nom nom ...
    signalC(potEmpty)                     -- reopen for the bees
```

While the pot is full, bees calling `fillOne` block on `potEmpty` — the
bear eats undisturbed; its `eatAll` (which holds the monitor only for the
bookkeeping) resets the count and wakes one bee to restart the cycle.
Compare with §5.16's semaphore version: the *policy* is identical, but the
state transitions live inside protected methods — no gate discipline to
hand-maintain, no risk of signaling `potEmpty` at the wrong moment.
(Mesa caveat as always: `while` instead of `if` in Java/Python/C.) A
runnable version is [`code/python/bearhoneypot_monitor.py`](code/python/bearhoneypot_monitor.py).

## 6.13 Summary

* Semaphores are powerful but low-level and unstructured; monitors
  encapsulate shared data + operations and enforce mutual exclusion
  automatically (one process inside at a time) with compiler/OS support.
* Hoare (1974): the monitor as the OS-structuring concept — data accessible
  only through its procedures; implicit synchronization; no entry queue
  guaranteed (starvation possible in principle).
* Java: every object has a lock; `synchronized` blocks/methods; an
  all-synchronized class is a Java monitor.
* Condition variables add *scheduled waiting*: `waitC` releases the monitor
  and blocks on a queue; `signalC` wakes one waiter and has **no memory**.
  Three flavors: explicit variables (C, Python, Go…), implicit single
  queue with state variables (Java), Ada's protected objects with
  entry barriers.
* Priority schemes: Hoare **E < S < W** (IRR — signaled process resumes
  immediately; `if` suffices), Mesa **E < W < S** (signaler continues —
  re-check with `while`), Java **E = W < S** (notified threads join the
  entry queue).
* Classic problems in monitor form: semaphore simulation, bounded buffer
  (notFull/notEmpty; Java's single queue forces `notifyAll`), readers–
  writers (state + OKtoRead/OKtoWrite; cascade unlock; Ada barriers
  express the policy directly), philosophers (fork counts + OKtoEat —
  deadlock structurally impossible), bear & pot (policy identical to the
  semaphore version, none of the bookkeeping).

## 6.14 Exercises

**6.1** Explain precisely why `waitC(c)` must *release the monitor* while
blocking — what deadlock arises if it does not? And why must the release
and the blocking be a single atomic step?

**6.2** Java bounded buffer of size 1 using `notify` instead of
`notifyAll`: exhibit the interleaving with one producer p and one consumer
q in which both end up waiting forever.

**6.3** Rewrite the semaphore simulation of §6.6 under Mesa semantics.
Explain why `count ≤ 0` in `signal` still wakes at most as many waiters as
signals.

**6.4** In the readers–writers monitor: a writer finishes with three
readers queued on `OKtoWrite`… wait — with three readers queued on
`OKtoRead` and one writer queued on `OKtoWrite`. Trace the cascade: who
runs, in what order, and what does each `signalC` accomplish?

**6.5** Ada's `ReadersWriter` of §6.10 lets a new reader sneak in while a
writer *waits* (its barrier only checks `Writing`). Add a
`WaitingWriters` counter so that waiting writers have priority over new
readers, mirroring the Hoare monitor's
`writers > 0 or not empty(OKtoWrite)`.

**6.6** Why is deadlock structurally impossible in the philosophers'
monitor of §6.11? Identify which Coffman condition (chapter 5.15) is
broken, and where exactly.

**6.7** In `HoneyPot` (§6.12): a bee executes `fillOne` when
`portions = H - 1`. Which statements run, which processes (if any) change
state, and who is woken?

## 6.15 Solutions

**6.1** If the waiter kept the monitor while blocked, no other process
could ever enter to change the data — including the process that could
make the condition true. The condition would never become true: instant
deadlock. And it must be atomic so that no third process can sneak in
*between* "the waiter releases" and "the waiter blocks": otherwise a
`signalC` arriving in that gap would be lost (the waiter is not yet
queued to receive it) — the classic lost-wakeup problem.

**6.2** Buffer size 1, currently full, p and q both runnable:

| step | event | state |
|---|---|---|
| 1 | q (consumer) takes item; buffer empty; q calls `notify` | the **only** waiter — if any — is a producer |
| 2 | q loops to take again: `while count = 0` → q waits | queue: {q}, buffer empty |
| 3 | p deposits; buffer full; p calls `notify` → wakes **q**? No — `notify` may choose any waiter; suppose it wakes q | q re-checks: `count = 0`?? — q is a *consumer*… |
| 4 | — but if p's `notify` implementation wakes p's *own kind* is impossible here (only q waits) |

The failing schedule needs *both* to be waiting first: buffer full, p
executes `deposit`… blocks (`count = 1`); q takes the item, calls `notify`
→ wakes **p** (only waiter — p re-checks, buffer empty, p deposits,
`notify` → wakes q…). With a *single* producer and consumer `notify`
actually suffices; the deadlock needs **two of a kind**: producers p₁, p₂
and buffer full: p₁ wakes p₂ (same kind), p₂ re-checks — full — waits
again; consumer q was never woken and sleeps on; p₁ waits too — **all
three wait forever**. `notify` gives no guarantee of *which* waiter wakes,
so "wake a compatible one" cannot be relied upon: hence `notifyAll` +
individual re-checks.

**6.3**

```
procedure wait()
  while count < 1: waitC(c)     -- or: count--; if count < 0: waitC(c)
  count ⇽ count - 1             --    with re-check on wake
procedure signal()
  count ⇽ count + 1
  if count ≤ 0: signalC(c)
```
Each `signal` increments `count` by one, so the number of waiters whose
condition becomes true grows by at most one per signal; spurious extra
wakeups (or stealers of the pass between wake and re-take) simply find
`count < 1` and wait again. Total passes granted = number of signals, as
with a real semaphore.

**6.4** After the writer's `EndWrite`: `writers = 0`; `OKtoRead`'s queue is
non-empty → `signalC(OKtoRead)` wakes **one** reader (IRR: it resumes
inside `StartRead` immediately): `readers = 1`, then it executes
`signalC(OKtoRead)` → second reader wakes: `readers = 2`, signals → third
reader: `readers = 3`, signals — the queue is now empty, its signal is a
no-op. The single writer stays queued on `OKtoWrite`: `EndRead` will wake
it only when `readers` drops back to 0. Three readers in, writer still
waiting — readers preferred after a write, exactly the slides' rule.

**6.5**

```ada
private
   Readers : Natural := 0;  Writing : Boolean := False;
   WaitingWriters : Natural := 0;

entry StartRead when not Writing and WaitingWriters = 0 is
begin
   Readers := Readers + 1;
end StartRead;

entry StartWrite when Readers = 0 and not Writing is
begin
   Writing := True;   -- entered: caller is writing
end StartWrite;
-- (callers queue on StartWrite while the barrier is false; to count
--  them, make StartWrite increment WaitingWriters *before* the barrier
--  via a two-step protocol: a procedure ReserveWrite that increments
--  and signals, plus the entry that consumes the reservation.)
```
The pattern: barriers can only read state, so *waiting* writers must be
counted by the code paths that lead them to wait — the Ada analogue of
`not empty(OKtoWrite)` is a counter maintained by the callers.

**6.6** Coffman's **hold-and-wait** is broken, and it is broken at a
precise place: a philosopher blocks *inside* `takeForks` *before taking
any fork* (the wait happens when `fork[i] ≠ 2`, and the fork decrements
run only after the wait returns). No philosopher ever holds one fork while
waiting for another, so no cycle of holders can form; the other three
conditions still hold (forks exclusive, no preemption, and circularity
would need holds) — but hold-and-wait alone suffices to rule the deadlock
out.

**6.7** `portions = H - 1 ≠ H`, so no wait: the bee increments
(`portions = H`), and since the pot just became full it executes
`signalC(potFull)`. If the bear was waiting on `potFull` (IRR: it resumes
immediately inside `eatAll`, resets `portions ⇽ 0`, signals `potEmpty` —
which may wake one bee waiting there). If the bear was not yet waiting,
the signal is **lost** — condition variables have no memory — but nothing
is broken: the bear's later `if portions < H` finds `portions = H` and
proceeds without waiting. (Under Mesa with `while`, identical reasoning
applies.)

## 6.16 Going further

* **The originals:** Hoare, *Monitors: an operating system structuring
  concept* (CACM 1974); Brinch Hansen's Concurrent Pascal; Lampson &
  Redell, *Experience with Processes and Monitors in Mesa* (1980) — the
  paper that argued for Mesa-style monitors and the `while`-loop
  discipline ("the signaler continues"), which is what the industry
  adopted.
* **Java in practice:** Goetz, *Java Concurrency in Practice* — when to
  use `notifyAll`, why `wait` must be in a loop, and
  `java.util.concurrent.locks` (`ReentrantLock`, `Condition`) as explicit
  monitors; the *notify vs notifyAll* Stack Overflow thread from the
  slides.
* **Ada:** protected objects and entry barriers (the Ada Rationale and
  Style Guide, §6.1.1) — the closest mainstream realization of Hoare's
  condition synchronization without explicit queues; the Ravenscar
  profile for real-time subsets.
* **Other flavors:** Go's `sync.Cond` (explicit conditions over a
  `Locker`), Python's `threading.Condition` (used in
  `producerconsumermonitor.py`), POSIX condition variables
  (the Oracle link in the slides) — and **spurious wakeups**: POSIX may
  wake a waiter with no signal at all; Mesa's `while` discipline is the
  standard defense (another reason it won).
* **Course material:** the handout *Models de monitors* (UIB campus
  virtual) has the priority-schema diagrams of §6.8.
* **Next:** the course moves to message passing — channels and messages —
  where synchronization is a *side effect of communication*, the last of
  the three great models (shared state, monitors, messages).

---

*End of the chapters available so far. New slide sets (channels and
messages, consensus, …) will be added here following the same structure.*
