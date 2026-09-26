/* dekker.c — Dekker's mutual exclusion algorithm (1963), chapter 2.7.
   Shared variables are accessed with sequentially-consistent atomics so the
   algorithm's correctness assumptions hold on modern hardware (chapter 3).
   Build: cc -O2 -pthread dekker.c -o dekker && ./dekker */
#include <pthread.h>
#include <stdbool.h>
#include <stdio.h>

#define N 2
#define ITERS 200000L

#define LOAD(p)     __atomic_load_n((p), __ATOMIC_SEQ_CST)
#define STORE(p, v) __atomic_store_n((p), (v), __ATOMIC_SEQ_CST)

volatile static bool want[N] = {false, false};
volatile static int turn = 0;
volatile static long counter = 0;

static void dekker_enter(int id) {
    int other = 1 - id;
    STORE(&want[id], true);
    while (LOAD(&want[other])) {
        if (LOAD(&turn) != id) {
            STORE(&want[id], false);
            while (LOAD(&turn) != id) {}
            STORE(&want[id], true);
        }
    }
}

static void dekker_exit(int id) {
    STORE(&want[id], false);
    STORE(&turn, 1 - id);
}

static void *worker(void *arg) {
    int id = *(int *)arg;
    for (long i = 0; i < ITERS; i++) {
        dekker_enter(id);
        counter = counter + 1;
        dekker_exit(id);
    }
    return NULL;
}

int main(void) {
    pthread_t t[N];
    int ids[N] = {0, 1};
    for (int i = 0; i < N; i++)
        pthread_create(&t[i], NULL, worker, &ids[i]);
    for (int i = 0; i < N; i++)
        pthread_join(t[i], NULL);
    printf("counter = %ld (expected %ld)\n", counter, N * ITERS);
    return 0;
}
