# producerconsumerinfinite.py — producer-consumer with an infinite buffer
# (guide 5.12): notEmpty counts buffered items; a Lock covers the buffer
# manipulation (the atomicity the abstract algorithm assumes).
# Build: python3 producerconsumerinfinite.py
import threading
import time
import random

buffer = []
notEmpty = threading.Semaphore(0)
mutex = threading.Lock()
CONSUMED = 20


def producer():
    for i in range(CONSUMED):
        item = f"item-{i}"
        time.sleep(random.random() * 0.01)
        with mutex:
            buffer.append(item)
            print(f"producer: deposited {item} (buffer={len(buffer)})")
        notEmpty.release()


def consumer():
    for _ in range(CONSUMED):
        notEmpty.acquire()
        with mutex:
            item = buffer.pop(0)
        print(f"consumer: took {item}")
        time.sleep(random.random() * 0.01)


p = threading.Thread(target=producer)
c = threading.Thread(target=consumer)
p.start()
c.start()
p.join()
c.join()
print("done")
