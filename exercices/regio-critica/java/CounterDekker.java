/**
 *
 * @author miquelmascarooliver
 */
public class CounterDekker implements Runnable {
    // public class CounterDekker extends Thread {

    static final int THREADS = 2;
    static final int MAX_COUNT = 10000000;
    static volatile int estat[] = new int[THREADS];
    static volatile int torn = 0;
    static volatile int n = 0;
    int id;

    public CounterDekker(int id) {
        this.id = id;
    }

    // public synchronized void espera(int altreProces) {
    // while (estat[altreProces] == 1) {
    // if (torn == altreProces) {
    // estat[id] = 0;
    // while (torn == altreProces) {
    // }
    // estat[id] = 1;
    // }
    // }
    // }

    @Override
    public void run() {
        int max = MAX_COUNT / THREADS;
        int altreProces = (id + 1) % THREADS;
        System.out.println("Thread " + id);

        for (int i = 0; i < max; i++) {
            // Dekker
            estat[id] = 1;
            // espera(altreProces);
            while (estat[altreProces] == 1) {
                if (torn == altreProces) {
                    estat[id] = 0;
                    while (torn == altreProces) {
                    }
                    estat[id] = 1;
                }
            }
            n += 1; // SC
            torn = altreProces;
            estat[id] = 0;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread[] threads = new Thread[THREADS];
        estat[0] = 0;
        estat[1] = 0;

        int i;
        for (i = 0; i < THREADS; i++) {
            threads[i] = new Thread(new CounterDekker(i));
            threads[i].start();
        }
        for (i = 0; i < THREADS; i++) {
            threads[i].join();
        }
        float error = (MAX_COUNT - n) / (float) MAX_COUNT * 100;
        System.out.printf("Counter value: %d Expected: %d Error: %3.6f%%\n", n, MAX_COUNT, error);
    }
}
