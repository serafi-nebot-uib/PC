# bearhoneypot_monitor.py — the bear, the honey pot and the bees with a
# monitor (guide 6.12): the same policy as the semaphore version, with the
# state transitions inside protected methods.
# Build: python3 bearhoneypot_monitor.py
import threading
import time

BEES = 5
H = 3
CYCLES = 3


class HoneyPot:
    def __init__(self, capacity):
        self.capacity = capacity
        self.portions = 0
        self.mutex = threading.Lock()
        self.pot_full = threading.Condition(self.mutex)
        self.pot_empty = threading.Condition(self.mutex)

    def fill_one(self, bee):
        with self.mutex:
            while self.portions == self.capacity:
                self.pot_empty.wait()          # pot full: don't disturb the bear
            self.portions += 1
            print(f"bee {bee}: deposited ({self.portions}/{self.capacity})")
            if self.portions == self.capacity:
                print(f"bee {bee}: pot FULL, waking the bear")
                self.pot_full.notify()         # last portion wakes the bear

    def eat_all(self):
        with self.mutex:
            while self.portions < self.capacity:
                self.pot_full.wait()
            time.sleep(0.2)                    # om nom nom
            print("bear: ate everything, going to sleep")
            self.portions = 0
            self.pot_empty.notify()            # reopen for the bees


pot = HoneyPot(H)


def bear():
    for _ in range(CYCLES):
        pot.eat_all()


def bee(i):
    while True:
        pot.fill_one(i)


b = threading.Thread(target=bear)
b.start()
bees = [threading.Thread(target=bee, args=(i,), daemon=True) for i in range(BEES)]
for t in bees:
    t.start()
time.sleep(2)
print("bees done")
