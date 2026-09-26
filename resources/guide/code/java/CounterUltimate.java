// CounterUltimate.java — no lock at all: the increment itself is the atomic
// read-modify-write operation (chapter 4.5).
// Build: javac CounterUltimate.java && java CounterUltimate
import java.util.concurrent.atomic.AtomicInteger;

public class CounterUltimate {
    static final int N = 4;
    static final long ITERS = 100_000;
    static AtomicInteger counter = new AtomicInteger(0);

    public static void main(String[] args) throws InterruptedException {
        Thread[] t = new Thread[N];
        for (int i = 0; i < N; i++) {
            t[i] = new Thread(() -> {
                for (long k = 0; k < ITERS; k++)
                    counter.incrementAndGet();
            });
            t[i].start();
        }
        for (int i = 0; i < N; i++)
            t[i].join();
        System.out.println("counter = " + counter.get() + " (expected " + N * ITERS + ")");
    }
}
