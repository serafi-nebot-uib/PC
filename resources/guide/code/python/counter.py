# counter.py — the lost-update race on a shared counter, with the thread as a
# class (redefining run) and as a call to a function.
# The race exists despite the GIL: n = n + 1 is a read-modify-write. Note that
# since CPython 3.11 the interpreter only switches threads at loop back-edges
# and calls, so the bare loop often shows no loss; time.sleep(0) releases the
# GIL inside the read-modify-write and makes the interleaving observable.
# Build: python3 counter.py
# See guide/01-introduction.md, sections 1.6.2 and 1.12.
import threading
import time

n = 0
ITERS = 20_000
THREADS = 4


class CounterThread(threading.Thread):
    def run(self):
        global n
        for _ in range(ITERS):
            tmp = n
            time.sleep(0)
            n = tmp + 1


def count():
    global n
    for _ in range(ITERS):
        tmp = n
        time.sleep(0)
        n = tmp + 1


def main():
    class_threads = [CounterThread() for _ in range(THREADS // 2)]
    func_threads = [threading.Thread(target=count) for _ in range(THREADS // 2)]
    for t in class_threads + func_threads:
        t.start()
    for t in class_threads + func_threads:
        t.join()
    print(f"n = {n} (expected {THREADS * ITERS})")


if __name__ == "__main__":
    main()
