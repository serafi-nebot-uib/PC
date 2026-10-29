# countersemaphore.py — mutual exclusion with threading.Semaphore(1)
# (guide 5.8-5.9).
# Build: python3 countersemaphore.py
import threading

n = 0
ITERS = 100_000
THREADS = 4
mutex = threading.Semaphore(1)


def count():
    global n
    for _ in range(ITERS):
        mutex.acquire()
        n = n + 1
        mutex.release()


threads = [threading.Thread(target=count) for _ in range(THREADS)]
for t in threads:
    t.start()
for t in threads:
    t.join()
print(f"n = {n} (expected {THREADS * ITERS})")
