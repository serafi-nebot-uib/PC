-- def_monitor.adb — body of the readers-writers protected object
-- (guide 6.10). The entry barriers are the whole policy: StartRead is open
-- whenever nobody is writing; StartWrite only when nobody reads or writes.
package body Def_Monitor is

   protected body ReadersWriter is

      entry StartRead when not Writing is
      begin
         Readers := Readers + 1;
      end StartRead;

      procedure EndRead is
      begin
         Readers := Readers - 1;
      end EndRead;

      entry StartWrite when Readers = 0 and not Writing is
      begin
         Writing := True;
      end StartWrite;

      procedure EndWrite is
      begin
         Writing := False;
      end EndWrite;

   end ReadersWriter;

end Def_Monitor;
