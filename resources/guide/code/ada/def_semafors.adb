-- def_semafors.adb — body of the protected-object semaphore (guide 6.7).
package body Def_Semafors is

   protected body Semaphore is

      entry Wait when Count > 0 is
      begin
         Count := Count - 1;
      end Wait;

      procedure Signal is
      begin
         Count := Count + 1;
      end Signal;

      function Value return Natural is
      begin
         return Count;
      end Value;

   end Semaphore;

end Def_Semafors;
