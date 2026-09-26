-- tasques.adb — Ada tasks: specification + body; activation at the `begin`
-- of the enclosing unit; the enclosing subprogram waits for its tasks.
-- Build: gnatmake tasques.adb && ./tasques
-- See guide/01-introduction.md, section 1.6.5.
with Ada.Text_IO; use Ada.Text_IO;

procedure Tasques is
   task T1;
   task T2;

   task body T1 is
   begin
      for I in 1 .. 5 loop
         Put_Line ("task 1: step" & Integer'Image (I));
      end loop;
   end T1;

   task body T2 is
   begin
      for I in 1 .. 5 loop
         Put_Line ("task 2: step" & Integer'Image (I));
      end loop;
   end T2;
begin
   Put_Line ("main: tasks activated");
end Tasques;
