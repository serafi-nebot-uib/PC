#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>

#define NUM_THREADS 2
#define MAX_COUNT 10000000L

volatile long count = 0;
volatile int want[NUM_THREADS];
volatile int last;

void *counter(void *threadid) {
    long id, i, max = MAX_COUNT / NUM_THREADS;
    id = (long)threadid;
    for (i = 0; i < max; i++) {
        // Peterson
        int other = (id + 1) % NUM_THREADS; // Si executa p és q i viceversa
        want[id] = 1;
        last = id;
        //__sync_synchronize();
        while (want[other] && last == id)
            // sched_yield()
            ;
        count++; // SC
        want[id] = 0;
    }
    pthread_exit(NULL);
}

int main(int argc, char *argv[]) {
    pthread_t threads[NUM_THREADS];
    int rc;
    long t;
    want[0] = 0; // wantp = false
    want[1] = 0; // wantq = false
    last = 0;
    for (t = 0; t < NUM_THREADS; t++) {
        rc = pthread_create(&threads[t], NULL, counter, (void *)t);
        // Si pthread_create va bé retorna 0 sino una valor que indica l'error
        if (rc) {
            printf("ERROR; return code from pthread_create() is %d\n", rc);
            exit(-1);
        }
    }
    for (t = 0; t < NUM_THREADS; t++) {
        pthread_join(threads[t], NULL);
    }
    float error = (MAX_COUNT - count) / (float)MAX_COUNT * 100;
    printf("Final result: %ld Expected: %ld Diff: %ld Error: %3.3f%%\n", count,
           MAX_COUNT, count - MAX_COUNT, error);
    puts("Bye from main");
    pthread_exit(NULL);
}
