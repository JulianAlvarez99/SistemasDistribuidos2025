import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.*;


public class Main {
    private static final int ROWSPERTHREAD = 500000;
    private static final int NUM_CONSUMERS = 4;
    private static final int BUFFER_SIZE = 10;
    private static final BlockingQueue<File> buffer = new ArrayBlockingQueue<>(BUFFER_SIZE);
    private static final File inputDir = new File("C:/path");
    private static final File outputDir = new File("C:/path");
    private static final File SENTINEL = new File("SENTINEL");

    static class ImageWorker extends Thread {
        private int initHeight;
        private int endHeight;
        private int width;
        private BufferedImage image;

        ImageWorker(BufferedImage image, int initHeight, int endHeight, int width) {
            this.image = image;
            this.initHeight = initHeight;
            this.endHeight = endHeight;
            this.width = width;
        }

        @Override
        public void run() {
            for (int y = initHeight; y < endHeight; y++) {
                for (int x = 0; x < width; x++) {
                    int p = image.getRGB(x, y);

                    int a = (p >> 24) & 0xff;
                    int r = (p >> 16) & 0xff;
                    int g = (p >> 8) & 0xff;
                    int b = p & 0xff;
                    int avg = (r + g + b) / 3;
                    p = (a << 24) | (avg << 16) | (avg << 8) | avg;
                    image.setRGB(x, y, p);
                }
            }
        }
    }

    static int Find_total_Threads(int totalPixels) {
        int totalThreads = 2;

        while (totalPixels / totalThreads > ROWSPERTHREAD) {
            totalThreads += 2;
        }
        //System.out.println("Total threads in use: " + totalThreads);
        return totalThreads;
    }

    static class FileWorker extends Thread {
        private File file;

        FileWorker(File file) {
            this.file = file;
        }

        @Override
        public void run() {
            try {
                BufferedImage img = ImageIO.read(file);
                int width = img.getWidth();
                int height = img.getHeight();
                int totalPixels = width * height;
                // Determinar la cantidad de hilos dinamicamente

                int totalThreads = Main.Find_total_Threads(totalPixels);

                // Dividir las filas de la matriz entre los hilos
                int rowsPerThread = height / totalThreads;
                int remainingRows = height % totalThreads;

                ImageWorker[] workers = new ImageWorker[totalThreads];
                int startRow = 0;

                // Creacion de hilos y asignacion de trabajo
                for (int i = 0; i < totalThreads; i++) {
                    int endRow = startRow + rowsPerThread + (i < remainingRows ? 1 : 0);
                    workers[i] = new ImageWorker(img, startRow, endRow, width);

                    startRow = endRow;
                }

                // Iniciacion de hilos
                for (ImageWorker worker : workers) {
                    worker.start();
                }

                //Espera a que todos los hilos terminen
                try {
                    for (ImageWorker worker : workers) {
                        worker.join();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                File outputFile = new File(outputDir, file.getName());
                ImageIO.write(img, "jpg", outputFile);
                System.out.println("Procesada y guardada: " + outputFile.getAbsolutePath());
            } catch (IOException e) {
                System.out.println(e);
            }
        }
    }

//// EJERCICIO SOLO CON HILOS
//    public static void main(String[] args) {
//        int filesQuantity = 0;
//        try {
//            if (args.length < 2) {
//                System.out.println("Uso: java ImageProcessorConcurrent <inputDirPath> <outputDirPath>");
//                return;
//            }
//            if (!outputDir.exists()) {
//                outputDir.mkdirs(); // crea la carpeta si no existe
//            }
//
//            long ini = System.currentTimeMillis();
//
//            File[] files = inputDir.listFiles((dir, name) -> {
//                String lowerName = name.toLowerCase();
//                return lowerName.endsWith(".jpg") || lowerName.endsWith(".png") || lowerName.endsWith(".jpeg");
//            });
//
//            if (files != null) {
//                filesQuantity = files.length;
//                FileWorker[] workers = new FileWorker[filesQuantity];
//                // Creacion de hilos y asignacion de trabajo
//                for (int i = 0; i < filesQuantity; i++) {
//                    workers[i] = new FileWorker(files[i]);
//                }
//                // Iniciacion de hilos
//                for (FileWorker worker : workers) {
//                    worker.start();
//                }
//                //Espera a que todos los hilos terminen
//                try {
//                    for (FileWorker worker : workers) {
//                        worker.join();
//                    }
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//            long fin = System.currentTimeMillis();
//            System.out.println(fin - ini);
//        }
//        catch(Exception e){
//            System.out.println(e);
//        }
//    }
//}

//// EJERCICIO CON PRODUCTOR Y CONSUMIDOR + HILOS
    public static void main(String[] args) {
        int filesQuantity = 0;
        try {
            if (!outputDir.exists()) {
                outputDir.mkdirs(); // crea la carpeta si no existe
            }

            long ini = System.currentTimeMillis();

            ExecutorService poolConsumidores = Executors.newFixedThreadPool(NUM_CONSUMERS);

            File[] files = inputDir.listFiles((dir, name) -> {
                String lowerName = name.toLowerCase();
                return lowerName.endsWith(".jpg") || lowerName.endsWith(".png") || lowerName.endsWith(".jpeg");
            });

            if (files != null) {
                Thread productor = new Thread(new Productor(files));
                productor.start();

                // Inician los consumidores
                for (int i = 0; i < NUM_CONSUMERS; i++) {
                    poolConsumidores.submit(new Consumidor(i));
                }
                productor.join();

                poolConsumidores.shutdown(); //// Finaliza el pool ( Esto es importante, sino el codigo se queda esperando)
                poolConsumidores.awaitTermination(5, TimeUnit.MINUTES); // Espera a que terminen todos los consumidores
            }
            long fin = System.currentTimeMillis();
            System.out.println("Execution time: ");
            System.out.println(fin - ini);
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    static class Productor implements Runnable {
        private final File[] files;

        Productor(File[] files) {
            this.files = files;
        }
        public void run() {
            try {
                for (File file : files) {
                    buffer.put(file); // Espera si el buffer está lleno
                }
                // Una vez que termina de producir, agrega sentinelas
                for (int i = 0; i < NUM_CONSUMERS; i++) {
                    buffer.put(SENTINEL); // Inserta objeto único de finalización
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    static class Consumidor implements Runnable {
        private final int id;
        private boolean sentinel = true;

        Consumidor(int id) {
            this.id = id;
        }
        public void run() {
            while (true) {
                try {
                    File file = buffer.take();
                    // Señal de fin
                    if(file == SENTINEL) {
                        break;
                    }
                    FileWorker worker = new FileWorker(file);
                    // Iniciacion de hilo
                    worker.start();
                    //Espera a que termine
                    worker.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
