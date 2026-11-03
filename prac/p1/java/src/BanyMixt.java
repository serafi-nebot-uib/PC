import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;

public class BanyMixt {
    static final int BANY_MAX = 3; // capacitat màxima de persones al bany
    static final int BANY_TIMES = 2; // vegades que cada persona va al bany durant una jornada

    // torn de decisió: només una persona pot estar decidint si entrar o no a la vegada
    static final Semaphore torn = new Semaphore(1, true);

    // dret d'us del bany: el reté el grup que pot passar al bany mentre el fa servir.
    // l'agafa el primer que entra i l'allibera el darrer que surt
    static final Semaphore bany = new Semaphore(1, true);

    // places del bany (BANY_MAX): cuantes persones a dintre del bany hi pot haver
    static final Semaphore places = new Semaphore(BANY_MAX, true);

    // mutex que protegeix les variables globals
    static final Semaphore mtx = new Semaphore(1, true);

    enum Sexe { HOME, DONA }

    static Sexe tipus = null; // sexe actual del bany (null = bany buit)
    static int nGrup = 0; // comptador dels membres del grup que pot entrar (no ha d'esperar a que l'altre sexe acabi)
    static int nDins = 0; // comptador de persones dintre del bany

    static final long ESPERA_MAX_MS = 1000; // espera màxima en ms entre fases

    static final class Persona extends Thread {

        private final Sexe sexe;
        private final String prefix;

        Persona(String nom, Sexe sexe) {
            super(nom);
            this.sexe = sexe;
            this.prefix = (sexe == Sexe.DONA) ? "        " : "";
        }

        private void espera() throws InterruptedException {
            long ms = (ESPERA_MAX_MS <= 0) ? 0 : ThreadLocalRandom.current().nextLong(ESPERA_MAX_MS + 1);
            if (ms > 0) Thread.sleep(ms);
        }

        private void missatge(String text) {
            System.out.println(prefix + getName() + " " + text);
        }

        @Override
        public void run() {
            try {
                espera();
                missatge("arriba al despatx");

                for (int vegada = 1; vegada <= BANY_TIMES; vegada++) {
                    espera();
                    missatge("treballa");
                    espera();
                    entra(vegada);
                    espera();
                    surt();
                }

                espera();
                missatge("acaba la feina");
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                // Qualsevol violació d'una asseveració (-ea) atura la simulació.
                System.err.println("[VIOLACIÓ] " + getName() + ": " + t);
                Runtime.getRuntime().halt(1);
            }
        }

        private void entra(int vegada) throws InterruptedException {
            torn.acquire(); // agafar torn per a decidir si entrar o esperar (només un ho pot fer a la vegada)

            mtx.acquire();
            boolean formarGrup = (tipus != sexe); // bany lliure o de l'altre sexe
            if (formarGrup) {
                // el bany esta lliure o actualment és de l'altre sexe
                mtx.release();

                // agafar el dret d'us del bany. possibles situacions:
                // - bany lliure -> soc el primer del meu grup, agaf el bany en nom del meu grup i es reté fins que el darrer del grup surti
                // - altre sexe encara hi és -> esper a que el grup (del sexe opost) l'alliberi
                bany.acquire();

                // el bany queda assignat al meu sexe
                mtx.acquire();
                tipus = sexe;
                nGrup = 1;
            } else {
                // el bany ja esta assignat al meu sexe -> m'afagesc al grup
                nGrup++;
            }
            mtx.release();
            torn.release(); // ja tenc lloc al grup garantit, deix passar al següent

            // esper una de les BANY_MAX plaçes que té el bany (bloqueig fins que algú del meu grup surti)
            places.acquire();

            // dedins del bany -> actualitzar el comptador de persones que hi ha dintre
            mtx.acquire();
            int dins = ++nDins;
            assert dins >= 1 && dins <= BANY_MAX : "capacitat del bany excedida";
            assert tipus == sexe : "sexes barrejats dins del bany";
            missatge("entra " + vegada + "/" + BANY_TIMES + ". " + (sexe == Sexe.HOME ? "Homes" : "Dones") + " al bany: " + dins);
            mtx.release();
        }

        /**
         * Surt del bany: allibera la plaça i, si sóc l'últim del grup,
         * imprimeix que el bany ha quedat buit i el cedeix a l'altre sexe.
         */
        private void surt() throws InterruptedException {
            mtx.acquire();
            nDins--;
            nGrup--;
            boolean ultim = (nGrup == 0); // soc el darrer en surtir?
            if (ultim) tipus = null; // soc el darrer -> canviar el sexe del bany a "no assignat"
            assert nDins >= 0 && nGrup >= nDins : "comptadors inconsistents";
            missatge("surt");
            mtx.release();

            places.release(); // sortir del bany i cedir la plaça al següent

            if (ultim) {
                System.out.println("*** El bany està buit***");
                bany.release(); // notificar al pròxim grup de que el bany ha quedat buit i poden avançar
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
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
        for (Persona p : persones) p.join();
    }
}
