import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

public class CuentaBancaria {

    private double saldo = 0;
    private final ReentrantLock lock = new ReentrantLock();  // Lock para proteger el saldo

    public class Banco extends Thread {
        private final double importe;
        private final Semaphore semaforo;

        public Banco(double importe, Semaphore semaforo) {
            this.importe = importe;
            this.semaforo = semaforo;
        }

        @Override
        public void run() {
            try {
                semaforo.acquire(); // Solo permite un número limitado de hilos concurrentes
                depositar(importe);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                semaforo.release(); // Libera el semáforo
            }
        }
    }

    public double getSaldo() {
        lock.lock();
        try {
            return saldo;
        } finally {
            lock.unlock();
        }
    }

    public void depositar(double importe) {
        lock.lock();
        try {
            for (int i = 0; i < importe; i++) {
                saldo += 1;
            }
        } finally {
            lock.unlock();
        }
    }

    public void extraer(double importe) {
        lock.lock();
        try {
            for (int i = 0; i < importe; i++) {
                saldo -= 1;
            }
        } finally {
            lock.unlock();
        }
    }

    public void transferir(CuentaBancaria destino, double importe) {
        this.extraer(importe);
        destino.depositar(importe);
    }
}

//Con synchronized
//public class CuentaBancaria {
//    private static double saldo = 0.0;
//
//    public class Banco extends Thread{
//    private double importe;
//
//    Banco(double importe){
//        this.importe = importe;
//    }
//        @Override
//        public void run(){
//            depositar(importe);
//        }
//    }
//
//    public synchronized double getSaldo() {
//        return saldo;
//    }
//
//    public synchronized void depositar(double importe) {
//        for (int i = 0; i < importe; i++) {
//            saldo += 1;
//        }
//    }
//
//    public synchronized void extraer(double importe) {
//        for (int i = 0; i < importe; i++) {
//            saldo -= 1;
//        }
//    }
//
//    public void transferir(CuentaBancaria destino, double importe) {
//        synchronized (this) {
//            extraer(importe);
//        }
//        synchronized (destino) {
//            destino.depositar(importe);
//        }
//    }
//}
