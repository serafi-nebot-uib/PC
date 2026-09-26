// CounterCompareAndSwap.java — spinlock with compare&swap
// (AtomicInteger.compareAndSet), chapter 4.3.5.
// Build: javac CounterCompareAndSwap.java && java CounterCompareAndSwap
import java.util.concurrent.atomic.AtomicInteger;

public class CounterCompareAndSwap {
    static final int N = 4;
    static final long ITERS = 100_000;
    static AtomicInteger mutex = new AtomicInteger(0);
    static volatile long counter = 0;

    public static void main(String[] args) throws InterruptedException {
        Thread[] t = new Thread[N];
        for (int i = 0; i < N; i++) {
            t[i] = new Thread(() -> {
                for (long k = 0; k < ITERS; k++) {
                    while (!mutex.compareAndSet(0, 1)) {}
                    counter++;
                    mutex.set(0);
                }
            });
            t[i].start();
        }
        for (int i = 0; i < N; i++)
            t[i].join();
        System.out.println("counter = " + counter + " (expected " + N * ITERS + ")");
    }
}
