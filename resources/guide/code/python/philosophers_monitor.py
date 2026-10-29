# philosophers_monitor.py — dining philosophers with a monitor (guide 6.11):
# fork[i] = number of free forks for philosopher i; takeForks waits for both;
# releaseForks updates neighbors and signals whose count returns to 2.
# Deadlock is structurally impossible: no one holds one fork while waiting.
# Build: python3 philosophers_monitor.py
import threading

N = 5
MEALS = 3

fork = [2] * N
mutex = threading.Lock()
ok_to_eat = [threading.Condition(mutex) for _ in range(N)]


def take_forks(i):
    with mutex:
        while fork[i] != 2:
            ok_to_eat[i].wait()
        fork[i] = 0
        fork[(i - 1) % N] -= 1
        fork[(i + 1) % N] -= 1


def release_forks(i):
    with mutex:
        fork[i] = 2
        fork[(i - 1) % N] += 1
        fork[(i + 1) % N] += 1
        if fork[(i - 1) % N] == 2:
            ok_to_eat[(i - 1) % N].notify()
        if fork[(i + 1) % N] == 2:
            ok_to_eat[(i + 1) % N].notify()


def philosopher(i):
    for meal in range(MEALS):
        print(f"phil{i}: thinking")
        take_forks(i)
        print(f"phil{i}: EATING (meal {meal + 1})")
        release_forks(i)
        print(f"phil{i}: put forks down")


threads = [threading.Thread(target=philosopher, args=(i,)) for i in range(N)]
for t in threads:
    t.start()
for t in threads:
    t.join()
print("done")
