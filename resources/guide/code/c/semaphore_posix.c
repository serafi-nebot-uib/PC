/* semaphore_posix.c — counting semaphore as mutex with POSIX semaphores
   (guide 5.8). NOTE: sem_init is unsupported on macOS (use
   semaphore_gcd.c there); this version targets Linux.
   Build (Linux): cc -O2 -pthread semaphore_posix.c -o semaphore_posix -lpthread */
#include <pthread.h>
#include <semaphore.h>
#include <stdio.h>

#define N 4
#define ITERS 100000L

static sem_t mutex;
static long counter = 0;

static void *worker(void *arg) {
    (void)arg;
    for (long i = 0; i < ITERS; i++) {
        sem_wait(&mutex);
        counter = counter + 1;
        sem_post(&mutex);
    }
    return NULL;
}

int main(void) {
    pthread_t t[N];
    if (sem_init(&mutex, 0, 1) != 0) {
        fprintf(stderr, "sem_init failed (macOS? use semaphore_gcd.c)\n");
        return 1;
    }
    for (int i = 0; i < N; i++)
        pthread_create(&t[i], NULL, worker, NULL);
    for (int i = 0; i < N; i++)
        pthread_join(t[i], NULL);
    sem_destroy(&mutex);
    printf("counter = %ld (expected %ld)\n", counter, N * ITERS);
    return 0;
}
