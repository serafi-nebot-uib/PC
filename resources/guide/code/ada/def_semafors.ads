-- def_semafors.ads — a counting semaphore as an Ada protected type
-- (guide 6.7): entries have barriers (boolean guards re-evaluated by the
-- runtime); procedures run under mutual exclusion.
package Def_Semafors is

   protected type Semaphore (Initial : Natural) is
      entry Wait;          -- barrier: blocks while Count = 0
      procedure Signal;
      function Value return Natural;
   private
      Count : Integer := Initial;
   end Semaphore;

end Def_Semafors;
