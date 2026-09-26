# threads.py — two ways to create threads: subclassing Thread (redefine run)
# or creating a Thread that calls a function. start() launches, join() waits.
# Build: python3 threads.py
# See guide/01-introduction.md, section 1.6.2.
import threading


class MyThread(threading.Thread):
    def run(self):
        for i in range(5):
            print(f"subclass thread: step {i}")


def task():
    for i in range(5):
        print(f"function thread: step {i}")


def main():
    t1 = MyThread()
    t2 = threading.Thread(target=task)
    t1.start()
    t2.start()
    t1.join()
    t2.join()
    print("main: both threads finished")


if __name__ == "__main__":
    main()
