// Threads.java — two ways to create threads: extending Thread vs implementing
// Runnable (recommended: Java has no multiple inheritance).
// Build: javac Threads.java && java Threads
// See guide/01-introduction.md, section 1.6.1.

class MyThread extends Thread {
    @Override
    public void run() {
        for (int i = 0; i < 5; i++)
            System.out.println("subclass thread: step " + i);
    }
}

class MyTask implements Runnable {
    @Override
    public void run() {
        for (int i = 0; i < 5; i++)
            System.out.println("runnable thread: step " + i);
    }
}

public class Threads {
    public static void main(String[] args) throws InterruptedException {
        Thread t1 = new MyThread();
        Thread t2 = new Thread(new MyTask());
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        System.out.println("main: both threads finished");
    }
}
