/* counter_get_and_add.c — ticket lock with get&add (atomic fetch-and-add),
   chapter 4.3.4: the bakery algorithm with an atomic ticket dispenser.
   Build: cc -O2 -pthread counter_get_and_add.c -o counter_get_and_add && ./counter_get_and_add */
#include <pthread.h>
#include <stdio.h>

#define N 4
#define ITERS 100000L

#define LOAD(p) __atomic_load_n((p), __ATOMIC_SEQ_CST)

volatile static long next_ticket = 0;
volatile static long turn = 0;
volatile static long counter = 0;

static void ticket_lock(void) {
    long my = __atomic_fetch_add(&next_ticket, 1, __ATOMIC_SEQ_CST);
    while (LOAD(&turn) != my) {}
}

static void ticket_unlock(void) {
    __atomic_fetch_add(&turn, 1, __ATOMIC_SEQ_CST);
}

static void *worker(void *arg) {
    (void)arg;
    for (long i = 0; i < ITERS; i++) {
        ticket_lock();
        counter = counter + 1;
        ticket_unlock();
    }
    return NULL;
}

int main(void) {
    pthread_t t[N];
    for (int i = 0; i < N; i++)
        pthread_create(&t[i], NULL, worker, NULL);
    for (int i = 0; i < N; i++)
        pthread_join(t[i], NULL);
    printf("counter = %ld (expected %ld)\n", counter, N * ITERS);
    return 0;
}
