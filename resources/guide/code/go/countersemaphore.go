// countersemaphore.go — a counting semaphore in Go: a buffered channel of
// capacity k (the standard-library-free idiom); golang.org/x/sync/semaphore
// is the weighted-library equivalent. Used here as a mutex (k = 1).
// Build: go run countersemaphore.go
package main

import (
	"fmt"
	"sync"
)

const (
	threads = 4
	iters   = 100000
)

func main() {
	mutex := make(chan struct{}, 1) // semaphore of value 1
	var wg sync.WaitGroup
	counter := 0
	wg.Add(threads)
	for i := 0; i < threads; i++ {
		go func() {
			defer wg.Done()
			for k := 0; k < iters; k++ {
				mutex <- struct{}{} // wait
				counter++
				<-mutex // signal
			}
		}()
	}
	wg.Wait()
	fmt.Printf("counter = %d (expected %d)\n", counter, threads*iters)
}
