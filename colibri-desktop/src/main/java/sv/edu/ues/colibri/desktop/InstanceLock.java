package sv.edu.ues.colibri.desktop;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Gestiona un archivo de bloqueo para impedir ejecuciones simultáneas de la aplicación.
 */
public final class InstanceLock implements AutoCloseable {

    private final Path lockFile;
    private final RandomAccessFile raf;
    private final FileChannel channel;
    private final FileLock lock;

    private InstanceLock(Path lockFile, RandomAccessFile raf, FileChannel channel, FileLock lock) {
        this.lockFile = lockFile;
        this.raf = raf;
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Intenta adquirir el candado exclusivo de la aplicación.
     *
     * @return candado adquirido correctamente
     * @throws IOException si el archivo no puede crearse o ya existe otra instancia
     */
    public static InstanceLock adquirir() throws IOException {
        Path dir = Path.of(System.getProperty("user.home"), ".colibri");
        Files.createDirectories(dir);
        Path file = dir.resolve("colibri.instance.lock");
        RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw");
        FileChannel channel = raf.getChannel();
        FileLock lock = channel.tryLock();
        if (lock == null) {
            channel.close();
            raf.close();
            throw new IOException("Ya hay una sesión de Colibrí en ejecución.");
        }
        raf.writeBytes("pid=" + ProcessHandle.current().pid() + "\n");
        return new InstanceLock(file, raf, channel, lock);
    }

    /**
     * Libera el candado y cierra los recursos del archivo auxiliar.
     *
     * @throws IOException si ocurre un error al cerrar el archivo o el canal
     */
    @Override
    public void close() throws IOException {
        if (lock != null) {
            lock.release();
        }
        channel.close();
        raf.close();
    }
}
