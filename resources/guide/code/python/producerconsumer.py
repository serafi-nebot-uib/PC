# producerconsumer.py — bounded-buffer producer-consumer with split
# semaphores (guide 5.13): notEmpty + notFull = N; mutex acquired last.
# Build: python3 producerconsumer.py
import threading
import time
import random
from collections import deque

N = 5
CONSUMED = 20
buffer = deque()
mutex = threading.Semaphore(1)
notEmpty = threading.Semaphore(0)
notFull = threading.Semaphore(N)


def producer():
    for i in range(CONSUMED):
        item = f"item-{i}"
        time.sleep(random.random() * 0.01)
        notFull.acquire()
        with mutex:
            buffer.append(item)
            print(f"producer: deposited {item} (buffer={len(buffer)})")
        notEmpty.release()


def consumer():
    for _ in range(CONSUMED):
        notEmpty.acquire()
        with mutex:
            item = buffer.popleft()
        notFull.release()
        print(f"consumer: took {item}")
        time.sleep(random.random() * 0.01)


p = threading.Thread(target=producer)
c = threading.Thread(target=consumer)
p.start()
c.start()
p.join()
c.join()
print("done")
