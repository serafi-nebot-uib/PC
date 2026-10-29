# bearhoneypot.py — the bear, the honey pot and the bees with semaphores
# (guide 5.16): the last bee wakes the bear and keeps the bees' gate closed
# until the bear has eaten and reopens it.
# Build: python3 bearhoneypot.py
import threading
import time

BEES = 5
H = 3          # pot capacity
CYCLES = 3     # how many times the pot is emptied

portions = 0
deposited = 0
mutex = threading.Semaphore(1)
pot_full = threading.Semaphore(0)
pot_empty = threading.Semaphore(1)


def bee(i):
    global portions, deposited
    while True:
        pot_empty.acquire()          # pass the gate (closes it behind)
        mutex.acquire()
        if deposited == CYCLES * H:  # mission accomplished
            mutex.release()
            pot_empty.release()
            return
        portions += 1
        deposited += 1
        print(f"bee {i}: deposited ({portions}/{H})")
        last = portions == H
        if not last:
            pot_empty.release()      # reopen for the next bee
        mutex.release()
        if last:
            print(f"bee {i}: pot FULL, waking the bear")
            pot_full.release()       # wake the bear; keep the gate closed


def bear():
    global portions
    for cycle in range(CYCLES):
        pot_full.acquire()
        print(f"bear: pot full, EATING (cycle {cycle + 1})")
        time.sleep(0.2)
        mutex.acquire()
        portions = 0
        mutex.release()
        pot_empty.release()          # reopen the gate
        print("bear: pot empty, going to sleep")


b = threading.Thread(target=bear)
b.start()
bees = [threading.Thread(target=bee, args=(i,)) for i in range(BEES)]
for t in bees:
    t.start()
for t in bees:
    t.join()
print("bees done")
