// CounterSemaphore.java — mutual exclusion with java.util.concurrent.Semaphore
// (a counting semaphore used with 1 permit as a mutex), guide 5.8-5.9.
// Build: javac CounterSemaphore.java && java CounterSemaphore
import java.util.concurrent.Semaphore;

public class CounterSemaphore {
    static final int N = 4;
    static final long ITERS = 100_000;
    static Semaphore mutex = new Semaphore(1);
    static volatile long counter = 0;

    public static void main(String[] args) throws InterruptedException {
        Thread[] t = new Thread[N];
        for (int i = 0; i < N; i++) {
            t[i] = new Thread(() -> {
                for (long k = 0; k < ITERS; k++) {
                    try {
                        mutex.acquire();
                    } catch (InterruptedException e) {
                        return;
                    }
                    counter++;
                    mutex.release();
                }
            });
            t[i].start();
        }
        for (int i = 0; i < N; i++)
            t[i].join();
        System.out.println("counter = " + counter + " (expected " + N * ITERS + ")");
    }
}
