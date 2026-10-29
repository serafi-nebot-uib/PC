// ReadersWritersMonitor.java — the readers-writers problem with Java's
// built-in monitor (guide 6.10). Java has no separate condition variables:
// one queue, so notifyAll wakes readers AND writers; each re-checks whether
// IT may proceed. Writers arriving while readers wait block new readers.
// Build: javac ReadersWritersMonitor.java && java ReadersWritersMonitor
public class ReadersWritersMonitor {
    private int readers = 0;
    private int writers = 0;      // 0 or 1 writing; >0 means one is writing
    private int waitingWriters = 0;

    public synchronized void startRead() throws InterruptedException {
        // a reader suspends if a process is writing or some writer waits
        while (writers > 0 || waitingWriters > 0)
            wait();
        readers++;
    }

    public synchronized void endRead() {
        readers--;
        if (readers == 0)
            notifyAll();          // last reader out: wake everyone (a writer may go)
    }

    public synchronized void startWrite() throws InterruptedException {
        waitingWriters++;
        while (readers > 0 || writers > 0)
            wait();
        waitingWriters--;
        writers++;
    }

    public synchronized void endWrite() {
        writers--;
        notifyAll();              // prefer whoever re-checks first (readers cascade in)
    }

    public static void main(String[] args) throws InterruptedException {
        ReadersWritersMonitor db = new ReadersWritersMonitor();
        Thread[] rs = new Thread[3];
        for (int i = 0; i < 3; i++) {
            rs[i] = new Thread(() -> {
                try {
                    db.startRead();
                    System.out.println(Thread.currentThread().getName() + " reading");
                    Thread.sleep(50);
                    db.endRead();
                } catch (InterruptedException ignored) {
                }
            }, "reader-" + i);
            rs[i].start();
        }
        Thread w = new Thread(() -> {
            try {
                Thread.sleep(10);
                db.startWrite();
                System.out.println("writer-0 writing");
                Thread.sleep(50);
                db.endWrite();
            } catch (InterruptedException ignored) {
            }
        }, "writer-0");
        w.start();
        for (int i = 0; i < 3; i++)
            rs[i].join();
        w.join();
        System.out.println("done");
    }
}
