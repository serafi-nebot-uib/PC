// CounterObject.java — mutual exclusion with a synchronized block on a
// lock object: the lock/unlock are added automatically at block entry/exit
// (guide 6.4).
// Build: javac CounterObject.java && java CounterObject
public class CounterObject {
    static final Object lock = new Object();
    static long counter = 0;
    static final int N = 4;
    static final long ITERS = 100_000;

    public static void main(String[] args) throws InterruptedException {
        Thread[] t = new Thread[N];
        for (int i = 0; i < N; i++) {
            t[i] = new Thread(() -> {
                for (long k = 0; k < ITERS; k++) {
                    synchronized (lock) {
                        counter++;
                    }
                }
            });
            t[i].start();
        }
        for (int i = 0; i < N; i++)
            t[i].join();
        System.out.println("counter = " + counter + " (expected " + N * ITERS + ")");
    }
}
