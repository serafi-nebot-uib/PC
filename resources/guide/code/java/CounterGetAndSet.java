// CounterGetAndSet.java — spinlock with get&set (AtomicBoolean.getAndSet),
// chapter 4.3.1. The slides map get&set to AtomicReference.getAndSet;
// AtomicBoolean is the idiomatic equivalent for a boolean lock word.
// Build: javac CounterGetAndSet.java && java CounterGetAndSet
import java.util.concurrent.atomic.AtomicBoolean;

public class CounterGetAndSet {
    static final int N = 4;
    static final long ITERS = 100_000;
    static AtomicBoolean mutex = new AtomicBoolean(false);
    static volatile long counter = 0;

    public static void main(String[] args) throws InterruptedException {
        Thread[] t = new Thread[N];
        for (int i = 0; i < N; i++) {
            t[i] = new Thread(() -> {
                for (long k = 0; k < ITERS; k++) {
                    while (mutex.getAndSet(true)) {}
                    counter++;
                    mutex.set(false);
                }
            });
            t[i].start();
        }
        for (int i = 0; i < N; i++)
            t[i].join();
        System.out.println("counter = " + counter + " (expected " + N * ITERS + ")");
    }
}
