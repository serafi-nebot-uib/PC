// SynchronizedCounter.java — a monitor as an encapsulated object: all public
// methods synchronized, state private (guide 6.3-6.4). The shared counter
// can only be touched through the monitor.
// Build: javac SynchronizedCounter.java && java SynchronizedCounter
public class SynchronizedCounter {
    static class Counter {
        private long n = 0;                    // not accessible outside

        public synchronized void increment() { // one caller at a time
            n = n + 1;
        }

        public synchronized long get() {
            return n;
        }
    }

    static final int N = 4;
    static final long ITERS = 100_000;

    public static void main(String[] args) throws InterruptedException {
        Counter c = new Counter();
        Thread[] t = new Thread[N];
        for (int i = 0; i < N; i++) {
            t[i] = new Thread(() -> {
                for (long k = 0; k < ITERS; k++)
                    c.increment();
            });
            t[i].start();
        }
        for (int i = 0; i < N; i++)
            t[i].join();
        System.out.println("counter = " + c.get() + " (expected " + N * ITERS + ")");
    }
}
