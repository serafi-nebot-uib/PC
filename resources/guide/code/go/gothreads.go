// gothreads.go — goroutines: independent executions launched by `go`,
// multiplexed onto OS threads by the Go runtime; sync.WaitGroup to wait.
// Build: go run gothreads.go
// See guide/01-introduction.md, section 1.6.4.
package main

import (
	"fmt"
	"sync"
)

func worker(name string, steps int, wg *sync.WaitGroup) {
	defer wg.Done()
	for i := 0; i < steps; i++ {
		fmt.Println(name, ": step", i)
	}
}

func main() {
	var wg sync.WaitGroup
	wg.Add(2)
	go worker("goroutine 1", 5, &wg)
	go worker("goroutine 2", 5, &wg)
	wg.Wait()
	fmt.Println("main: both goroutines finished")
}
