// CounterGetAndAdd.java — ticket lock with get&add
// (AtomicInteger.getAndAdd / getAndIncrement), chapter 4.3.4.
// The bakery algorithm with an atomic ticket dispenser: FIFO, no starvation.
// Build: javac CounterGetAndAdd.java && java CounterGetAndAdd
import java.util.concurrent.atomic.AtomicInteger;

public class CounterGetAndAdd {
    static final int N = 4;
    static final long ITERS = 100_000;
    static AtomicInteger nextTicket = new AtomicInteger(0);
    static AtomicInteger turn = new AtomicInteger(0);
    static volatile long counter = 0;

    static void ticketLock() {
        int my = nextTicket.getAndAdd(1);
        while (turn.get() != my) {}
    }

    static void ticketUnlock() {
        turn.getAndAdd(1);
    }

    public static void main(String[] args) throws InterruptedException {
        Thread[] t = new Thread[N];
        for (int i = 0; i < N; i++) {
            t[i] = new Thread(() -> {
                for (long k = 0; k < ITERS; k++) {
                    ticketLock();
                    counter++;
                    ticketUnlock();
                }
            });
            t[i].start();
        }
        for (int i = 0; i < N; i++)
            t[i].join();
        System.out.println("counter = " + counter + " (expected " + N * ITERS + ")");
    }
}
