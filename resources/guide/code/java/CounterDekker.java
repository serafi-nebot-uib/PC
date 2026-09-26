// CounterDekker.java — Dekker's algorithm (chapter 2.7) protecting a counter.
// All shared variables are scalar volatiles: volatile on an ARRAY only covers
// the reference, not the elements, so wantp/wantq must be separate fields.
// Build: javac CounterDekker.java && java CounterDekker
public class CounterDekker {
    static volatile boolean wantp = false, wantq = false;
    static volatile int turn = 0;          // 0 = p favored, 1 = q favored
    static volatile long counter = 0;
    static final long ITERS = 200_000;

    static void dekkerEnter(boolean isP) {
        if (isP) { wantp = true; } else { wantq = true; }
        while (isP ? wantq : wantp) {
            if (turn != (isP ? 0 : 1)) {
                if (isP) { wantp = false; } else { wantq = false; }
                while (turn != (isP ? 0 : 1)) {}
                if (isP) { wantp = true; } else { wantq = true; }
            }
        }
    }

    static void dekkerExit(boolean isP) {
        if (isP) { wantp = false; turn = 1; } else { wantq = false; turn = 0; }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread p = new Thread(() -> {
            for (long k = 0; k < ITERS; k++) {
                dekkerEnter(true);
                counter++;
                dekkerExit(true);
            }
        });
        Thread q = new Thread(() -> {
            for (long k = 0; k < ITERS; k++) {
                dekkerEnter(false);
                counter++;
                dekkerExit(false);
            }
        });
        p.start();
        q.start();
        p.join();
        q.join();
        System.out.println("counter = " + counter + " (expected " + 2 * ITERS + ")");
    }
}
