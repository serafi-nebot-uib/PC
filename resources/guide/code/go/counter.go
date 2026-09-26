// counter.go — the lost-update race on a shared counter.
// Build: go run counter.go      (go run -race counter.go detects the race)
// See guide/01-introduction.md, section 1.12.
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
				counter++
			}
		}()
	}
	wg.Wait()
	fmt.Printf("counter = %d (expected %d)\n", atomic.LoadInt64(&counter), threads*iters)
}
