4. Escriviu un algorisme que implementi l'algorisme de Manna-Pnueli. Sabent que la sentència if no és atòmica comprovau que l'escenari adjunt demostra que l'algorisme no és correcte per no satisfer l'exclusió mútua.

El problema és que la sentència "if wantp/q = -1" no és atòmica. Segurament a la majoria de les arquitectures fan falta, com a mínim, dues operacions. Per exemple:
    1. obtenir el valor de wantp/q
    2. comprovar el valor de wantp/q amb -1
El problema sorgeix perque no es garanteix que entre aquestes dues operacions el fil cedesqui l'execució a l'altre fil i modifiqui la variable wantp/q abans de que la segona operació es realitzi. Això pot provocar que :
