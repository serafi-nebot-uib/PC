/* peterson.c — Peterson's mutual exclusion algorithm (1981), chapter 2.8.
   Sequentially-consistent atomics keep the proof's assumptions valid.
   Build: cc -O2 -pthread peterson.c -o peterson && ./peterson */
#include <pthread.h>
#include <stdbool.h>
#include <stdio.h>

#define N 2
#define ITERS 200000L

#define LOAD(p)     __atomic_load_n((p), __ATOMIC_SEQ_CST)
#define STORE(p, v) __atomic_store_n((p), (v), __ATOMIC_SEQ_CST)

volatile static bool want[N] = {false, false};
volatile static int last = 0;
volatile static long counter = 0;

static void peterson_enter(int id) {
    int other = 1 - id;
    STORE(&want[id], true);
    STORE(&last, id);
    while (LOAD(&want[other]) && LOAD(&last) == id) {}
}

static void peterson_exit(int id) {
    STORE(&want[id], false);
}

static void *worker(void *arg) {
    int id = *(int *)arg;
    for (long i = 0; i < ITERS; i++) {
        peterson_enter(id);
        counter = counter + 1;
        peterson_exit(id);
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
