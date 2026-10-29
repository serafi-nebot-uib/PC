/* simulation_semaphore.c — a counting semaphore simulated with a mutex and
   a condition variable (guide 6.7). Mesa discipline: the wait re-checks.
   Build: cc -O2 -pthread simulation_semaphore.c -o simulation_semaphore && ./simulation_semaphore */
#include <pthread.h>
#include <stdio.h>

typedef struct {
    int value;
    pthread_mutex_t mutex;
    pthread_cond_t cond;
} semaphore;

void semaphore_init(semaphore *s, int k) {
    s->value = k;
    pthread_mutex_init(&s->mutex, NULL);
    pthread_cond_init(&s->cond, NULL);
}

void semaphore_wait(semaphore *s) {
    pthread_mutex_lock(&s->mutex);
    while (s->value == 0)
        pthread_cond_wait(&s->cond, &s->mutex);   /* releases mutex while blocked */
    s->value--;
    pthread_mutex_unlock(&s->mutex);
}

void semaphore_signal(semaphore *s) {
    pthread_mutex_lock(&s->mutex);
    s->value++;
    pthread_cond_signal(&s->cond);                /* harmless if nobody waits */
    pthread_mutex_unlock(&s->mutex);
}

#define N 4
#define ITERS 100000L

static semaphore mutex;
static long counter = 0;

static void *worker(void *arg) {
    (void)arg;
    for (long i = 0; i < ITERS; i++) {
        semaphore_wait(&mutex);
        counter = counter + 1;
        semaphore_signal(&mutex);
    }
    return NULL;
}

int main(void) {
    pthread_t t[N];
    semaphore_init(&mutex, 1);
    for (int i = 0; i < N; i++)
        pthread_create(&t[i], NULL, worker, NULL);
    for (int i = 0; i < N; i++)
        pthread_join(t[i], NULL);
    printf("counter = %ld (expected %ld)\n", counter, N * ITERS);
    return 0;
}
