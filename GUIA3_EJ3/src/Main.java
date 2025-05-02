//Sin el sincronized lo que ocurre es que se genera una condicion de carrera al depositar el dinero
//por lo que se generan inconsistencias en el saldo de las cuenta, no depositando el importe correcto
import java.util.concurrent.Semaphore;

public class Main {
    public static void main(String[] args) {
        CuentaBancaria Julian = new CuentaBancaria();

        Semaphore semaphore = new Semaphore(1);

        try {
            CuentaBancaria.Banco Cajero1 = Julian.new Banco(10000, semaphore);
            CuentaBancaria.Banco Cajero2 = Julian.new Banco(20000, semaphore);

            System.out.println("Saldo inicial: " + Julian.getSaldo());
            Cajero1.start();
            Cajero2.start();
            // Esperar a que finalicen los hilos

            try {
                Cajero1.join();
                Cajero2.join();
            }
            catch (InterruptedException e) {
            e.printStackTrace();
            }
            System.out.println("Saldo final: " + Julian.getSaldo());
            } catch(Exception e){
                 System.out.println(e);
            }
    }
}