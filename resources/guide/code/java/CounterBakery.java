// CounterBakery.java — Lamport's bakery algorithm for N processes
// (chapter 3.2). AtomicIntegerArray gives volatile/atomic element access.
// Build: javac CounterBakery.java && java CounterBakery
import java.util.concurrent.atomic.AtomicIntegerArray;

public class CounterBakery {
    static final int N = 4;
    static final long ITERS = 50_000;
    static AtomicIntegerArray choosing = new AtomicIntegerArray(N);
    static AtomicIntegerArray number = new AtomicIntegerArray(N);
    static volatile long counter = 0;

    static void bakeryEnter(int i) {
        choosing.set(i, 1);
        int max = 0;
        for (int j = 0; j < N; j++)
            max = Math.max(max, number.get(j));
        number.set(i, max + 1);
        choosing.set(i, 0);
        for (int j = 0; j < N; j++) {
            if (j == i) continue;
            while (choosing.get(j) == 1) {}
            int ni = number.get(i);
            int nj;
            do {
                nj = number.get(j);
            } while (nj != 0 && !(ni < nj || (ni == nj && i < j)));
        }
    }

    static void bakeryExit(int i) {
        number.set(i, 0);
    }

    public static void main(String[] args) throws InterruptedException {
        Thread[] t = new Thread[N];
        for (int i = 0; i < N; i++) {
            final int id = i;
            t[i] = new Thread(() -> {
                for (long k = 0; k < ITERS; k++) {
                    bakeryEnter(id);
                    counter++;
                    bakeryExit(id);
                }
            });
            t[i].start();
        }
        for (int i = 0; i < N; i++)
            t[i].join();
        System.out.println("counter = " + counter + " (expected " + N * ITERS + ")");
    }
}
