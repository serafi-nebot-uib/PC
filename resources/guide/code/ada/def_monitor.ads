-- def_monitor.ads — the readers-writers problem as an Ada protected object
-- (guide 6.10): entries with barriers express the access policy directly.
-- Readers exclude writers (not other readers); writers exclude everyone.
package Def_Monitor is

   protected type ReadersWriter is
      entry StartRead;
      procedure EndRead;
      entry StartWrite;
      procedure EndWrite;
   private
      Readers : Natural := 0;
      Writing : Boolean := False;
   end ReadersWriter;

end Def_Monitor;
