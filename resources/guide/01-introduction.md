# 1 · Introduction to concurrent programming

> **What you will learn.** What a concurrent program is and how it differs from
> a parallel one; how to create threads in Java, Python, C, Go and Ada; the
> *interleaving model*, the single most important mental tool of this course;
> what "atomic" really means; the limited-critical-reference restriction; why
> `volatile` exists; and what a race condition is.

## 1.1 Why concurrency?

Modern software is inherently concurrent or distributed:

* **Event-driven GUIs** — while one window is repainting, another download must
  keep progressing. The *structure* of the program is "several things that can
  happen at any time".
* **Real-time systems** — a controller must sample sensors, update a display and
  respond to alarms *as they happen*, not strictly one after another.
* **Internet applications** — a multi-user game or a web server handles
  thousands of clients simultaneously.
* **Performance** — single cores stopped getting much faster around 2005; since
  then, performance comes from using many cores at once.

The good news: the concepts are **independent of the technology**. This course
deliberately separates three layers:

| Layer | Content |
|---|---|
| **Concepts** | interleaving, mutual exclusion, safety and liveness |
| **Tools** | semaphores, monitors, channels and messages |
| **Problems** | critical section, producer–consumer, readers–writers, consensus |

The same problems reappear whether you use Java threads, POSIX threads, Go
goroutines, Ada tasks or message-passing between machines. Learn the concepts
once, apply them everywhere.

## 1.2 Concurrency vs. parallelism

The two words are used interchangeably in casual speech; in this course they
mean different things, and the distinction is worth internalizing:

* A **concurrent program** is a set of sequential programs (its
  **processes**) that may be executed in parallel.
* **Parallelism** is about *execution*: the executions of different programs
  overlap **in time**, running on separate processors (related tasks or not).
* **Concurrency** is about *structure*: a program is *composed* of processes
  that are logically active at the same time, whether or not they physically
  run at the same instant. It is *potential parallelism*.

> **Structure vs. execution.** Concurrency is *dealing with* several things at
> once (structuring a program as independent activities); parallelism is
> *doing* several things at once (physically simultaneous execution). Rob
> Pike's slogan: *concurrency is not parallelism* — a concurrent program is
> correct and meaningful even on a single core; parallelism is just one way to
> execute it faster.

**Multitasking** is the word for the same idea in two different contexts:

* **Operating system view:** the OS generalizes the old idea of overlapping
  I/O with computation. A **scheduler** implements time-slicing (quantum-based
  preemption) so that many programs share one CPU and no single program
  monopolizes the machine.
* **Programming language view (multithreading):** a *single program* can
  launch and coordinate multiple threads of control. The concurrency lives
  inside your program, not just between programs.

## 1.3 Terminology: process, thread, task

Different traditions use slightly different words for "a sequential flow of
control":

* **Process** (OS theory): a program in execution, with its **own address
  space**. Two processes communicate only through the OS (pipes, sockets,
  shared memory segments, signals…).
* **Thread** (programming languages; popularized by POSIX `pthread`s on UNIX
  systems): a sequential flow of control that runs **inside the address space
  of a process**. Threads of one process share the same memory, so
  communication is trivial — and so is accidentally corrupting each other's
  data, which is what this course is largely about. (Example: Java's main
  thread.)
* **Task** (Ada): conceptually equivalent to a thread; Ada's unit of
  concurrency. The same word is also used in real-time systems for small units
  of work (schedulable entities), a slightly different meaning.

The key difference to remember: **processes have separate memory; threads share
memory.** Shared memory makes communication cheap and synchronization hard —
the central tension of chapters 2–4.

## 1.4 Where concurrent processes actually run

* **Multiprocessors** — several CPUs (today: several cores in one chip)
  dedicated to computation, e.g. numerical simulation.
* **Heterogeneous processors inside one computer** — graphics processors
  (GPUs), dedicated I/O and communication processors. Your laptop is a
  distributed system in a box.
* **Distributed systems** — many computers, no shared memory, communicating by
  messages, e.g. server farms.

Chapters 1–4 focus on processes sharing memory (multitasking,
multiprocessors). Distributed systems appear in the interleaving discussion
below and get full treatment later in the course.

## 1.5 Synchronization

Processes rarely work in complete isolation: to cooperate they need to
**communicate**, and to communicate they must **synchronize** — that is, agree
on when data is ready, who may touch it, and in what order things happen. Two
goals drive everything:

* **Correctness (safety)** — the result of the concurrent execution must be as
  if the interleaving had not corrupted anything. Concurrency bugs are the
  wrong results, lost updates, crashes.
* **Efficiency** — synchronization costs time (waiting) and hardware traffic.
  Good synchronization minimizes both: processes that *can* proceed should
  proceed.

Synchronization is the core of concurrent programming: without communication
there is no need for it, and with communication you cannot avoid it.

## 1.6 Concurrency in five languages

The course examples use five languages. The *concepts* are identical; the
syntax differs. All example files are in [`code/`](code/).

### 1.6.1 Java

Java represents each thread of control by an object of class `Thread`. There
are two ways to create one:

1. **Extend `Thread`** and override `run()`. Simple, but Java has no multiple
   inheritance, so your class can extend nothing else.
2. **Implement `Runnable`** (a functional interface with one method, `run()`)
   and pass it to a `Thread` constructor. **This is the recommended way** —
   it keeps your class free to inherit from something else and separates the
   *task* from the *thread running it*.

```java
class MyTask implements Runnable {
    public void run() { /* code this thread executes */ }
}
Thread t = new Thread(new MyTask());
t.start();   // allocates a new thread; it invokes run()
t.join();    // wait (block) until t terminates
```

* `run()` — what the thread executes. You *define* it; you never call it
  yourself.
* `start()` — actually creates the new thread and makes it call `run()`.
  Calling `run()` directly would just execute the code in the *current*
  thread — a classic beginner bug.
* `join()` — blocks the caller until the thread finishes. Both `start()`-related
  waiting and `join()` can raise `InterruptedException` (another thread may
  cancel the wait), so calls must be wrapped in `try/catch` (or declared).
* **`static` variables are shared** by all objects of the class — the easiest
  way to create shared state between threads, and hence races.
* Every thread may cache register copies of variables; declaring a field
  `volatile` forces loads and stores on every use (section 1.11).
* **Atomicity caveat:** reads/writes of all primitive types are atomic
  *except* `long` and `double`, which are 64-bit and may be written as two
  32-bit halves on 32-bit platforms. Declaring them `volatile` makes their
  access atomic too.

Example: [`code/java/Threads.java`](code/java/Threads.java). Reference:
[`java.lang.Thread`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html).

### 1.6.2 Python

Python's `threading` module offers two styles, mirroring the slides:

```python
import threading

# style 1: subclass Thread and redefine run()
class MyThread(threading.Thread):
    def run(self):
        ...                       # thread body

# style 2: create a Thread whose run() calls your function
t = threading.Thread(target=my_function, args=(...))

t.start()     # runs until run()/target returns
t.join()      # wait for termination
```

Once started, a thread runs independently; `join()` waits for it. Full
examples: [`code/python/threads.py`](code/python/threads.py),
[`code/python/counter.py`](code/python/counter.py). Docs:
[`concurrency`](https://docs.python.org/3/library/concurrency.html) package
overview.

**The GIL (Global Interpreter Lock).** CPython compiles source code to
*bytecode*, which the interpreter executes. The GIL is a global lock that
controls bytecode execution: **only one thread at a time can execute Python
bytecode inside a CPython process.** Consequences:

* Threads *are* concurrent — the interpreter switches between them
  (interleaving!) — but CPU-bound Python code gets **no true parallelism**.
* I/O-bound programs still benefit: a thread blocked on I/O (or inside C
  extensions like NumPy) **releases the GIL**, letting others run.
* For CPU-bound work, more threads do not speed things up and can even slow
  them down due to GIL contention and thread-management overhead. (Use
  `multiprocessing`, or C extensions, instead.)
* **The GIL does not eliminate races.** `n = n + 1` is a read, an add and a
  store; if the interpreter switches threads between the read and the store,
  an update is lost. One nuance discovered while testing this guide's code:
  since CPython 3.11 the interpreter only checks whether to switch at
  *specific bytecode boundaries* (loop back-edges and calls), so the minimal
  `for _ in range(N): n = n + 1` loop often shows *no* lost updates even
  under heavy contention — the switch lands between iterations, not inside
  the read-modify-write. The race window is still there; the demo in
  [`counter.py`](code/python/counter.py) makes it observable by releasing
  the GIL (`time.sleep(0)`) inside the window.
* Since **Python 3.13 (2024)** there is an experimental *free-threaded* build
  (PEP 703) that can run without the GIL, enabling real parallelism — with
  everything this course warns about suddenly applying to Python. Removing the
  GIL does not give linear speedup: cores, memory bandwidth, synchronization
  and overhead still limit performance.

Talks worth watching: David Beazley's *Python Concurrency* and his famous GIL
deep-dive.

### 1.6.3 C (POSIX threads)

The `pthread` library (POSIX standard; available on all UNIX-like systems,
including macOS and Linux) supports concurrency for C/C++:

```c
pthread_t t1, t2;
pthread_create(&t1, NULL, worker, arg);   // start: runs worker(arg)
pthread_join(t1, NULL);                   // wait for termination
```

The API covers thread creation and termination, synchronization (join,
mutexes, condition variables), scheduling control, thread-specific data, and
signal handling. (Tutorial: <https://computing.llnl.gov/tutorials/pthreads/>.)

The crucial mental model — **what threads of one process share vs. own**:

| Shared by all threads of the process | Private to each thread |
|---|---|
| code (instructions) | thread ID |
| global data / heap | register set (incl. program counter) |
| open files | stack pointer and stack (locals, return addresses) |
| signal handlers | signal mask, priority/scheduling params |
| current working directory | errno, thread return value |
| user and group IDs | |

Shared = cheap communication. Private = each thread's control flow and local
variables. Example: [`code/c/threads.c`](code/c/threads.c).

### 1.6.4 Go

Go makes concurrency a language primitive:

```go
go myFunction()      // launches a goroutine: independent execution of a function
```

* A **goroutine** is *not* an OS thread. It is a lightweight activity
  scheduled by the Go runtime, with its own (small, growable) stack. You can
  have hundreds of thousands of them.
* The runtime multiplexes (M:N) many goroutines onto a small pool of OS
  threads (by default one per CPU), moving goroutines between threads as
  needed. The slides' phrasing "one thread per program with hundreds of
  goroutines" captures the spirit (from the programmer's view, goroutines are
  the unit of concurrency) but the runtime really does use several OS threads.
* `sync` and `sync/atomic` packages provide the synchronization tools;
  channels provide message passing (`ch <- v`, `<-ch`).

Example: [`code/go/gothreads.go`](code/go/gothreads.go). Tour:
<https://go.dev/tour/concurrency/1>.

### 1.6.5 Ada

Ada calls a concurrent activity a **task**. A task is declared in two parts, a
*specification* and a *body*:

```ada
procedure Demo is
   task T;                    -- specification
   task body T is             -- body: the code it runs
   begin
      Put_Line ("hello from T");
   end T;
begin
   Put_Line ("main");
end Demo;
```

* A task is **activated** at the `begin` of the enclosing unit: it starts
  running in parallel with the parent and its sibling tasks.
* The enclosing subprogram **does not finish until all tasks it declared have
  finished** — an implicit join at the end of the block.
* For shared variables, Ada lets you declare them `pragma Volatile` /
  `pragma Atomic` (and `Volatile_Components` / `Atomic_Components` to make all
  elements of an array volatile/atomic) — the same concerns as Java's
  `volatile` (section 1.11).

Examples: [`code/ada/tasques.adb`](code/ada/tasques.adb).

## 1.7 Abstraction

Two vocabulary words from software engineering that the course leans on:

* **Encapsulation:** a program module is divided into a *public specification*
  and a *hidden implementation*. Users of the module rely only on the
  specification.
* **Concurrency is itself an abstraction:** it is a *designed way to reason*
  about the dynamic behavior of programs. Nobody can think about a million
  possible execution orders simultaneously; the concurrency abstraction gives
  us a simplified, disciplined model of what "happens" when processes run
  together. The next section defines it precisely.

## 1.8 The interleaving model of concurrency

This is the theoretical heart of the course. Everything in chapters 2–4 is
stated, analyzed and proven inside this model.

### 1.8.1 Definition

* A **concurrent program** consists of a *finite set of sequential processes*.
* Each process is written using a *finite set of atomic statements*
  (statements whose execution is indivisible; see section 1.9).
* An **execution** of the program is the execution of a *sequence* of atomic
  statements obtained by the **arbitrary interleaving** of the atomic
  statements of the processes.
* One such execution (one concrete interleaved sequence, together with the
  values it produces) is called a **computation** or a **scenario**
  (*càlcul*, *escenari*).

Think of it as a **global observer** (a scheduler deity): at each step it
arbitrarily picks one of the processes and lets *one* atomic statement of that
process run to completion; then it picks again — maybe the same process, maybe
another. The observable behavior of the program is the resulting sequence of
statements. Note the two guarantees of this abstraction:

1. A statement of one process **completes before** any statement of another
   process begins (atomicity), and
2. we always have a **global view of the state**: every read sees the value
   left by the last executed write in the interleaving.

> Reality check. Real machines are messier (see 1.8.4) — but this model is
> exactly right for *reasoning*: if a program is correct under *arbitrary*
> interleaving, it is correct no matter how the scheduler behaves, on any
> number of cores. Chapter 3 discusses what happens when even assumption (2)
> breaks (memory models).

### 1.8.2 States, transitions, state diagrams

* During a computation, the **control pointer** of a process indicates which
  statement that process will execute next. Every process has its own control
  pointer (its program counter).
* The **state** of a concurrent program is a tuple consisting of **one label
  (control-pointer value) per process** and **one value per variable** (global
  and local).
* There is a **transition** from state `s1` to state `s2` if executing one
  statement (pointed at by some process's control pointer) in `s1` yields
  `s2`.
* The **state diagram** is built inductively: start from the initial state
  (a single node); from every state, add the transitions of all executable
  statements; repeat for newly discovered states. Each state labels exactly
  one node. The states obtained this way are the **reachable states**.

**Worked example.** Two processes share `n`, initially 0; each runs one
increment:

```
shared: int n = 0;
     p                  q
p1: n <- n + 1      q1: n <- n + 1
```

States are `(pc_p, pc_q, n)`:

```
                    (p1,q1,0)
              p1                q1
           (p2,q1,1)          (p1,q2,1)
              q1                p1
           (p2,q2,2)          (p2,q2,2)
```

* Four reachable states; **two scenarios** (left path: `p1;q1`, right path:
  `q1;p1`). Both end with `n = 2`.
* On a single core, the two scenarios happen because the scheduler may preempt
  between the statements; on two cores the picture is the same *as an
  interleaving* — parallel execution of atomic statements is observationally
  equivalent to some interleaving of them.
* The number of scenarios grows explosively with the number of processes and
  statements — you can never test them all. That is why we *prove* properties
  instead (chapters 2–4) — and why this guide machine-checked those proofs.

### 1.8.3 Arbitrary interleaving: consequences

* After each statement, the next statement may come from *any* process —
  including the same one again. The model constrains only the *order* of
  statements, never *how much time* passes between them.
* **Correctness must not depend on execution speed.** A concurrent algorithm
  must work for *all* interleavings — including the ones where one process
  runs a billion steps while another runs one. (This becomes one of Dijkstra's
  formal requirements in chapter 2.)
* **You cannot in general reproduce an execution.** The exact interleaving
  that triggered a bug depends on nanosecond-level timing; running the program
  again usually does not repeat it. This is why concurrency bugs are
  notoriously hard to debug, and why we favor designs that are correct *by
  construction* over ones that are correct *by luck*.

### 1.8.4 The abstraction in real systems

How does the "global observer interleaving" picture map onto real hardware?

* **Multitasking systems (one CPU):** the "observer" is the CPU + operating
  system. Interrupts (timers, I/O) let the **scheduler** take the CPU away
  from one process and give it to another via a **context switch** (saving
  registers, loading another's). Atomicity of a single instruction is
  guaranteed by the CPU; the interleaving granularity between processes is
  controlled by interrupts and the scheduler.
* **Multiprocessors:** with enough CPUs, processes genuinely run
  simultaneously — no artificial interleaving needed. If two CPUs write the
  same memory cell "at the same time", there is a potential conflict — but in
  practice the memory hardware is designed for this: **accesses to a single
  (aligned) memory cell are made atomic by the hardware**, which serializes
  them. So even true parallel execution *behaves like* an interleaving at the
  granularity of single loads/stores.
* **Distributed systems:** each node executes its own statements and
  communicates by **messages**. There is no shared memory. The interleaving
  view still applies, with one extra causality rule: **a message must be sent
  before it is received**. What makes distributed systems genuinely harder
  than shared-memory ones: no process has a global view (each node knows only
  its own history), and nodes can fail — the model depends on the topology of
  the interconnect and on fault tolerance.

## 1.9 Atomic statements

> An **atomic statement** is one that executes to completion **without any
> interleaving** of statements from other processes.

Atomicity is not a property of the *code* alone; it is a property of the
*granularity at which we agree to model the system*:

* At the hardware level, a single load or store of an aligned word is atomic
  (multiprocessor memory systems serialize concurrent accesses to the same
  cell — previous section).
* In our pseudocode, a simple assignment `x <- e` with only local or
  unshared variables is taken as atomic.
* A *compound* operation — `n <- n + 1` when `n` is shared, or
  `await wantq = false` followed by a later flag update — is **not**
  automatically atomic. Assuming atomicity of such compound operations is the
  root cause of the wrong "solutions" in chapter 2.

Why does atomicity matter so much? Because the interleaving model's guarantee
("a statement of one process completes before another's begins") only protects
statements that are actually indivisible. If you *assume* `n <- n + 1` is
atomic but the hardware interleaves the read of `n` with another process's
write, your reasoning — and your program — collapses. The next section makes
this precise.

## 1.10 Correct and incorrect scenarios; limited critical reference (LCR)

Consider the shared counter `n <- n + 1` executed by two processes. On real
hardware, the increment is *not* one atomic statement: it is a read, an add,
and a write. To reason honestly we must decompose it (with a local `t`):

```
shared: int n = 0;
        p                     q
p1: tp <- n + 1          q1: tq <- n + 1
p2: n  <- tp             q2: n  <- tq
```

**Correct scenario** (q waits until p is done):

| step | statement | n | comment |
|---|---|---|---|
| 1 | p1 | 0 | tp = 1 |
| 2 | p2 | 1 | p's increment lands |
| 3 | q1 | 1 | tq = 2 |
| 4 | q2 | 2 | final value 2 ✔ |

**Incorrect scenario** (the statements interleave badly):

| step | statement | n | comment |
|---|---|---|---|
| 1 | p1 | 0 | tp = 1 |
| 2 | q1 | 0 | tq = 1 — q read the *old* n! |
| 3 | p2 | 1 | p writes 1 |
| 4 | q2 | 1 | q writes 1 — **p's increment is lost** ✘ |

Both scenarios are legal executions of the same program; the final value of
`n` is 1 or 2 depending on timing. Nothing "malfunctioned" — the program is
simply not designed for concurrent execution.

### Critical references

> A reference to a variable `v` is a **critical reference** (in process `p`) if
> `v` is (or may be) accessed by another process:
>
> (a) `v` is assigned in one process and also appears (read or written) in
> another, or
> (b) `v` appears in an expression in one process and is assigned in another.

In the example above, inside statement `p1: tp <- n + 1` the read of `n` is a
critical reference (q assigns `n`); inside `q2: n <- tq` the write of `n` is
critical. 

> **The limited critical reference (LCR) restriction:** an atomic statement may
> contain **at most one critical reference**.

Why this rule exists: it tells us exactly *what we may assume to be atomic*.
If a statement contains two or more references to variables that other
processes touch (e.g. `n <- n + 1` reads `n` and writes `n`), we may **not**
assume the whole statement executes indivisibly — unless the hardware or
language explicitly guarantees it. Decomposing such statements into
single-critical-reference steps (as we did above) makes the interleaving
semantics well-defined at a granularity that real machines actually honor.
Conversely, if a statement touches shared variables only once, the interleaving
model's guarantee applies directly.

## 1.11 Volatile variables

Suppose `n` is shared between processes p and q, and q assigns it at some
point. Because of the interleaving model, q's assignment may land *anywhere*
in p's execution. Compilers do not know this: seeing that p reads `n` several
times with no assignment to `n` *in p*, an optimizer may legally:

* load `n` once into a register and reuse the stale register value, or
* reorder/eliminate memory accesses it considers redundant.

Then p would work on a value that is no longer the most recent one.

> Declaring a variable **volatile** tells the compiler: this variable may be
> modified by someone else at any time — **load it from memory before every
> use and store it back after every modification.**

Facts worth remembering:

* `volatile` fixes *compiler* caching/reordering. In Java, `volatile` also
  gives atomicity for `long`/`double` and establishes a memory ordering
  (happens-before) among volatile accesses — chapter 3.
* `volatile` does **not** make compound operations atomic: `volatile int n;
  n = n + 1;` is still read-add-write and can still lose updates. In
  pre-C11 C, `volatile` has *no* defined inter-thread semantics at all.

## 1.12 Race conditions

> A **race condition** is a flaw in a program that **only manifests when its
  execution is interleaved with other processes in a particular way** — the
  result depends on the *relative timing* of concurrent accesses to shared
  state. (The slides describe it as the error that appears when a program "has
> not been designed adequately for simultaneous execution with others".)

The lost-update example of section 1.10 is the canonical race: two processes
check/modify the same variable, and at least one update vanishes depending on
the interleaving. The name comes from the accesses "racing" each other to the
variable.

A small terminology refinement you may meet in the literature: a **data
race** is the *specific* case of two concurrent accesses to the same location,
at least one a write, with no synchronization ordering them (undefined
behavior in C/Java unless atomic). A **race condition** is the broader
*situation* where correctness depends on timing. You can have a data race
without a logical race (on data that does not matter) and — surprisingly — a
logical race without a data race (all accesses atomic, but the check-then-act
logic still interleaves badly; see attempt 2 in chapter 2).

> The slides illustrate race conditions with a *deadlock* example (two
> processes each waiting forever for the other to act). Deadlocks are indeed a
> typical pathology of badly designed concurrent programs, but they are a
> different failure mode (a *liveness* failure, chapter 2) — a race condition
> is about *wrong values* depending on timing. This guide keeps the standard
> meaning.

You will find runnable lost-update demos in every language of the course:

* [`code/java/Counter.java`](code/java/Counter.java)
* [`code/python/counter.py`](code/python/counter.py) — the race exists
  *despite* the GIL; the read-modify-write is forced open with
  `time.sleep(0)` (see the CPython 3.11+ nuance in section 1.6.2).
* [`code/c/counter.c`](code/c/counter.c)
* [`code/go/counter.go`](code/go/counter.go)
* [`code/ada/counter.adb`](code/ada/counter.adb)

All of them increment a shared counter a large number of times from two or
more threads and print the final value — which is (almost) always smaller than
expected. Chapters 2–4 build the tools that fix this.

## 1.13 Summary

* A concurrent program is a set of sequential processes; concurrency is
  *structure*, parallelism is *simultaneous execution*.
* Threads share memory (cheap communication, hard synchronization); processes
  have separate address spaces; Ada tasks are threads; goroutines are
  runtime-scheduled lightweight threads.
* The **interleaving model**: executions are arbitrary interleavings of
  atomic statements; states are tuples of control pointers + variable values;
  the state diagram enumerates reachable states; scenarios are paths.
* Correctness must be independent of relative speed; executions are
  irreproducible — hence proofs, not tests.
* Real systems realize the interleaving model through interrupts/schedulers
  (uniprocessor), atomic hardware access to single memory cells
  (multiprocessor), and send-before-receive causality (distributed).
* **Atomic statement**: executes indivisibly; compound shared-variable
  statements must be decomposed; **LCR**: at most one critical reference per
  atomic statement.
* `volatile` prevents compiler caching of shared variables; it does not create
  atomicity.
* **Race condition**: outcome depends on interleaving; the lost update of
  `n <- n + 1` is the archetype.

## 1.14 Exercises

**1.1** Classify each of the following as *concurrency*, *parallelism*, both,
or neither: (a) four build jobs compiled simultaneously on four cores;
(b) a GUI whose animation loop, network handler and sound handler are separate
activities on one core; (c) a program computing `a+b` twice; (d) ten web
servers on ten machines handling one site's traffic.

**1.2** For the program of section 1.8.2 (two processes, one increment each,
with `n <- n + 1` modeled atomically): (a) how many reachable states are there
if both processes run the increment *twice* each (statements
`n <- n+1; n <- n+1`)? (b) Are all final values of `n` equal? Justify.

**1.3** Write the full state diagram of the decomposed increment program
(p1, p2, q1, q2) of section 1.10. How many reachable states and how many
scenarios are there? Which final values of `n` are possible?

**1.4** In `code/python/counter.py`, why does the race exist even though the
GIL prevents two threads from executing bytecode simultaneously?

**1.5** For each statement below, say whether it contains more than one
critical reference (assume `i`, `j` are local and `a`, `b`, `n` may be
modified by other processes):
(a) `i <- a`; (b) `a <- b`; (c) `n <- n + 1`; (d) `i <- a + j`; (e) `a <- 5`.

**1.6** A friend claims: "make the flag `volatile` and the check-then-set
entry protocol is safe." Explain precisely which problem `volatile` solves and
which one it does not.

## 1.15 Solutions

**1.1** (a) parallelism (independent tasks, physically simultaneous — related
or not is irrelevant); (b) concurrency without parallelism (structure of
independent activities, single core); (c) neither — it is a sequential
program with repetition; (d) both: the site's *structure* is concurrent
handling, and execution is physically simultaneous (distributed).

**1.2** (a) States `(pc_p, pc_q, n)` with pc ∈ {s1, s2, s3(done)} × same for q
and n tracking increments: 3×3 program-counter pairs × 5 possible values
(0..4), though not all combinations reachable; the reachable count is
**25 states** (every pc pair is reachable with every n ≤ (steps done)); the
exact number is not the point — the point is (b): **yes**, since each
increment is a single atomic statement, any interleaving yields exactly 4
increments: `n = 4` always. Atomicity of the increment eliminates the race.

**1.3** States: `(pc_p, pc_q, tp, tq, n)`. Programs counters range over
{p1, p2, done} × {q1, q2, done}; tp, tq ∈ {1}, n ∈ {0, 1, 2}. All
3×3 = 9 pc-pair combinations are reachable (any interleaving order is
possible), with the corresponding n values: **9 reachable states**.
**6 scenarios** (orders of p1<p2, q1<q2 in a sequence of 4 steps:
4!/(2!·2!) = 6). Final values: **n = 2 in 4 scenarios, n = 1 in 2 scenarios**
(the ones where q1 executes before p2, or p1 before q2 — i.e. one increment
is lost).

**1.4** The GIL serializes *bytecode* execution, but `n += 1` is several
bytecodes (`LOAD n`, `ADD`, `STORE n`). A thread switch between the load and
the store makes one thread write a stale value. The GIL prevents
*simultaneous* access, not *interleaved* access. (Fine print: since CPython
3.11, switches only occur at back-edges and calls — see section 1.6.2 — so
the minimal loop may hide the race; widen the window with any call between
the read and the write, as `counter.py` does.)

**1.5** (a) one critical reference (read of `a`) — OK under LCR.
(b) two (read `b`, write `a`) — violates LCR; decompose: `i <- b; a <- i`.
(c) two (read `n`, write `n`) — violates LCR; decompose as in 1.10.
(d) one (read of `a`) — OK. (e) one (write of `a`) — OK. (The value 5 is a
constant, not a variable reference.)

**1.6** `volatile` (in Java/C) forces the compiler to re-load the flag before
every test and store it after every write, and orders the *volatile*
accesses. It does **not** make the sequence "read other's flag, then set my
flag" one atomic step: another thread can still run entirely between the two
statements. Exactly that gap breaks attempt 2 in chapter 2 — with perfectly
volatile, perfectly visible variables. Atomicity ≠ visibility.

## 1.16 Going further

* **Rob Pike, *Concurrency is not parallelism*** (Heroku talk, 2012) — the
  structure/execution distinction, with gopher illustrations.
* **M. Ben-Ari, *Principles of Concurrent and Distributed Programming*, ch. 1–2**
  — where the interleaving semantics and the LCR restriction come from.
* **David Beazley** — *Python Concurrency Live at the Edge of Your Seat*
  (USENIX 2009, <http://www.dabeaz.com/usenix2009/concurrent/>) and his GIL
  talk (<http://www.dabeaz.com/python/GIL.pdf>): the GIL behavior made
  visceral; also PEP 703 and the 3.13 free-threading HOWTO
  (<https://docs.python.org/3/howto/free-threading-python.html>).
* **Why is debugging so hard?** Look into heisenbugs, stress testing,
  deterministic schedulers for testing (e.g. Go's `-race` detector, ThreadSanitizer)
  — all exist because of section 1.8.3's irreproducibility.
* **A taste of what's ahead:** the memory-model subtleties hinted at in 1.11
  (compiler + cache + out-of-order execution) are exactly what breaks the
  "correct" algorithms of chapter 2 on real machines — the plot of chapters
  3 and 4.

---

Next: [02-critical-section.md](02-critical-section.md) — the first great
problem of the course, and five decades of beautiful wrong answers to it.
