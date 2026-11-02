#!/usr/bin/env python3
"""Validador de la sortida de BanyMixt.

Comprova els invariants del problema del bany mixt sobre la sortida del
programa (s'espera la sortida per stdin):

  1. Cada persona: exactament 1 "arriba al despatx", 2 "treballa",
     1 "entra 1/2" + 1 "entra 2/2" (en aquest ordre), 2 "surt" i
     1 "acaba la feina" com a ÚLTIM missatge de la persona.
  2. El comptador "Homes|Dones al bany: N" és sempre coherent amb els
     entra/surt anteriors i 1 <= N <= 3.
  3. Mai hi ha homes i dones dins del bany al mateix temps.
  4. "*** El bany està buit***" només s'imprimeix quan el bany és buit
     (cap persona de cap sexe dins).
  5. El sagnat és correcte: les dones amb 8 espais, els homes sense.
  6. La paraula de sexe del missatge "entra" coincideix amb el sexe de
     la persona.

Sortida: "OK" i codi 0 si tot és correcte; sinó llista els errors i codi 1.
"""
import re
import sys

SEX = {
    "GORI": "H", "COSME": "H", "JAUME": "H", "DAMIA": "H",
    "ANTONI": "H", "BERNAT": "H",
    "AINA": "D", "GERONIA": "D", "CATALINA": "D",
    "ELISABET": "D", "JOANA": "D", "FRANCESCA": "D",
}

RE_ARRIBA = re.compile(r"(\S+) arriba al despatx$")
RE_TREBALLA = re.compile(r"(\S+) treballa$")
RE_ENTRA = re.compile(r"(\S+) entra (\d)/2\. (Homes|Dones) al bany: (\d+)$")
RE_SURT = re.compile(r"(\S+) surt$")
RE_ACABA = re.compile(r"(\S+) acaba la feina$")
RE_BUIT = re.compile(r"\*\*\* El bany està buit\*\*\*$")

def main() -> int:
    try:
        sys.stdin.reconfigure(encoding="utf-8")
    except Exception:
        pass

    st = {n: {"arriba": 0, "treballa": 0, "entra": [], "surt": 0,
              "acaba": 0, "acabat": False} for n in SEX}
    h = d = 0            # persones dins del bany (home/dona), en ordre de sortida
    errors: list[str] = []

    def err(lineno: int, msg: str) -> None:
        errors.append(f"  línia {lineno}: {msg}")

    for lineno, raw in enumerate(sys.stdin.read().splitlines(), 1):
        line = raw.rstrip()
        if not line.strip():
            continue
        indented = line.startswith("        ")
        content = line.lstrip(" ")

        m = RE_BUIT.fullmatch(content)
        if m:
            if indented:
                err(lineno, "el missatge 'bany buit' no hauria d'estar sagnat")
            if h != 0 or d != 0:
                err(lineno, f"'bany buit' imprès amb persones dins (h={h}, d={d})")
            continue

        m = RE_ARRIBA.fullmatch(content)
        if m:
            nom = m.group(1)
            if nom not in SEX:
                err(lineno, f"persona desconeguda: {nom}")
                continue
            if indented != (SEX[nom] == "D"):
                err(lineno, f"sagnat incorrecte per a {nom} ({SEX[nom]})")
            st[nom]["arriba"] += 1
            if st[nom]["arriba"] > 1:
                err(lineno, f"{nom} arriba més d'una vegada")
            continue

        m = RE_ENTRA.fullmatch(content)
        if m:
            nom, k, paraula, n = m.group(1), int(m.group(2)), m.group(3), int(m.group(4))
            if nom not in SEX:
                err(lineno, f"persona desconeguda: {nom}")
                continue
            if indented != (SEX[nom] == "D"):
                err(lineno, f"sagnat incorrecte per a {nom} ({SEX[nom]})")
            if SEX[nom] == "H":
                h += 1
                esperada = "Homes"
            else:
                d += 1
                esperada = "Dones"
            if paraula != esperada:
                err(lineno, f"{nom} ({SEX[nom]}) entra amb paraula de sexe '{paraula}'")
            # Comprova rang del propi sexe i exclusió de l'altre sexe
            esperat = h if SEX[nom] == "H" else d
            if n != esperat:
                err(lineno, f"{nom} imprimeix '{paraula} al bany: {n}' però el comptador real és {esperat}")
            if SEX[nom] == "H":
                if not (1 <= h <= 3):
                    err(lineno, f"comptador d'homes fora de rang en entrar {nom} (h={h})")
                if d != 0:
                    err(lineno, f"{nom} entra amb dones dins (h={h}, d={d})")
            else:
                if not (1 <= d <= 3):
                    err(lineno, f"comptador de dones fora de rang en entrar {nom} (d={d})")
                if h != 0:
                    err(lineno, f"{nom} entra amb homes dins (h={h}, d={d})")
            if k != len(st[nom]["entra"]) + 1:
                err(lineno, f"{nom} entra {k}/2 fora d'ordre (visites: {st[nom]['entra']})")
            st[nom]["entra"].append(k)
            continue

        m = RE_SURT.fullmatch(content)
        if m:
            nom = m.group(1)
            if nom not in SEX:
                err(lineno, f"persona desconeguda: {nom}")
                continue
            if indented != (SEX[nom] == "D"):
                err(lineno, f"sagnat incorrecte per a {nom} ({SEX[nom]})")
            if SEX[nom] == "H":
                h -= 1
            else:
                d -= 1
            if h < 0 or d < 0:
                err(lineno, f"{nom} surt amb comptador negatiu (h={h}, d={d})")
            st[nom]["surt"] += 1
            continue

        m = RE_TREBALLA.fullmatch(content)
        if m:
            nom = m.group(1)
            if nom not in SEX:
                err(lineno, f"persona desconeguda: {nom}")
                continue
            if indented != (SEX[nom] == "D"):
                err(lineno, f"sagnat incorrecte per a {nom} ({SEX[nom]})")
            if st[nom]["acabat"]:
                err(lineno, f"{nom} fa accions després d'acabar la feina")
            st[nom]["treballa"] += 1
            continue

        m = RE_ACABA.fullmatch(content)
        if m:
            nom = m.group(1)
            if nom not in SEX:
                err(lineno, f"persona desconeguda: {nom}")
                continue
            if indented != (SEX[nom] == "D"):
                err(lineno, f"sagnat incorrecte per a {nom} ({SEX[nom]})")
            st[nom]["acaba"] += 1
            st[nom]["acabat"] = True
            continue

        err(lineno, f"línia no reconeguda: {line!r}")

    # Comprovacions finals
    for nom, s in st.items():
        if s["arriba"] != 1:
            errors.append(f"  {nom}: arriba={s['arriba']} (esperat 1)")
        if s["treballa"] != 2:
            errors.append(f"  {nom}: treballa={s['treballa']} (esperat 2)")
        if s["entra"] != [1, 2]:
            errors.append(f"  {nom}: entra={s['entra']} (esperat [1, 2])")
        if s["surt"] != 2:
            errors.append(f"  {nom}: surt={s['surt']} (esperat 2)")
        if s["acaba"] != 1:
            errors.append(f"  {nom}: acaba={s['acaba']} (esperat 1)")

    if errors:
        print("FALLA:")
        print("\n".join(errors))
        return 1
    print("OK")
    return 0

if __name__ == "__main__":
    sys.exit(main())
