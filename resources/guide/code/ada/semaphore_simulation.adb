-- semaphore_simulation.adb — a runnable simulation of the protected-object
-- semaphore: four tasks each increment a counter 100_000 times inside a
-- critical section protected by the semaphore (guide 6.7). The inner
-- declare-block waits for the workers before the final print.
-- Build: gnatmake -q semaphore_simulation.adb && ./semaphore_simulation
with Ada.Text_IO; use Ada.Text_IO;
with Def_Semafors; use Def_Semafors;

procedure Semaphore_Simulation is

   Mutex : Semaphore (Initial => 1);
   Counter : Natural := 0;
   Iters : constant := 100_000;

   task type Worker;

   task body Worker is
   begin
      for I in 1 .. Iters loop
         Mutex.Wait;
         Counter := Counter + 1;
         Mutex.Signal;
      end loop;
   end Worker;

begin
   declare
      W : array (1 .. 4) of Worker;
   begin
      null;
   end;   -- the four workers finish here
   Put_Line ("counter =" & Natural'Image (Counter)
             & " (expected" & Natural'Image (4 * Iters) & ")");
end Semaphore_Simulation;
