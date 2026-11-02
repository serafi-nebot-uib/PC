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
| `buit` | binari | 1 | El bany és lliure; l'agafa el 1r de cada grup i el deixa l'últim |
| `hMult` / `dMult` | comptador | 3 | Màxim 3 homes / 3 dones dins alhora |
| `hMtx` / `dMtx` | binari | 1 | Protegeixen els comptadors `hPend/hDins` / `dPend/dDins` |

Estructura de cada procés (`Persona`): arriba al despatx → treballa →
*(entra → surt → treballa) ×2* → acaba la feina.

### Protocol d'entrada (per sexe, amb els comptadors `pend` i `dins`)

1. `mult.acquire()` — reservo una de les 3 places del meu sexe.
2. `torn.acquire()` — agafo el torn; només un procés decideix a la vegada.
3. `pend++` sota el meu `mtx`; si sóc **el primer del grup** i hi ha l'altre
   sexe dins → `buit.acquire()`.
4. `torn.release()` — **el primer del grup espera retenció del torn**: mentre
   ell bloqueja al semàfor `buit`, cap procés nou (de cap sexe) pot arribar a
   demanar plaça.
5. Ja sóc dins: `dins++` sota `mtx`, imprimeixo `entra i/2. Homes|Dones al bany: N`.

### Protocol de sortida

1. `dins--`, `pend--` i `surt` sota `mtx`.
2. `mult.release()` — lliuro la plaça.
3. Si sóc l'**últim del grup** (`pend == 0`): imprimeixo `*** El bany està buit***`
   i `buit.release()` — el bany queda disponible per a l'altre sexe.

### Per què no hi ha inanició (la clau de la solució)

La propietat fonamental exigida és: **si D espera, cap H arribat després
(H7) pot entrar abans que ella**. Es compleix *estructuralment*, independentment
de l'ordre de les cues:

- Mentre D6 bloqueja a `buit.acquire()` (pas 3), **retén el torn**. Cap altre
  procés pot passar del pas 2: els H queden aturats al semàfor `torn`, darrere
  seu, i no poden ni comptar-se ni entrar.
- Quan l'últim H del grup surt fa `buit.release()` → D6 és la següent a entrar
  i, mentre el seu grup visqui, els H nous tornen a quedar-se retinguts al torn.
- Els grups, per tant, s'alternen H→D→H→D… sempre que hi hagi gent pendent
  dels dos sexes: ningú espera infinit.

A més, la retenció del torn resol els exemples de l'enunciat:

- `H D H H`: H1 entra; D es retinguda al torn (H2 encara no ha passat); H2
  passa el torn, s uneix al grup d'H1 i entra; quan el bany queda buit, D entra;
  H3 espera darrere de D. → seqüència efectiva H1, H2, D, H3, tal com diu la
  figura.
- `H D H H H H`: el 4t H espera plaça al multiplex (`hMult`, màxim 3); D
  espera retinguda el torn; el 5è H espera darrere de D al torn. → exactament
  la figura 3.

La `fairness = true` afegeix FIFO dins de cada cua (H5 no adelanta H4, com va
confirmar el professor a l'aula digital), però la propietat H7↮D6 no en
dependeix: ve donada per la retenció del torn.

### Detalls de correcció

- `pend` (reservats) es descompta només quan el procés *surt*: si un 4t procés
  del mateix sexe espera plaça, el grup no es considera acabat i el bany no
  es declara buit entre ell i el seu company (coherent amb la figura 3).
- La sortida mai toca el semàfor `torn`, de manera que el primer del grup
  bloquejat a `buit` no impedeix mai que l'altre sexe surti i l'alli
  (lliure de bloquejos).
- Els sagnats de 8 espais en els missatges de les dones segueixen el format de
  l'exemple de l'enunciat.

## Proves realitzades

Eines a `tools/` (no formen part de l'exercici, són de verificació):

- `check_output.py` — valida la sortida completa: protocol de missatges per
  persona (1 arriba, 2 treballa, entra 1/2 i 2/2 en ordre, 2 surt, 1 acaba com
  a últim missatge), comptador imprès coherent amb els entra/surt, capacitat
  ≤ 3, exclusió de sexes, `*** El bany està buit***` només amb el bany buit i
  sagnats correctes.
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
