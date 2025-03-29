import java.lang.Integer;
import java.util.Random;
import Repositorio_SistDistribuidos_2025.Matrix;

public class Main {
    public final static int SIZE = 10000;
    public final static int SCALAR = 10;
    private final static int ROWSPERTHREAD = 10000000;

    static class Worker extends Thread{
        private final int initRowIndex;
        private final int finalRowIndex;
        private final Matrix<Integer> matrix;
        private final int scalar;

        Worker(int initRowIndex, int finalRowIndex, Matrix<Integer> matrix, int scalar) {
            this.initRowIndex = initRowIndex;
            this.finalRowIndex = finalRowIndex;
            this.matrix = matrix;
            this.scalar = scalar;
        }

        @Override
        public void run() {
            for (int i = initRowIndex; i < finalRowIndex; i++) {
                for (int j = 0; j < matrix.getCols(); j++) {
                    matrix.setElement(i,
                                      j,
                                 SCALAR * matrix.getElement(i,j));
                }
            }

        }
    }

    public static void main(String[] args) {
        Random rand = new Random();
        Matrix<Integer> matrix = new Matrix<>(SIZE, SIZE);

        int rows = matrix.getRows();
        int cols = matrix.getCols();

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                matrix.setElement(i,j,rand.nextInt(0,100));
            }
        }
        double ini = System.currentTimeMillis();
//        System.out.println("Initial matrix");
//        matrix.printMatrix();
//        System.out.println();

        // Determinar la cantidad de hilos dinamicamente
        int totalThreads = 2;

        while (matrix.matrixSize() / totalThreads > ROWSPERTHREAD)
        {
            totalThreads += 2;
        }

        System.out.println("Total threads in use: " + totalThreads);

        // Dividir las filas de la matriz entre los hilos
        int rowsPerThread = rows / totalThreads;
        int remainingRows = rows % totalThreads;

        Worker[] workers = new Worker[totalThreads];
        int startRow = 0;

        // Creacion de hilos y asignacion de trabajo
        for (int i = 0; i < totalThreads; i++) {
            int endRow = startRow + rowsPerThread + (i<remainingRows ? 1:0);
            workers[i] = new Worker(startRow, endRow, matrix, SCALAR);

            startRow = endRow;
        }

        // Iniciacion de hilos
        for(Worker worker : workers) {
            worker.start();
        }

        //Espera a que todos los hilo terminen
        try{
            for(Worker worker : workers){
                worker.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        double fin = System.currentTimeMillis();
//        System.out.println("Final matrix");
//        matrix.printMatrix();
//        System.out.println();
        System.out.printf("Time taken: %.3f ms %n",fin-ini);

    }
}

/*public class Main {
    public static void main(String[] args) {
        Random rand = new Random();
        Integer randomSize = rand.nextInt(0,50);
        double ini = System.currentTimeMillis();
        Matrix<Integer> matrix = new Matrix<>(randomSize, randomSize);

        int rows = matrix.getRows();
        int cols = matrix.getCols();

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                matrix.setElement(i,j,rand.nextInt(0,50));
            }
        }
        matrix.printMatrix();

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                matrix.setElement(i,
                                  j,
                        randomSize * matrix.getElement(i,j));
            }
        }
        double fin = System.currentTimeMillis();
        matrix.printMatrix();
        System.out.println();
        System.out.printf("Time taken: %.3f ms %n",fin-ini);

    }
}*/