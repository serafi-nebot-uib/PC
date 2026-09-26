// CounterPeterson.java — Peterson's algorithm (chapter 2.8) protecting a counter.
// wantp/wantq are scalar volatiles (volatile on an ARRAY covers only the
// reference, not the elements); last records who requested entry last.
// Build: javac CounterPeterson.java && java CounterPeterson
public class CounterPeterson {
    static volatile boolean wantp = false, wantq = false;
    static volatile int last = 0;          // 1 = p was last, 2 = q was last
    static volatile long counter = 0;
    static final long ITERS = 200_000;

    static void petersonEnter(boolean isP) {
        if (isP) { wantp = true; last = 1; } else { wantq = true; last = 2; }
        while (isP ? (wantq && last == 1) : (wantp && last == 2)) {}
    }

    static void petersonExit(boolean isP) {
        if (isP) { wantp = false; } else { wantq = false; }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread p = new Thread(() -> {
            for (long k = 0; k < ITERS; k++) {
                petersonEnter(true);
                counter++;
                petersonExit(true);
            }
        });
        Thread q = new Thread(() -> {
            for (long k = 0; k < ITERS; k++) {
                petersonEnter(false);
                counter++;
                petersonExit(false);
            }
        });
        p.start();
        q.start();
        p.join();
        q.join();
        System.out.println("counter = " + counter + " (expected " + 2 * ITERS + ")");
    }
}
