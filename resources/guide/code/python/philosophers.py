# philosophers.py — dining philosophers, Tanenbaum's solution (guide 5.15.3):
# synchronize on the philosophers' states (THINKING/HUNGRY/EATING), one
# semaphore per philosopher for the "you may eat" notification, and a mutex
# around every status check/modification.
# Build: python3 philosophers.py
import threading

N = 5
MEALS = 3
THINKING, HUNGRY, EATING = 0, 1, 2

status = [THINKING] * N
sync = [threading.Semaphore(0) for _ in range(N)]
mutex = threading.Semaphore(1)


def left(i):
    return (i - 1) % N


def right(i):
    return (i + 1) % N


def can_eat(i):
    # called only while holding mutex
    if status[i] == HUNGRY and status[left(i)] != EATING and status[right(i)] != EATING:
        status[i] = EATING
        sync[i].release()


def take_forks(i):
    mutex.acquire()
    status[i] = HUNGRY
    can_eat(i)
    mutex.release()
    sync[i].acquire()      # block here unless can_eat already let us through


def put_forks(i):
    mutex.acquire()
    status[i] = THINKING
    can_eat(left(i))
    can_eat(right(i))
    mutex.release()


def philosopher(i):
    for meal in range(MEALS):
        print(f"phil{i}: thinking")
        take_forks(i)
        print(f"phil{i}: EATING (meal {meal + 1})")
        put_forks(i)
        print(f"phil{i}: put forks down")


threads = [threading.Thread(target=philosopher, args=(i,)) for i in range(N)]
for t in threads:
    t.start()
for t in threads:
    t.join()
print("done")
