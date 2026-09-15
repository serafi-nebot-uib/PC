# Multi-language concurrency assignments
# Usage: make [c|go|java|py|ada|all|clean]
# Each language directory gets its own <lang>/build/ for artifacts (gitignored)

all: c go java py

# C (pthreads)
c: c/build/threads
	c/build/threads

c/build/threads: c/threads.c | c/build
	$(CC) -Wall -pthread -o $@ $<

# Go (go run compiles to a cache, nothing lands in build/)
go:
	go run go/threads/*.go

# Java: class declares package pkg237_threads, so run via package path
java: java/build/pkg237_threads/Threads.class
	java -cp java/build pkg237_threads.Threads

java/build/pkg237_threads/Threads.class: java/Threads.java | java/build
	javac -d java/build $<

# Python
py:
	python3 python/threads.py

# Ada: compile from ada/build/ so artifacts (.o/.ali/executable) land there
ada: ada/build/tasques ada/build/tasques3
	ada/build/tasques
	ada/build/tasques3

ada/build/tasques: ada/tasques.adb | ada/build
	cd ada/build && gnatmake ../tasques

ada/build/tasques3: ada/tasques3.adb | ada/build
	cd ada/build && gnatmake ../tasques3

c/build java/build ada/build:
	mkdir -p $@

clean:
	rm -rf c/build java/build ada/build

.PHONY: all c go java py ada clean
