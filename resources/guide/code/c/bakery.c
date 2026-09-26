/* bakery.c — Lamport's bakery algorithm (1974) for N processes, chapter 3.2.
   Build: cc -O2 -pthread bakery.c -o bakery && ./bakery */
#include <pthread.h>
#include <stdbool.h>
#include <stdio.h>

#define N 4
#define ITERS 50000L

#define LOAD(p)     __atomic_load_n((p), __ATOMIC_SEQ_CST)
#define STORE(p, v) __atomic_store_n((p), (v), __ATOMIC_SEQ_CST)

volatile static bool choosing[N];
volatile static long number[N];
volatile static long counter = 0;

static void bakery_enter(int i) {
    STORE(&choosing[i], true);
    long max = 0;
    for (int j = 0; j < N; j++) {
        long nj = LOAD(&number[j]);
        if (nj > max) max = nj;
    }
    STORE(&number[i], max + 1);
    STORE(&choosing[i], false);
    for (int j = 0; j < N; j++) {
        if (j == i) continue;
        while (LOAD(&choosing[j])) {}
        long nj, ni = LOAD(&number[i]);
        do {
            nj = LOAD(&number[j]);
        } while (nj != 0 && !(ni < nj || (ni == nj && i < j)));
    }
}

static void bakery_exit(int i) {
    STORE(&number[i], 0);
}

static void *worker(void *arg) {
    int id = *(int *)arg;
    for (long k = 0; k < ITERS; k++) {
        bakery_enter(id);
        counter = counter + 1;
        bakery_exit(id);
    }
    return NULL;
}

int main(void) {
    pthread_t t[N];
    int ids[N];
    for (int i = 0; i < N; i++) {
        ids[i] = i;
        pthread_create(&t[i], NULL, worker, &ids[i]);
    }
    for (int i = 0; i < N; i++)
        pthread_join(t[i], NULL);
    printf("counter = %ld (expected %ld)\n", counter, N * ITERS);
    return 0;
}
