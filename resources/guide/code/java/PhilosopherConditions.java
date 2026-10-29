// PhilosopherConditions.java — dining philosophers with an explicit
// ReentrantLock and one Condition per philosopher (guide 6.11): the
// java.util.concurrent.locks equivalent of the monitor version — multiple
// wait-sets per lock, await()/signal() like waitC/signalC. Mesa: while.
// Build: javac PhilosopherConditions.java && java PhilosopherConditions
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class PhilosopherConditions {
    static final int N = 5;
    static final int MEALS = 3;

    static final ReentrantLock lock = new ReentrantLock();
    static final Condition[] okToEat = new Condition[N];
    static final int[] fork = {2, 2, 2, 2, 2};   // free forks per philosopher

    static {
        for (int i = 0; i < N; i++)
            okToEat[i] = lock.newCondition();
    }

    static void takeForks(int i) throws InterruptedException {
        lock.lock();
        try {
            while (fork[i] != 2)
                okToEat[i].await();
            fork[i] = 0;
            fork[(i + N - 1) % N]--;
            fork[(i + 1) % N]--;
        } finally {
            lock.unlock();
        }
    }

    static void releaseForks(int i) {
        lock.lock();
        try {
            fork[i] = 2;
            fork[(i + N - 1) % N]++;
            fork[(i + 1) % N]++;
            if (fork[(i + N - 1) % N] == 2)
                okToEat[(i + N - 1) % N].signal();
            if (fork[(i + 1) % N] == 2)
                okToEat[(i + 1) % N].signal();
        } finally {
            lock.unlock();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Thread[] t = new Thread[N];
        for (int i = 0; i < N; i++) {
            final int id = i;
            t[i] = new Thread(() -> {
                for (int meal = 0; meal < MEALS; meal++) {
                    System.out.println("phil" + id + ": thinking");
                    try {
                        takeForks(id);
                        System.out.println("phil" + id + ": EATING (meal " + (meal + 1) + ")");
                        releaseForks(id);
                        System.out.println("phil" + id + ": put forks down");
                    } catch (InterruptedException ignored) {
                        return;
                    }
                }
            });
            t[i].start();
        }
        for (int i = 0; i < N; i++)
            t[i].join();
        System.out.println("done");
    }
}
