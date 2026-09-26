/* counter_swap.c — spinlock with atomic swap, chapter 4.3.3.
   The old value of mutex is checked in the local variable after the exchange.
   Build: cc -O2 -pthread counter_swap.c -o counter_swap && ./counter_swap */
#include <pthread.h>
#include <stdio.h>

#define N 4
#define ITERS 100000L

volatile static int mutex = 0;
volatile static long counter = 0;

static void lock_acquire(void) {
    int local = 1;
    do {
        local = 1;
        local = __atomic_exchange_n(&mutex, local, __ATOMIC_SEQ_CST);
    } while (local == 1);
}

static void lock_release(void) {
    __atomic_store_n(&mutex, 0, __ATOMIC_SEQ_CST);
}

static void *worker(void *arg) {
    (void)arg;
    for (long i = 0; i < ITERS; i++) {
        lock_acquire();
        counter = counter + 1;
        lock_release();
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
