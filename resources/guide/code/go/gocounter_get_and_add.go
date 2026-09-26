// gocounter_get_and_add.go — ticket lock with get&add (atomic.AddUint32),
// chapter 4.3.4: the bakery algorithm with an atomic ticket dispenser.
// Build: go run gocounter_get_and_add.go
package main

import (
	"fmt"
	"sync"
	"sync/atomic"
)

const (
	threads = 4
	iters   = 100000
)

var (
	nextTicket uint32
	turn       uint32
	counter    int64
)

func ticketLock() uint32 {
	my := atomic.AddUint32(&nextTicket, 1) - 1
	for atomic.LoadUint32(&turn) != my {
	}
	return my
}

func ticketUnlock() {
	atomic.AddUint32(&turn, 1)
}

func main() {
	var wg sync.WaitGroup
	wg.Add(threads)
	for i := 0; i < threads; i++ {
		go func() {
			defer wg.Done()
			for k := 0; k < iters; k++ {
				ticketLock()
				counter++
				ticketUnlock()
			}
		}()
	}
	wg.Wait()
	fmt.Printf("counter = %d (expected %d)\n", atomic.LoadInt64(&counter), threads*iters)
}
