/* threads.c — creating and joining POSIX threads.
   Build: cc -O2 -pthread threads.c -o threads && ./threads */
#include <pthread.h>
#include <stdio.h>

static void *worker(void *arg) {
    const char *name = arg;
    for (int i = 0; i < 5; i++)
        printf("%s: step %d\n", name, i);
    return NULL;
}

int main(void) {
    pthread_t t1, t2;
    pthread_create(&t1, NULL, worker, "thread 1");
    pthread_create(&t2, NULL, worker, "thread 2");
    pthread_join(t1, NULL);
    pthread_join(t2, NULL);
    printf("main: both threads finished\n");
    return 0;
}
