with Text_Io;
use  Text_Io;

procedure Tasques is

   task type tasca is
      entry Start (Idx: in integer);
   end tasca;

   task body tasca is
      My_Idx : integer;
   begin
      accept Start (Idx: in Integer) do
         My_Idx := Idx;
      end Start;
      delay 0.5;
      Put_Line ("Hi!!, i'm task " &My_Idx'img);
   end tasca;

   THREADS : constant integer := 4;
   type tasques is array (1..THREADS) of tasca;
   t: tasques;

begin
     for Idx in 1..THREADS loop
        t(Idx).Start(Idx);
     end loop;
     --Put_Line ("Waiting!");
     --delay 0.5;
     --Put_Line ("Bye!");
end Tasques;
