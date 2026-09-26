with Ada.Text_IO; use Ada.Text_IO;

procedure Tasques3 is

   task type Worker (Id : Positive);

   task body Worker is
   begin
      Put_Line ("Hi I'm the thread" & Positive'Image (Id));
   end Worker;

   W1 : Worker (Id => 1);
   W2 : Worker (Id => 2);
   W3 : Worker (Id => 3);
   W4 : Worker (Id => 4);

begin
   null; -- El programa espera automàticament que totes les tasques acabin
end Tasques3;