# philosopherslr.py — dining philosophers, LR solution (guide 5.15.2):
# everyone requests the forks in ascending order; phil4 takes fork0 first,
# breaking the circular wait (no deadlock, though eating can serialize).
# Build: python3 philosopherslr.py
import threading

MEALS = 3
forks = [threading.Semaphore(1) for _ in range(5)]


def philosopher(i):
    left, right = i, (i + 1) % 5
    if i == 4:                       # the wrap-around philosopher:
        left, right = right, left    # request in the other order (fork0 first)
    for meal in range(MEALS):
        print(f"phil{i}: thinking")
        forks[left].acquire()
        print(f"phil{i}: took fork {left}")
        forks[right].acquire()
        print(f"phil{i}: took fork {right} -- EATING")
        forks[right].release()
        forks[left].release()
        print(f"phil{i}: put both forks down")


threads = [threading.Thread(target=philosopher, args=(i,)) for i in range(5)]
for t in threads:
    t.start()
for t in threads:
    t.join()
print("done")
