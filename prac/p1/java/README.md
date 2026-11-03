# PR1 — El problema del bany mixt amb semàfors

21720 Programació Concurrent · Curs 2026-2027

## Com executar-ho

```bash
javac -encoding UTF-8 -d out src/BanyMixt.java
java -ea -cp out BanyMixt        # -ea activa les asseveracions internes (recomanat)
java -cp out BanyMixt 500        # espera màxima de 500 ms (per defecte 1000)
java -cp out BanyMixt 0          # sense esperes: màxima pressió d'entrellaçats
```

Durada aproximada amb la configuració per defecte: ~13 s. Cada execució
acaba perquè els 12 processos (6 homes + 6 dones) fan exactament 2 visites
al bany; `main` fa `join()` de tots els fils, de manera que un bloqueig es
manifestaria com una execució que no acaba.

## Disseny

Eines de sincronització: **només semàfors** (comptadors i binaris), tots
creats amb `fairness = true` (cues FIFO):

| Semàfor | Tipus | Inici | Funció |
|---|---|---|---|
| `torn` | binari | 1 | Torn d'entrada: només un procés decidint a la vegada |
| `buit` | binari | 1 | El bany és lliure de grup; l'agafa el 1r del grup i el deixa l'últim |
| `places` | comptador | 3 | Màxim 3 persones dins alhora (compartit: cap procés esperant per formar el grup següent el reté) |
| `mtx` | binari | 1 | Protegeix `tipus`, `nGrup` i `nDins` |

Estat compartit (sempre s'hi accedeix dins de `mtx`):

- `tipus` — sexe que té el bany assignat (`null` = bany buit, cap grup viu).
  És la variable que manté l'exclusió de sexes i identifica el grup viu.
- `nGrup` — processos del grup viu que han passat el torn (dins del bany o
  esperant una plaça).
- `nDins` — persones dins del bany ara mateix. Com que mai es barregen els
  sexes, totes són del sexe `tipus`: aquest únic comptador dins dona
  directament el valor dels missatges "Homes/Dones al bany: N".

Estructura de cada procés (`Persona`): arriba al despatx → treballa →
*(entra → surt → treballa) ×2* → acaba la feina.

### Protocol d'entrada

1. `torn.acquire()` — agafo el torn: només jo decido.
2. Sota `mtx`: si `tipus != sexe` (bany buit o de l'altre sexe) → sóc el
   **primer del grup següent**: allibero `mtx` i faig `buit.acquire()`.
   Si l'altre sexe encara és dins, bloquejo **retenció del torn**; quan
   arriba el meu torn, assigno `tipus = sexe` i `nGrup = 1`.
   Si `tipus == sexe` → m'afegeixo al grup viu: `nGrup++`.
3. `torn.release()` — ja tinc el lloc al grup garantit; deixo passar el
   següent.
4. `places.acquire()` — espero una de les 3 places.
5. Sota `mtx`: `nDins++` i imprimeixo `entra i/2. Homes|Dones al bany: N`.

### Protocol de sortida

1. Sota `mtx`: `nDins--`, `nGrup--`, imprimeixo `surt`; si `nGrup == 0`
   sóc l'últim del grup → `tipus = null`.
2. `places.release()` — lliuro la plaça.
3. Si sóc l'últim del grup: imprimeixo `*** El bany està buit***` i
   `buit.release()` — el bany queda disponible per a l'altre sexe.

### Per què no hi ha inanició (la clau de la solució)

La propietat fonamental exigida és: **si D espera, cap H arribat després
(H7) pot entrar abans que ella**. Es compleix *estructuralment*,
independentment de l'ordre de les cues:

- Mentre D6 bloqueja a `buit.acquire()` (pas 2), **retén el torn**: cap
  procés nou pot demanar torn, ni per unir-se al grup dins ni per formar el
  grup següent. Els H queden aturats al semàfor `torn`, darrere seu.
- Quan l'últim H del grup surt fa `buit.release()` → D6 és la següent a
  entrar i, mentre el seu grup visqui, els H nous tornen a quedar-se
  retinguts al torn.
- Els grups, per tant, s'alternen H→D→H→D… sempre que hi hagi gent pendent
  dels dos sexes: ningú espera infinit.

A més, la retenció del torn resol els exemples de l'enunciat:

- `H D H H`: H1 forma grup; D espera retinguda el torn (o el pren just
  abans, si hi arriba abans); H2, si aconsegueix passar el torn abans que
  D el retengui, s'afegeix al grup d'H1 i entra; D espera el bany buit;
  H3 queda darrere de D. → seqüència H1, H2, D, H3, tal com diu la figura 2.
- `H D H H H H`: H2 i H3 (i H4 si arriba a temps) s'afegeixen al grup d'H1;
  el 4rt del grup espera una plaça al multiplex `places` (màxim 3 dins);
  D espera retinguda el torn; el 5è H espera darrere de D. → exactament la
  figura 3.
- Exemple del fòrum (`[H3 H2 H1]`, després H4, H5, D6, H7): H4 i H5 ja són
  al grup dels homes quan D6 arriba, i ella espera darrere seu; H7 queda
  darrere d'ella. → D6 no passa abans d'H4/H5, i H7 mai passa abans de D6.

La `fairness = true` afegeix FIFO dins de cada cua (H5 no adelanta H4, com
va confirmar el professor a l'aula digital), però la propietat H7↮D6 no en
dependeix: ve donada per la retenció del torn.

### Detalls de correcció

- `nGrup` es descompta només quan el procés *surt*: si un 4t procés del
  mateix sexe espera plaça, el grup no es considera acabat i el bany no es
  declara buit entre ell i el seu company (coherent amb la figura 3).
  Per això cal comptar els membres del grup en lloc de només els que són
  dins: `nGrup − nDins` és exactament la cua d'espera de places.
- La sortida mai toca el semàfor `torn`, de manera que el primer del grup
  bloquejat a `buit` no impedeix mai que l'altre sexe surti i l'allibi
  (lliure de bloquejos).
- Els sagnats de 8 espais en els missatges de les dones segueixen el format
  de l'exemple de l'enunciat.

### Eleccions de disseny

- **Un sol semàfor de capacitat (`places`) compartit.** Com que els sexes
  mai es barregen, la capacitat és global: 3 persones. Compte: això només
  és segur perquè la plaça es demana **després** d'haver assegurat el lloc
  al grup (`buit` ja és nostre). Si es demanés abans del torn, un procés
  esperant per formar el grup següent retardaria una plaça mentre bloqueja
  al `buit`, i reduiria la capacitat efectiva del grup que hi ha dins.
- **La plaça es demana DESPRÉS del torn.** Formar part del grup (`nGrup`)
  és el que fixa el lloc a la cua: amb les places plenes, espero la plaça
  ja dins del meu grup, i un procés de l'altre sexe que arribi després em
  troba a mi primer al torn (D6 espera H4 i H5; H7 espera D6).
- **Comptadors compartits + `tipus` (i no comptadors per sexe).** Amb un
  únic comptador de grup cal saber de qui és el grup viu: d'això s'encarrega
  `tipus`. Sense `tipus`, un comptador compartit és inservible: si D6
  incrementés el comptador del grup viu dels homes (nGrup 3→4), mai seria
  "primera" (nGrup == 1), ningú reclamaria el `buit` per al seu grup i, en
  sortir els homes, nGrup mai arribaria a 0 → el `buit` no es lliura mai →
  bloqueig permanent. La informació equivalent es podria codificar també
  amb dos comptadors per sexe (`hPend/dPend`, patró "lightswitch" per sexe):
  són dues maneres d'emetre la mateixa informació (de qui és el grup viu).
- **Dos comptadors (`nGrup` i `nDins`), necessàriament.** `nGrup == 0`
  decideix l'últim del grup (qui deixa el `buit`) i compta també qui espera
  plaça; `nDins` dona el comptador imprès, que l'enunciat exigeix per sexe.
  Com que dins del bany tothom és del sexe `tipus`, un únic comptador dins
  és suficient per als dos missatges.

## Proves realitzades

Eines a `tools/` (no formen part de l'exercici, són de verificació):

- `check_output.py` — valida la sortida completa: protocol de missatges per
  persona (1 arriba, 2 treballa, entra 1/2 i 2/2 en ordre, 2 surt, 1 acaba
  com a últim missatge), comptador imprès coherent amb els entra/surt,
  capacitat ≤ 3, exclusió de sexes, `*** El bany està buit***` només amb el
  bany buit i sagnats correctes.
- `check_starvation.py` — sobre execucions amb esperes nul·les (totes les
  visites pendents des de l'inici) comprova que les onades de grup alternen
  estrictament entre sexes.

Resultats (amb `-ea`, asseveracions internes actives):

| Escenari | Execucions | Resultat |
|---|---|---|
| espera 0 ms (estrès màxim) | 50 + 60 | 100% OK |
| espera 2 ms | 50 | 100% OK |
| espera 20 ms | 40 | 100% OK |
| espera 200 ms | 25 | 100% OK |
| espera 1000 ms (per defecte) | 8 + 5 | 100% OK |

Cap bloqueig (totes les execucions dins del *timeout*), cap violació de
capacitat/exclusió, cap inanició (alternança d'onades sempre correcta).
El validador es va verificar amb 8 mutacions de la sortida (capacitat 4,
sexe barrejat, surt perdut, `buit` prematur, comptador erroni, sagnat
incorrecte, acció post-`acaba`, persona sense arribar) — totes detectades.
