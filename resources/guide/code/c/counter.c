/* counter.c — the lost-update race on a shared counter.
   Build: cc -O0 -pthread counter.c -o counter && ./counter
   (-O0 shows classic interleaving lost updates; -O2 also exposes
   compiler caching of the shared variable. Either way, expect < 400000.)
   See guide/01-introduction.md, section 1.12. */
#include <pthread.h>
#include <stdio.h>

#define THREADS 4
#define ITERS 100000L

static long counter = 0;

static void *worker(void *arg) {
    (void)arg;
    for (long i = 0; i < ITERS; i++)
        counter = counter + 1;
    return NULL;
}

int main(void) {
    pthread_t t[THREADS];
    for (int i = 0; i < THREADS; i++)
        pthread_create(&t[i], NULL, worker, NULL);
    for (int i = 0; i < THREADS; i++)
        pthread_join(t[i], NULL);
    printf("counter = %ld (expected %ld)\n", counter, THREADS * ITERS);
    return 0;
}
