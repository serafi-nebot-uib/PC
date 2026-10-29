-- readerswriters.adb — a runnable demo of the readers-writers protected
-- object: three readers read a counter, two writers increment it
-- (guide 6.10). The inner declare-block waits for the tasks before the
-- final print.
-- Build: gnatmake -q readerswriters.adb && ./readerswriters
with Ada.Text_IO; use Ada.Text_IO;
with Def_Monitor; use Def_Monitor;

procedure ReadersWriters is

   DB : ReadersWriter;
   Value : Natural := 0;

   task type Reader (Id : Natural);

   task body Reader is
   begin
      for I in 1 .. 3 loop
         DB.StartRead;
         Put_Line ("reader" & Natural'Image (Id) & ": read value ="
                   & Natural'Image (Value));
         DB.EndRead;
      end loop;
   end Reader;

   task type Writer (Id : Natural);

   task body Writer is
   begin
      for I in 1 .. 2 loop
         DB.StartWrite;
         Value := Value + 1;
         Put_Line ("writer" & Natural'Image (Id) & ": wrote value ="
                   & Natural'Image (Value));
         DB.EndWrite;
      end loop;
   end Writer;

begin
   declare
      R1 : Reader (Id => 1);
      R2 : Reader (Id => 2);
      R3 : Reader (Id => 3);
      W1 : Writer (Id => 1);
      W2 : Writer (Id => 2);
   begin
      null;
   end;   -- all readers and writers finish here
   Put_Line ("final value =" & Natural'Image (Value) & " (expected 4)");
end ReadersWriters;
