# readerswriters.py — the readers-writers problem with a monitor
# (guide 6.10): readers exclude writers (not other readers); writers exclude
# everyone. Waiting writers block new readers; EndWrite prefers readers.
# Readers read a shared counter, writers increment it.
# Build: python3 readerswriters.py
import threading
import time
import random

value = 0
readers = 0
writers = 0
waiting_writers = 0
mutex = threading.Lock()
ok_to_read = threading.Condition(mutex)
ok_to_write = threading.Condition(mutex)


def start_read():
    global readers
    with mutex:
        # suspend while writing, or while a writer waits (writer priority
        # over newly arriving readers)
        while writers > 0 or waiting_writers > 0:
            ok_to_read.wait()
        readers += 1
        ok_to_read.notify()     # cascade: let the next waiting reader in


def end_read():
    global readers
    with mutex:
        readers -= 1
        if readers == 0:
            ok_to_write.notify()   # last reader out wakes one writer


def start_write():
    global writers, waiting_writers
    with mutex:
        waiting_writers += 1
        while readers > 0 or writers > 0:
            ok_to_write.wait()
        waiting_writers -= 1
        writers += 1


def end_write():
    global writers
    with mutex:
        writers -= 1
        # Prefer waking readers (they cascade in); but a Condition cannot
        # test whether its queue is empty (the Hoare monitor's
        # "not empty(OKtoRead)" test), and waking only ok_to_read would
        # strand a second waiting writer forever. Notify both: wrong
        # wakeups just re-check their condition and wait again.
        ok_to_read.notify()
        ok_to_write.notify()


def reader(i):
    for _ in range(3):
        start_read()
        print(f"reader {i}: read value = {value}")
        time.sleep(random.random() * 0.05)
        end_read()


def writer(i):
    for _ in range(2):
        time.sleep(random.random() * 0.05)
        start_write()
        globals()['value'] = value + 1
        print(f"writer {i}: wrote value = {value}")
        end_write()


rs = [threading.Thread(target=reader, args=(i,)) for i in range(3)]
ws = [threading.Thread(target=writer, args=(i,)) for i in range(2)]
for t in rs + ws:
    t.start()
for t in rs + ws:
    t.join()
print(f"done (final value = {value})")
