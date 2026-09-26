/* counter_test_and_set.c — spinlock with test&set, chapter 4.3.2.
   Acquire succeeds iff the old value was 0.
   Build: cc -O2 -pthread counter_test_and_set.c -o counter_test_and_set && ./counter_test_and_set */
#include <pthread.h>
#include <stdio.h>
#include <stdbool.h>

#define N 4
#define ITERS 100000L

volatile static bool mutex = false;
volatile static long counter = 0;

static void lock_acquire(void) {
    while (__atomic_test_and_set(&mutex, __ATOMIC_SEQ_CST)) {}
}

static void lock_release(void) {
    __atomic_clear(&mutex, __ATOMIC_SEQ_CST);
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
