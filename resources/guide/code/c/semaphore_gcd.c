/* semaphore_gcd.c — counting semaphore as mutex with Apple's GCD
   dispatch semaphores (the practical semaphore API on macOS).
   Build (macOS): cc -O2 semaphore_gcd.c -o semaphore_gcd && ./semaphore_gcd */
#include <dispatch/dispatch.h>
#include <pthread.h>
#include <stdio.h>

#define N 4
#define ITERS 100000L

static dispatch_semaphore_t mutex;
static long counter = 0;

static void *worker(void *arg) {
    (void)arg;
    for (long i = 0; i < ITERS; i++) {
        dispatch_semaphore_wait(mutex, DISPATCH_TIME_FOREVER);
        counter = counter + 1;
        dispatch_semaphore_signal(mutex);
    }
    return NULL;
}

int main(void) {
    pthread_t t[N];
    mutex = dispatch_semaphore_create(1);
    for (int i = 0; i < N; i++)
        pthread_create(&t[i], NULL, worker, NULL);
    for (int i = 0; i < N; i++)
        pthread_join(t[i], NULL);
    printf("counter = %ld (expected %ld)\n", counter, N * ITERS);
    return 0;
}
