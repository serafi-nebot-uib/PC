// PCMonitor.java — the bounded-buffer monitor with two condition variables
// (guide 6.9): notFull blocks producers when full, notEmpty blocks consumers
// when empty. Mesa discipline: wait in a while loop.
// Used by ProducerConsumerMonitor.java.
// Build: javac PCMonitor.java ProducerConsumerMonitor.java && java ProducerConsumerMonitor
public class PCMonitor {
    private final int[] buffer;
    private int count = 0, front = 0, rear = 0;

    public PCMonitor(int n) {
        buffer = new int[n];
    }

    public synchronized void deposit(int item) throws InterruptedException {
        while (count == buffer.length)
            wait();                                    // waitC(notFull)
        buffer[rear] = item;
        rear = (rear + 1) % buffer.length;
        count++;
        notifyAll();                                   // signalC(notEmpty)
    }

    public synchronized int fetch() throws InterruptedException {
        while (count == 0)
            wait();                                    // waitC(notEmpty)
        int item = buffer[front];
        front = (front + 1) % buffer.length;
        count--;
        notifyAll();                                   // signalC(notFull)
        return item;
    }
}
