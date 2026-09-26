/* counter_ultimate.c — no lock at all: the increment itself is the atomic
   read-modify-write operation (chapter 4.5).
   Build: cc -O2 -pthread counter_ultimate.c -o counter_ultimate && ./counter_ultimate */
#include <pthread.h>
#include <stdio.h>

#define N 4
#define ITERS 100000L

static long counter = 0;

static void *worker(void *arg) {
    (void)arg;
    for (long i = 0; i < ITERS; i++)
        __atomic_add_fetch(&counter, 1, __ATOMIC_RELAXED);
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
