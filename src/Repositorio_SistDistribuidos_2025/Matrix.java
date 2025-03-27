package Repositorio_SistDistribuidos_2025;

public class Matrix<TypeData> {
    private final int rows;
    private final int cols;
    private TypeData[][] matrix;

    public Matrix(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        this.matrix = (TypeData[][]) new Object[rows][cols];
    }
    public Matrix(int rows, int cols, TypeData[][] matrix) {
        this.rows = rows;
        this.cols = cols;
        this.matrix = matrix;
    }
    public Matrix(TypeData[][] matrix) {
        this.rows = matrix.length;
        this.cols = matrix[0].length;
        this.matrix = matrix;
    }
    public int getRows() {
        return rows;
    }
    public int getCols() {
        return cols;
    }
    public TypeData[][] getMatrix() {
        return matrix;
    }
    public int matrixSize() {return rows * cols;}
    public TypeData getElement(int row, int col) {
     if (row < 0 || row >= rows || col < 0 || col >= cols) {
         throw new IndexOutOfBoundsException("Index out of bounds");
     }
        return matrix[row][col];
    }
    public void setMatrix(TypeData[][] matrix) {
        this.matrix = matrix;
    }
    public void setElement(int row, int col, TypeData data) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            throw new IndexOutOfBoundsException("Index out of bounds");
        }
        matrix[row][col] = data;
    }

    public void printMatrix() {
        System.out.println();
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                System.out.print(matrix[i][j] + "\t");
            }
            System.out.println();
        }
    }
}
