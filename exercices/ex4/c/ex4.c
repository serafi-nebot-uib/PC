#include <stdio.h>
#include <stdlib.h>
#include <time.h>
#include <pthread.h>

#define NUM_THREADS 2
#define MAX_COUNT 10000000L

volatile long count = 0;
volatile int want[NUM_THREADS];

void *counter(void *arg) {
    long id = (long) arg, other = (id + 1) % NUM_THREADS;
    int mul = id == 0 ? 1 : -1;
    for (long i = 0; i < MAX_COUNT / NUM_THREADS; i++) {
        want[id] = want[other] == -1 ? -mul : mul;
        while (want[other] == mul*want[id]);
        count++;
        want[id] = 0;
    }
    pthread_exit(NULL);
}

int main(int argc, char **argv) {
    pthread_t threads[NUM_THREADS];
    for (long t = 0; t < NUM_THREADS; t++) {
        int rc = pthread_create(&threads[t], NULL, counter, (void *)t);
        if (rc) {
            printf("ERROR; return code from pthread_create() is %d\n", rc);
            exit(-1);
        }
    }
    for (long t = 0; t < NUM_THREADS; t++)
        pthread_join(threads[t], NULL);
    float error = (MAX_COUNT - count) / (float)MAX_COUNT * 100;
    printf("Final result: %ld Expected: %ld Diff: %ld Error: %3.3f%%\n", count, MAX_COUNT, count - MAX_COUNT, error);
    pthread_exit(NULL);
    return 0;
}
