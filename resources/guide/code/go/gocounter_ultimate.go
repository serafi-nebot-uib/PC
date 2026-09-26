// gocounter_ultimate.go — no lock at all: the increment itself is the atomic
// read-modify-write operation (chapter 4.5).
// Build: go run gocounter_ultimate.go
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

var counter int64

func main() {
	var wg sync.WaitGroup
	wg.Add(threads)
	for i := 0; i < threads; i++ {
		go func() {
			defer wg.Done()
			for k := 0; k < iters; k++ {
				atomic.AddInt64(&counter, 1)
			}
		}()
	}
	wg.Wait()
	fmt.Printf("counter = %d (expected %d)\n", atomic.LoadInt64(&counter), threads*iters)
}
