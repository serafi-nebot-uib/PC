import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 21720 Programació Concurrent — Curs 2026-2027
 * Primer exercici avaluable: El problema del bany mixt amb semàfors.
 *
 * Regles del bany:
 *   - No hi pot haver al mateix temps dones i homes.
 *   - Com a màxim 3 dones o 3 homes al mateix temps.
 *   - Cada persona hi va dues vegades durant la jornada.
 *
 * Eines de sincronització: ÚNICAMENT semàfors (comptadors i binaris).
 * Tots els semàfors es creen amb fairness = true (cues FIFO), de manera que
 * l'ordre d'arribada es respecta (H5 no adelanta H4) i, sobretot, es
 * garanteix la propietat fonamental contra la inanició: si una dona espera,
 * cap home que hi arribi després (H7) no pot entrar abans que ella.
 *
 * Idea central (el "torn d'entrada"):
 *   El primer procés de cada grup que arriba mentre hi ha l'altre sexe dins
 *   es queda BLOQUEJAT al semàfor "buit" SENSE alliberar el torn. Així cap
 *   procés nou (ni del seu sexe ni de l'altre) pot demanar plaça fins que el
 *   bany queda buit i li toca. Això força l'alternança de grups i impedeix
 *   la inanició de manera ESTRUCTURAL, independentment de l'ordre de les
 *   cues dels semàfors.
 */
public class BanyMixt {

    // ----------------------------- Configuració -----------------------------

    /** Capacitat màxima del bany (per sexe). */
    static final int MAX_BANY = 3;
    /** Vegades que cada persona va al bany durant la jornada. */
    static final int BANYADES = 2;

    // ------------------------------- Semàfors -------------------------------
    // Tots amb fairness = true: cues FIFO.

    /** Torn d'entrada: només UN procés pot estar decidint si entrar o esperar. */
    static final Semaphore torn = new Semaphore(1, true);
    /** El bany està lliure. L'agafa el primer procés de cada grup i el deixa
     *  l'últim procés del mateix grup en sortir. */
    static final Semaphore buit = new Semaphore(1, true);
    /** Multiplex dels homes: màxim MAX_BANY dins alhora. */
    static final Semaphore hMult = new Semaphore(MAX_BANY, true);
    /** Multiplex de les dones: màxim MAX_BANY dins alhora. */
    static final Semaphore dMult = new Semaphore(MAX_BANY, true);
    /** Protegeix els comptadors dels homes. */
    static final Semaphore hMtx = new Semaphore(1, true);
    /** Protegeix els comptadors de les dones. */
    static final Semaphore dMtx = new Semaphore(1, true);

    // ------------------------------ Comptadors ------------------------------

    /** Homes que han reservat plaça (dins del bany o a punt d'hi entrar). */
    static int hPend = 0;
    /** Dones que han reservat plaça (dins del bany o a punt d'hi entrar). */
    static int dPend = 0;
    /** Homes dins del bany ara mateix. */
    static int hDins = 0;
    /** Dones dins del bany ara mateix. */
    static int dDins = 0;

    /** Multiplicador de les esperes en ms (per defecte 1000; 0 = sense esperes,
     *  útil per a proves d'estrès d'entrellaçats). */
    static volatile long esperaMaxMs = 1000;

    // ------------------------------ Processos -------------------------------

    enum Sexe { HOME, DONA }

    static final class Persona extends Thread {

        private final Sexe sexe;
        /** Les dones escriuen els missatges sagnats amb 8 espais, com a la
         *  sortida d'exemple de l'enunciat. */
        private final String prefix;

        Persona(String nom, Sexe sexe) {
            super(nom);                    // l'identificador és una cadena
            this.sexe = sexe;
            this.prefix = (sexe == Sexe.DONA) ? "        " : "";
        }

        private void espera() throws InterruptedException {
            long ms = (esperaMaxMs <= 0) ? 0
                    : ThreadLocalRandom.current().nextLong(esperaMaxMs + 1);
            if (ms > 0) Thread.sleep(ms);
        }

        private void missatge(String text) {
            System.out.println(prefix + getName() + text);
        }

        @Override
        public void run() {
            try {
                espera();
                missatge(" arriba al despatx");

                for (int vegada = 1; vegada <= BANYADES; vegada++) {
                    espera();
                    missatge(" treballa");
                    espera();
                    entra(vegada);
                    espera();               // temps dins del bany
                    surt();
                }

                espera();
                missatge(" acaba la feina");
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                // Qualsevol violació d'una asseveració (-ea) atura la simulació.
                System.err.println("[VIOLACIÓ] " + getName() + ": " + t);
                Runtime.getRuntime().halt(1);
            }
        }

        /**
         * Reserva plaça, comprova que pot passar i entra al bany.
         * A la sortida imprimeix "entra i/2. Homes|Dones al bany: N".
         */
        private void entra(int vegada) throws InterruptedException {
            boolean home = (sexe == Sexe.HOME);
            Semaphore mult = home ? hMult : dMult;   // plaça del meu sexe
            Semaphore mtx  = home ? hMtx  : dMtx;    // comptadors del meu sexe

            mult.acquire();                  // 1) reservo una de les 3 places
            torn.acquire();                  // 2) agafo el torn d'entrada

            mtx.acquire();
            int pend = home ? ++hPend : ++dPend;
            boolean primer = (pend == 1);    // sóc el primer del meu grup?
            mtx.release();

            // 3) Si sóc el primer del grup i hi ha l'altre sexe, espero el bany
            //    buit MANTENINT el torn: cap procés nou pot demanar plaça fins
            //    que jo hagi passat (H7 no adelanta mai D6).
            if (primer) buit.acquire();
            torn.release();

            // 4) Ja sóc dins: actualitzo el comptador i imprimeixo.
            mtx.acquire();
            int dins = home ? ++hDins : ++dDins;
            assert dins >= 1 && dins <= MAX_BANY : "capacitat del bany excedida";
            missatge(" entra " + vegada + "/" + BANYADES + ". "
                    + (home ? "Homes" : "Dones") + " al bany: " + dins);
            mtx.release();
        }

        /**
         * Surt del bany: allibera la plaça i, si sóc l'últim del grup,
         * imprimeix que el bany ha quedat buit i el cedeix a l'altre sexe.
         */
        private void surt() throws InterruptedException {
            boolean home = (sexe == Sexe.HOME);
            Semaphore mult = home ? hMult : dMult;
            Semaphore mtx  = home ? hMtx  : dMtx;

            mtx.acquire();
            int dins = home ? --hDins : --dDins;
            int pend = home ? --hPend : --dPend;
            assert dins >= 0 && pend >= dins : "comptadors inconsistents";
            missatge(" surt");
            mtx.release();

            mult.release();                  // lliuro la meva plaça

            if (pend == 0) {
                // Últim del grup: el bany queda lliure per a l'altre sexe.
                System.out.println("*** El bany està buit***");
                buit.release();
            }
        }
    }

    // --------------------------------- Main ---------------------------------

    public static void main(String[] args) throws InterruptedException {
        if (args.length > 0) {
            try {
                esperaMaxMs = Long.parseLong(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Ús: java BanyMixt [esperaMàximaEnMs]  (0 = sense esperes)");
                System.exit(2);
            }
        }

        Persona[] persones = {
            new Persona("GORI",      Sexe.HOME),
            new Persona("COSME",     Sexe.HOME),
            new Persona("JAUME",     Sexe.HOME),
            new Persona("DAMIA",     Sexe.HOME),
            new Persona("ANTONI",    Sexe.HOME),
            new Persona("BERNAT",    Sexe.HOME),
            new Persona("AINA",      Sexe.DONA),
            new Persona("GERONIA",   Sexe.DONA),
            new Persona("CATALINA",  Sexe.DONA),
            new Persona("ELISABET",  Sexe.DONA),
            new Persona("JOANA",     Sexe.DONA),
            new Persona("FRANCESCA", Sexe.DONA),
        };

        for (Persona p : persones) p.start();
        for (Persona p : persones) p.join();   // si algun thread es bloqueja, main no acaba
    }
}
