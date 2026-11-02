#!/usr/bin/env python3
"""Comprova la propietat anti-inanició sobre una execució amb esperes nul·les
(java BanyMixt 0): com que totes les visites estan pendents des del principi,
els grups que entren al bany han d'alternar estrictament H, D, H, D...

Dos grups consecutius del mateix sexe només són legítims si l'altre sexe
encara no ha arribat al despatx (cap membre ha imprès "arriba") o ja ha
esgotat totes les seves visites. Si no, significaria que un procés que
esperava ha estat adelantat → inanició.

Sortida per stdin. "OK" i codi 0 si es compleix; sinó errors i codi 1.
"""
import re
import sys

SEX = {
    "GORI": "H", "COSME": "H", "JAUME": "H", "DAMIA": "H",
    "ANTONI": "H", "BERNAT": "H",
    "AINA": "D", "GERONIA": "D", "CATALINA": "D",
    "ELISABET": "D", "JOANA": "D", "FRANCESCA": "D",
}
TOTAL_VISITES = 2  # per persona

RE_ARRIBA = re.compile(r"(\S+) arriba al despatx$")
RE_ENTRA = re.compile(r"(\S+) entra (\d)/2\.")

def main() -> int:
    try:
        sys.stdin.reconfigure(encoding="utf-8")
    except Exception:
        pass

    arriba: set[str] = set()
    visites = {n: 0 for n in SEX}
    errors: list[str] = []
    onada_actual = None          # sexe de l'onada en curs
    ultima_onada = None          # sexe de l'onada anterior completa

    def altres_pendents(sexe: str) -> bool:
        """Cert si l'altre sexe té visites pendents I ja ha arribat."""
        altre = "D" if sexe == "H" else "H"
        pendents = any(visites[n] < TOTAL_VISITES for n in SEX if SEX[n] == altre)
        arribats = any(n in arriba for n in SEX if SEX[n] == altre)
        return pendents and arribats

    def tanca_onada() -> None:
        nonlocal ultima_onada, onada_actual
        if onada_actual is None:
            return
        if ultima_onada is not None and onada_actual == ultima_onada:
            if altres_pendents(onada_actual):
                errors.append(
                    f"  inanició: dues onades consecutives del sexe {onada_actual} "
                    f"mentre l'altre sexe esperava")
        ultima_onada = onada_actual
        onada_actual = None

    for lineno, raw in enumerate(sys.stdin.read().splitlines(), 1):
        content = raw.rstrip().lstrip(" ")
        m = RE_ARRIBA.fullmatch(content)
        if m and m.group(1) in SEX:
            arriba.add(m.group(1))
            continue
        m = RE_ENTRA.fullmatch(content)
        if m and m.group(1) in SEX:
            nom = m.group(1)
            sexe = SEX[nom]
            if onada_actual is None:
                onada_actual = sexe
            elif onada_actual != sexe:
                errors.append(
                    f"  línia {lineno}: {nom} ({sexe}) entra dins d'una onada "
                    f"del sexe {onada_actual} → sexes barrejats")
            visites[nom] += 1
            continue
        if content == "*** El bany està buit***":
            tanca_onada()

    tanca_onada()

    if errors:
        print("FALLA (alternança d'onades):")
        print("\n".join(errors))
        return 1
    print("OK (alternança d'onades)")
    return 0

if __name__ == "__main__":
    sys.exit(main())
