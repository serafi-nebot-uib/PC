from threading import Semaphore, Thread
from enum import Enum, auto
import time
import random
import os

DEBUG = int(os.getenv("DEBUG", "0"))

class Gender(Enum):
  MALE = auto()
  FEMALE = auto()

RESTROOM_CNT_MAX = 3
RESTROOM_TIMES = 2

lock = Semaphore(1)
restroom = Semaphore(RESTROOM_CNT_MAX)
restroom_empty = Semaphore(0)
restroom_cnt = 0
restroom_type = None

def restroom_enter(name: str, t: int, gender: Gender):
  global restroom_cnt, restroom_type
  prefix = " " * int(gender == Gender.FEMALE) * 8

  while True:
    lock.acquire()
    # check if current restroom type is of the same gender or is not set (None -> empty)
    if restroom_type in (gender, None):
      lock.release()
      restroom.acquire()

      lock.acquire()
      restroom_cnt += 1
      restroom_type = gender
      print(f"{prefix}{name} entra {t}/2. {"Homes" if gender == Gender.MALE else "Dones"} al bany: {restroom_cnt}")
      lock.release()
      break

    # current restroom type is not of the same gender -> wait until it is empty and check again next iteration
    lock.release()
    restroom_empty.acquire()

def restroom_exit(name: str, gender: Gender):
  global restroom_cnt, restroom_type
  prefix = " " * int(gender == Gender.FEMALE) * 8

  lock.acquire()
  print(f"{prefix}{name} surt")
  restroom_cnt -= 1
  if restroom_cnt == 0:
    print("*** El bany està buit***")
    restroom_type = None
    restroom_empty.release() # signal to waiting processes that the restroom is empty

  restroom.release()
  lock.release()

def worker(name: str, gender: Gender):
  prefix = " " * int(gender == Gender.FEMALE) * 8

  time.sleep(random.randint(0, 1))
  print(f"{prefix}{name} arriba al despatx")

  time.sleep(random.randint(0, 1))
  print(f"{prefix}{name} treballa")

  for i in range(1, RESTROOM_TIMES + 1):
    time.sleep(random.randint(0, 1))
    restroom_enter(name, i, gender)

    time.sleep(random.randint(0, 1))
    restroom_exit(name, gender)

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
