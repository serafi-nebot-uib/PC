from threading import Semaphore, Thread
from enum import Enum, auto
import time
import random
import os
import colors

DEBUG = int(os.getenv("DEBUG", "0"))

class Gender(Enum):
  MALE = auto()
  FEMALE = auto()


RESTROOM_CNT_MAX = 3
RESTROOM_TIMES = 2

def debug(msg):
  if DEBUG > 0: print(f"{colors.RED}[DEBUG]{colors.RESET} {msg}")

restroom = Semaphore(1)
inside = 0
inside_last = 0
inside_type = None

choosing = Semaphore(1)
ticket_last = 0

empty = Semaphore(0)
not_full = Semaphore(0)

def enter(name: str, t: int, gender: Gender):
  global inside, ticket_last, inside_last, inside_type
  prefix = " " * int(gender == Gender.FEMALE) * 8

  choosing.acquire()
  ticket_last += 1
  ticket = ticket_last
  choosing.release()
  debug(f"{name} ticket: {ticket}")

  nxt = False
  while not nxt:
    restroom.acquire()
    nxt = inside_last == ticket - 1
    restroom.release()
    if not nxt:
      debug(f"{name} next wait")
      not_full.acquire()

  while True:
    debug(f"{name} loop")
    restroom.acquire()
    if inside >= RESTROOM_CNT_MAX:
      restroom.release()
      debug(f"{name} inside full")
      not_full.acquire()
      restroom.acquire()

    if inside < RESTROOM_CNT_MAX:
      debug(f"{name} almost enter")
      if inside_type in (gender, None):
        debug(f"{name} inside")
        inside += 1
        inside_last = ticket
        inside_type = gender
        print(f"{prefix}{name} entra {t}/2. {"Homes" if gender == Gender.MALE else "Dones"} al bany: {inside}")
        restroom.release()
        break
      else:
        debug(f"{name} wrong sex")
        restroom.release()
        empty.acquire()

def exit(name: str, gender: Gender):
  global inside, inside_type
  prefix = " " * int(gender == Gender.FEMALE) * 8

  restroom.acquire()
  print(f"{prefix}{name} surt")
  inside -= 1
  not_full.release()
  if inside == 0:
    print("*** El bany està buit***")
    inside_type = None
    empty.release()
  restroom.release()


# lock = Semaphore(1)
# restroom_not_full = Semaphore(0)
# restroom_empty = Semaphore(0)
# restroom_cnt = 0
# restroom_type = None
#
# def restroom_enter(name: str, t: int, gender: Gender):
#   global restroom_cnt, restroom_type
#   prefix = " " * int(gender == Gender.FEMALE) * 8
#
#   while True:
#     lock.acquire()
#     if restroom_cnt >= RESTROOM_CNT_MAX:
#       lock.release()
#       restroom_not_full.acquire()
#       lock.acquire()
#
#     if restroom_cnt < RESTROOM_CNT_MAX:
#       if restroom_type in (gender, None):
#         restroom_cnt += 1
#         restroom_type = gender
#         print(f"{prefix}{name} entra {t}/2. {"Homes" if gender == Gender.MALE else "Dones"} al bany: {restroom_cnt}")
#         lock.release()
#         break
#       else:
#         lock.release()
#         restroom_empty.acquire()
#     else:
#       lock.release()
#
# def restroom_exit(name: str, gender: Gender):
#   global restroom_cnt, restroom_type
#   prefix = " " * int(gender == Gender.FEMALE) * 8
#
#   lock.acquire()
#   print(f"{prefix}{name} surt")
#   restroom_cnt -= 1
#   restroom_not_full.release()
#   if restroom_cnt == 0:
#     print("*** El bany està buit***")
#     restroom_type = None
#     restroom_empty.release() # signal to waiting processes that the restroom is empty
#   lock.release()

class Queue:
  def __init__(self):
    self.gate = Semaphore(0)
    self.lock = Semaphore(1)
    self.waiting = 0

  def wait(self):
    self.lock.acquire()
    self.waiting += 1
    self.lock.release()

    self.gate.acquire()

    self.lock.acquire()
    self.waiting -= 1
    self.lock.release()

def worker(name: str, gender: Gender):
  prefix = " " * int(gender == Gender.FEMALE) * 8

  time.sleep(random.randint(0, 1))
  print(f"{prefix}{name} arriba al despatx")

  time.sleep(random.randint(0, 1))
  print(f"{prefix}{name} treballa")

  for i in range(1, RESTROOM_TIMES + 1):
    time.sleep(random.randint(0, 1))
    enter(name, i, gender)

    time.sleep(random.randint(0, 1))
    exit(name, gender)

  time.sleep(random.randint(0, 1))
  print(f"{prefix}{name} acaba la feina")

if __name__ == "__main__":
  config = (("GORI", Gender.MALE), ("COSME", Gender.MALE), ("JAUME", Gender.MALE),
            ("DAMIA", Gender.MALE), ("ANTONI", Gender.MALE), ("BERNAT", Gender.MALE),
            ("AINA", Gender.FEMALE), ("GERONIA", Gender.FEMALE), ("CATALINA", Gender.FEMALE),
            ("ELISABET",Gender.FEMALE), ("JOANA", Gender.FEMALE), ("FRANCESCA", Gender.FEMALE))
  workers = [Thread(target=worker, args=args) for args in config]
  for w in workers: w.start()
  for w in workers: w.join()
