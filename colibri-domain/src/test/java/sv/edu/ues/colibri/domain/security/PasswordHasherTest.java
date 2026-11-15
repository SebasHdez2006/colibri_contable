package sv.edu.ues.colibri.domain.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprueba el hash de contraseñas y la generación de códigos de recuperación.
 */
class PasswordHasherTest {

    @Test
    void noAlmacenaTextoClaroYVerificaCorrectamente() {
        char[] password = "Colibri2026!".toCharArray();
        byte[] salt = PasswordHasher.generarSal();
        byte[] hash = PasswordHasher.hash(password.clone(), salt);

        String comoTexto = new String(hash, StandardCharsets.ISO_8859_1);
        assertFalse(comoTexto.contains("Colibri2026!"));
        assertNotEquals("Colibri2026!", new String(hash));

        assertTrue(PasswordHasher.verificar("Colibri2026!".toCharArray(), salt, hash));
        assertFalse(PasswordHasher.verificar("otra".toCharArray(), salt, hash));
    }

    @Test
    void codigoRecuperacionTieneLongitudRazonable() {
        String codigo = PasswordHasher.generarCodigoRecuperacion();
        assertTrue(codigo.length() >= 8);
    }
}
