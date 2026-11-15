package sv.edu.ues.colibri.domain.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Hash PBKDF2 con sal. Nunca almacena contraseñas en texto claro (H08.02).
 */
public final class PasswordHasher {

    private static final int ITERACIONES = 120_000;
    private static final int LONGITUD_CLAVE = 256;
    private static final int LONGITUD_SAL = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static byte[] generarSal() {
        byte[] sal = new byte[LONGITUD_SAL];
        RANDOM.nextBytes(sal);
        return sal;
    }

    public static byte[] hash(char[] password, byte[] sal) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, sal, ITERACIONES, LONGITUD_CLAVE);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular el hash de la contraseña", e);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public static boolean verificar(char[] password, byte[] sal, byte[] hashEsperado) {
        byte[] calculado = hash(password, sal);
        // MessageDigest.isEqual es de tiempo constante: no cortocircuita al primer
        // byte distinto, evitando que un atacante infiera bytes del hash por timing.
        return MessageDigest.isEqual(calculado, hashEsperado);
    }

    public static String generarCodigoRecuperacion() {
        byte[] bytes = new byte[8];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, 10).toUpperCase();
    }
}
