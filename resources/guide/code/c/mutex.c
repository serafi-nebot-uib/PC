/* mutex.c — mutual exclusion with a pthread mutex (the OS's binary
   semaphore with ownership): the counter is exact.
   Build: cc -O2 -pthread mutex.c -o mutex && ./mutex */
#include <pthread.h>
#include <stdio.h>

#define N 4
#define ITERS 100000L

static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
static long counter = 0;

static void *worker(void *arg) {
    (void)arg;
    for (long i = 0; i < ITERS; i++) {
        pthread_mutex_lock(&mutex);
        counter = counter + 1;
        pthread_mutex_unlock(&mutex);
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
