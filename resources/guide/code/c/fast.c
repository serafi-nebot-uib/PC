/* fast.c — Lamport's fast mutual exclusion algorithm (1987), chapter 3.3.
   Two "doors" (gate1, gate2) plus want[] back-off flags, N processes.
   Build: cc -O2 -pthread fast.c -o fast && ./fast */
#include <pthread.h>
#include <stdbool.h>
#include <stdio.h>

#define N 4
#define ITERS 50000L

#define LOAD(p)     __atomic_load_n((p), __ATOMIC_SEQ_CST)
#define STORE(p, v) __atomic_store_n((p), (v), __ATOMIC_SEQ_CST)

volatile static int gate1 = 0;      /* 0 = empty, otherwise a process id (1..N) */
static int gate2 = 0;
volatile static bool want[N + 1];
volatile static long counter = 0;

static void fast_enter(int id) {
    for (;;) {
        STORE(&want[id], true);                 /* p1 */
        STORE(&gate1, id);                      /* p2: write id at door 1 */
        if (LOAD(&gate2) != 0) {                /* p3: door 2 occupied */
            STORE(&want[id], false);            /* p3a: back off */
            while (LOAD(&gate2) != 0) {}        /* p3b: wait for door 2 free */
            continue;                           /* p3c: compete again */
        }
        STORE(&gate2, id);                      /* p4: write id at door 2 */
        if (LOAD(&gate1) != id) {               /* p5: look back at door 1 */
            STORE(&want[id], false);            /* p5a */
            for (int j = 1; j <= N; j++)        /* p5b: wait for all others */
                if (j != id)
                    while (LOAD(&want[j])) {}
            if (LOAD(&gate2) == id)             /* p5c: my id still on door 2 */
                return;
            while (LOAD(&gate2) != 0) {}        /* p5d: q overtook: wait */
            continue;                           /* p5e: retry */
        }
        return;                                 /* fast path */
    }
}

static void fast_exit(int id) {
    STORE(&gate2, 0);                           /* p6: erase door 2 */
    STORE(&want[id], false);                    /* p7 */
}

static void *worker(void *arg) {
    int id = *(int *)arg;
    for (long k = 0; k < ITERS; k++) {
        fast_enter(id);
        counter = counter + 1;
        fast_exit(id);
    }
    return NULL;
}

int main(void) {
    pthread_t t[N];
    int ids[N];
    for (int i = 0; i < N; i++) {
        ids[i] = i + 1;
        pthread_create(&t[i], NULL, worker, &ids[i]);
    }
    for (int i = 0; i < N; i++)
        pthread_join(t[i], NULL);
    printf("counter = %ld (expected %ld)\n", counter, N * ITERS);
    return 0;
}
