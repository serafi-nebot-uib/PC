// CounterMethod.java — mutual exclusion with a synchronized method: the
// lock is the instance's lock (guide 6.4).
// Build: javac CounterMethod.java && java CounterMethod
public class CounterMethod {
    static class Counter {
        private long n = 0;

        synchronized void increment() {
            n++;
        }

        synchronized long value() {
            return n;
        }
    }

    static final int N = 4;
    static final long ITERS = 100_000;

    public static void main(String[] args) throws InterruptedException {
        Counter counter = new Counter();
        Thread[] t = new Thread[N];
        for (int i = 0; i < N; i++) {
            t[i] = new Thread(() -> {
                for (long k = 0; k < ITERS; k++)
                    counter.increment();
            });
            t[i].start();
        }
        for (int i = 0; i < N; i++)
            t[i].join();
        System.out.println("counter = " + counter.value() + " (expected " + N * ITERS + ")");
    }
}
