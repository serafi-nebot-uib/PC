-- counter.adb — the lost-update race on a shared counter in Ada.
-- pragma Atomic makes each access atomic, but N := N + 1 is still a
-- read-modify-write of two steps: updates can be lost.
-- Build: gnatmake counter.adb && ./counter
-- See guide/01-introduction.md, section 1.12.
with Ada.Text_IO; use Ada.Text_IO;

procedure Counter is
   N : Integer := 0;
   pragma Atomic (N);

   ITERS   : constant := 100_000;
   THREADS : constant := 4;

   task type Worker;
   task body Worker is
   begin
      for I in 1 .. ITERS loop
         N := N + 1;
      end loop;
   end Worker;

   W : array (1 .. THREADS) of Worker;
begin
   null;
   Put_Line ("n =" & Integer'Image (N)
             & " (expected" & Integer'Image (ITERS * THREADS) & ")");
end Counter;
