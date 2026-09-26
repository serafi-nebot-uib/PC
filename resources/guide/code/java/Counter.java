// Counter.java — the lost-update race on a shared counter.
// Build: javac Counter.java && java Counter
// See guide/01-introduction.md, section 1.12.
public class Counter {
    static volatile int counter = 0;
    static final int THREADS = 2;
    static final int ITERS = 10_000_000;

    public static void main(String[] args) throws InterruptedException {
        Thread[] t = new Thread[THREADS];
        for (int i = 0; i < THREADS; i++) {
            t[i] = new Thread(() -> {
                for (int k = 0; k < ITERS; k++)
                    counter++;
            });
            t[i].start();
        }
        for (int i = 0; i < THREADS; i++)
            t[i].join();
        System.out.println("counter = " + counter + " (expected " + THREADS * ITERS + ")");
    }
}
