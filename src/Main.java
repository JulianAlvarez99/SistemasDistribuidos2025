import java.lang.Integer;
import java.util.Random;
import Repositorio_SistDistribuidos_2025.Matrix;

public class Main {
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