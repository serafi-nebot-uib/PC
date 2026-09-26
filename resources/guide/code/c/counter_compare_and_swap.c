/* counter_compare_and_swap.c — spinlock with compare&swap (CAS), chapter 4.3.5.
   Build: cc -O2 -pthread counter_compare_and_swap.c -o counter_compare_and_swap && ./counter_compare_and_swap */
#include <pthread.h>
#include <stdio.h>
#include <stdbool.h>

#define N 4
#define ITERS 100000L

volatile static int mutex = 0;
volatile static long counter = 0;

static void lock_acquire(void) {
    int expected = 0;
    while (!__atomic_compare_exchange_n(&mutex, &expected, 1, false,
                                        __ATOMIC_SEQ_CST, __ATOMIC_SEQ_CST))
        expected = 0;
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
