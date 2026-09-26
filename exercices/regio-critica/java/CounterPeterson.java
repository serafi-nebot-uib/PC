public class CounterPeterson implements Runnable {

    static final int THREADS = 2;
    static final int MAX_COUNT = 10000000;
    static volatile int[] want = new int[THREADS];
    static volatile int last = 0;
    static volatile int n = 0;
    int id;

    public CounterPeterson(int id) {
        this.id = id;
    }

    @Override
    public void run() {
        int max = MAX_COUNT / THREADS;
        int altreProces = (id + 1) % THREADS;
        System.out.println("Thread " + id);
        for (int i = 0; i < max; i++) {
            // Peterson
            want[id] = 1;
            last = id;
            while (want[altreProces] == 1 && last == id) {
            }
            // System.out.println("Index: " + i + " Contador " + n + " Fil: " + id + " Altre
            // procés: " + altreProces);
            n += 1; // SC
            want[id] = 0;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread[] threads = new Thread[THREADS];
        want[0] = 0;
        want[1] = 0;

        int i;
        for (i = 0; i < THREADS; i++) {
            threads[i] = new Thread(new CounterPeterson(i));
            threads[i].start();
        }
        for (i = 0; i < THREADS; i++) {
            threads[i].join();
        }
        float error = (MAX_COUNT - n) / (float) MAX_COUNT * 100;
        System.out.printf("Counter value: %d Expected: %d Error: %3.6f%%\n", n, MAX_COUNT, error);
    }
}
