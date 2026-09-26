// gocounter_swap.go — spinlock with atomic swap (atomic.SwapInt32),
// chapter 4.3.3: the old value of mutex is checked after the exchange.
// Build: go run gocounter_swap.go
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
	mutex  int32
	counter int64
)

func lockAcquire() {
	for atomic.SwapInt32(&mutex, 1) == 1 {
	}
}

func lockRelease() {
	atomic.StoreInt32(&mutex, 0)
}

func main() {
	var wg sync.WaitGroup
	wg.Add(threads)
	for i := 0; i < threads; i++ {
		go func() {
			defer wg.Done()
			for k := 0; k < iters; k++ {
				lockAcquire()
				counter++
				lockRelease()
			}
		}()
	}
	wg.Wait()
	fmt.Printf("counter = %d (expected %d)\n", atomic.LoadInt64(&counter), threads*iters)
}
