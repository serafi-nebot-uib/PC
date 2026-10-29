# producerconsumermonitor.py — bounded-buffer producer-consumer with a
# monitor: threading.Condition gives two explicit condition variables,
# notFull (blocks producers when full) and notEmpty (blocks consumers when
# empty) (guide 6.9).
# Build: python3 producerconsumermonitor.py
import threading
import time
import random
from collections import deque

N = 3
ITEMS = 12


class BoundedBuffer:
    def __init__(self, n):
        self.buffer = deque()
        self.n = n
        self.mutex = threading.Lock()
        self.not_full = threading.Condition(self.mutex)
        self.not_empty = threading.Condition(self.mutex)

    def deposit(self, item):
        with self.not_full:               # takes the monitor (mutex)
            while len(self.buffer) == self.n:
                self.not_full.wait()      # waitC(notFull)
            self.buffer.append(item)
            self.not_empty.notify()       # signalC(notEmpty)

    def fetch(self):
        with self.not_empty:
            while len(self.buffer) == 0:
                self.not_empty.wait()     # waitC(notEmpty)
            item = self.buffer.popleft()
            self.not_full.notify()        # signalC(notFull)
            return item


buffer = BoundedBuffer(N)


def producer():
    for i in range(ITEMS):
        time.sleep(random.random() * 0.01)
        buffer.deposit(i)
        print(f"producer: deposited {i}")


def consumer():
    for _ in range(ITEMS):
        item = buffer.fetch()
        print(f"consumer: took {item}")
        time.sleep(random.random() * 0.01)


p = threading.Thread(target=producer)
c = threading.Thread(target=consumer)
p.start()
c.start()
p.join()
c.join()
print("done")
