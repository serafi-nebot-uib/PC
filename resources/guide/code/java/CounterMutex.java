// CounterMutex.java — a counting semaphore simulated with Java's built-in
// monitor: synchronized methods, a state variable and wait/notify
// (guide 6.7). Mesa discipline: wait() re-checks in a while loop.
// Build: javac CounterMutex.java && java CounterMutex
public class CounterMutex {
    static final int N = 4;
    static final long ITERS = 100_000;

    static class Semaphore {
        private int count;

        Semaphore(int k) {
            count = k;
        }

        synchronized void waitS() throws InterruptedException {
            while (count < 1)
                wait();          // releases the monitor while blocked
            count--;
        }

        synchronized void signalS() {
            count++;
            notify();                                /* harmless if nobody waits */
        }
    }

    static Semaphore mutex = new Semaphore(1);
    static volatile long counter = 0;

    public static void main(String[] args) throws InterruptedException {
        Thread[] t = new Thread[N];
        for (int i = 0; i < N; i++) {
            t[i] = new Thread(() -> {
                for (long k = 0; k < ITERS; k++) {
                    try {
                        mutex.waitS();
                    } catch (InterruptedException e) {
                        return;
                    }
                    counter++;
                    mutex.signalS();
                }
            });
            t[i].start();
        }
        for (int i = 0; i < N; i++)
            t[i].join();
        System.out.println("counter = " + counter + " (expected " + N * ITERS + ")");
    }
}
