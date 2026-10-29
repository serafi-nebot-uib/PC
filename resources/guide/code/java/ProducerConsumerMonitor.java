// ProducerConsumerMonitor.java — producers and consumers on the bounded-
// buffer monitor of PCMonitor.java (guide 6.9). notifyAll wakes the whole
// mixed queue; each woken thread re-checks whether IT can continue.
// Build: javac PCMonitor.java ProducerConsumerMonitor.java && java ProducerConsumerMonitor
public class ProducerConsumerMonitor {
    static final int BUFFER = 3;
    static final int ITEMS = 12;

    public static void main(String[] args) throws InterruptedException {
        PCMonitor buffer = new PCMonitor(BUFFER);

        Thread producer = new Thread(() -> {
            for (int i = 0; i < ITEMS; i++) {
                try {
                    buffer.deposit(i);
                    System.out.println("producer: deposited " + i);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });

        Thread consumer = new Thread(() -> {
            for (int i = 0; i < ITEMS; i++) {
                try {
                    int item = buffer.fetch();
                    System.out.println("consumer: took " + item);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();
        System.out.println("done");
    }
}
