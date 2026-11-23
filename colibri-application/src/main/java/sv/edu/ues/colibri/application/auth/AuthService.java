package sv.edu.ues.colibri.application.auth;

import sv.edu.ues.colibri.domain.exception.DomainException;
import sv.edu.ues.colibri.domain.model.BitacoraEntrada;
import sv.edu.ues.colibri.domain.model.Contador;
import sv.edu.ues.colibri.domain.model.RolUsuario;
import sv.edu.ues.colibri.domain.security.PasswordHasher;
import sv.edu.ues.colibri.persistence.jdbc.JdbcSupport;
import sv.edu.ues.colibri.persistence.jdbc.PersistenceException;
import sv.edu.ues.colibri.persistence.repo.BitacoraRepository;
import sv.edu.ues.colibri.persistence.repo.ContadorRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Autenticación de usuarios con perfil contable (H08.01–H08.02).
 */
public class AuthService {

    public static final int MAX_INTENTOS = 5;
    public static final int MINUTOS_BLOQUEO = 15;
    public static final int MINUTOS_INACTIVIDAD = 15;
    public static final int MINUTOS_VIGENCIA_CLAVE_TEMPORAL = 24 * 60;
    private static final int MAX_MULTIPLICADOR_BLOQUEO = 4;

    // Hash ficticio para igualar el costo de PBKDF2 cuando el usuario no existe o
    // está inactivo; de otro modo el tiempo de respuesta revelaría su existencia.
    private static final byte[] SAL_FICTICIA = PasswordHasher.generarSal();
    private static final byte[] HASH_FICTICIO =
            PasswordHasher.hash("colibri-login-ficticio".toCharArray(), SAL_FICTICIA);

    private final JdbcSupport jdbc;
    private final ContadorRepository contadorRepository = new ContadorRepository();
    private final BitacoraRepository bitacoraRepository = new BitacoraRepository();

    private volatile Contador sesionActual;
    private volatile LocalDateTime ultimoActividad;

    public AuthService(JdbcSupport jdbc) {
        this.jdbc = jdbc;
    }

    public Contador login(String usuario, char[] password) {
        String usuarioNormalizado = usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT);
        DomainException fallo = jdbc.inTransaction(conn -> {
            Contador contador = contadorRepository.buscarPorUsuario(conn, usuarioNormalizado).orElse(null);
            char[] intento = password == null ? new char[0]
                    : Arrays.copyOf(password, password.length);
            if (contador == null || !contador.isActivo()) {
                // Se ejecuta la misma verificación PBKDF2 para no revelar por
                // diferencia de tiempo si el nombre de usuario existe.
                PasswordHasher.verificar(intento, SAL_FICTICIA, HASH_FICTICIO);
                return new DomainException("Credenciales inválidas.");
            }

            LocalDateTime ahora = LocalDateTime.now();
            if (contador.estaBloqueado(ahora)) {
                return new DomainException("Cuenta bloqueada hasta " + contador.getBloqueadoHasta()
                        + ". Intente más tarde.");
            }

            boolean ok = PasswordHasher.verificar(
                    intento,
                    contador.getPasswordSalt(),
                    contador.getPasswordHash());

            if (!ok) {
                contador.setIntentosFallidos(contador.getIntentosFallidos() + 1);
                if (contador.getIntentosFallidos() >= MAX_INTENTOS) {
                    // Bloqueo progresivo: cada bloqueo reciente duplica la espera
                    // (15, 30, 60, 120, 240 min) para mitigar el DoS por intentos
                    // fallidos repetidos contra una cuenta legítima.
                    long bloqueosRecientes = contarBloqueosRecientes(conn, contador.getId());
                    long minutos = MINUTOS_BLOQUEO
                            * (1L << Math.min(bloqueosRecientes, MAX_MULTIPLICADOR_BLOQUEO));
                    contador.setBloqueadoHasta(ahora.plusMinutes(minutos));
                    contador.setIntentosFallidos(0);
                    bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                            contador.getId(), "CUENTA_BLOQUEADA", "CONTADOR",
                            contador.getId().toString(), null, null,
                            "Bloqueo tras " + MAX_INTENTOS + " intentos fallidos"));
                }
                contadorRepository.actualizarSeguridad(conn, contador);
                bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                        contador.getId(), "LOGIN_FALLIDO", "CONTADOR", contador.getId().toString(),
                        null, null, "Intento fallido"));
                return new DomainException("Credenciales inválidas.");
            }

            if (contador.getClaveTemporalExpira() != null
                    && !contador.getClaveTemporalExpira().isAfter(ahora)) {
                contador.setClaveTemporalExpira(null);
                contadorRepository.actualizarSeguridad(conn, contador);
                return new DomainException("La clave temporal emitida por soporte técnico venció. "
                        + "Solicite una nueva en Soporte técnico → Desbloqueo.");
            }

            contador.setIntentosFallidos(0);
            contador.setBloqueadoHasta(null);
            contador.setUltimoAcceso(ahora);
            contadorRepository.actualizarSeguridad(conn, contador);
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), "LOGIN", "CONTADOR", contador.getId().toString(),
                    null, contador.getUsuario(), null));

            this.sesionActual = contador;
            this.ultimoActividad = ahora;
            return null;
        });
        if (fallo != null) {
            throw fallo;
        }
        return sesionActual;
    }

    public void logout() {
        if (sesionActual != null) {
            Contador c = sesionActual;
            jdbc.runInTransaction(conn -> bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    c.getId(), "LOGOUT", "CONTADOR", c.getId().toString(), null, null, null)));
        }
        sesionActual = null;
        ultimoActividad = null;
    }

    public void touch() {
        if (sesionActual != null) {
            ultimoActividad = LocalDateTime.now();
        }
    }

    public boolean sesionExpirada() {
        if (sesionActual == null || ultimoActividad == null) {
            return true;
        }
        return ultimoActividad.plusMinutes(MINUTOS_INACTIVIDAD).isBefore(LocalDateTime.now());
    }

    public Contador exigirSesion() {
        if (sesionActual == null || sesionExpirada()) {
            sesionActual = null;
            throw new DomainException("Sesión no activa o expirada por inactividad.");
        }
        touch();
        return sesionActual;
    }

    public Contador exigirSoporteTecnico() {
        Contador sesion = exigirSesion();
        if (!sesion.esSoporteTecnico()) {
            throw new DomainException("Esta operación está reservada al soporte técnico.");
        }
        return sesion;
    }

    public Contador exigirContador() {
        Contador sesion = exigirSesion();
        if (!sesion.esContador()) {
            throw new DomainException("El soporte técnico no accede a la configuración contable ni a saldos.");
        }
        return sesion;
    }

    /**
     * Re-autentica al usuario de la sesión activa exigiendo su contraseña
     * vigente. Se usa antes de operaciones sensibles como la reapertura de
     * períodos contables ya cerrados.
     *
     * @param clave contraseña a verificar
     * @throws DomainException si no hay sesión o la clave no coincide
     */
    public void exigirClaveActual(char[] clave) {
        Contador sesion = exigirSesion();
        if (!PasswordHasher.verificar(
                clave == null ? new char[0] : Arrays.copyOf(clave, clave.length),
                sesion.getPasswordSalt(), sesion.getPasswordHash())) {
            throw new DomainException("La contraseña no coincide con la del usuario en sesión.");
        }
    }

    public Contador getSesionActual() {
        return sesionActual;
    }

    public List<Contador> listarUsuarios() {
        exigirSoporteTecnico();
        return jdbc.inTransaction(contadorRepository::listar);
    }

    public long contarActivos() {
        exigirSoporteTecnico();
        return jdbc.inTransaction(contadorRepository::contarActivos);
    }

    public String crearUsuario(String usuario, String nombre, char[] password) {
        return crearUsuario(usuario, nombre, null, RolUsuario.CONTADOR, password);
    }

    public String crearUsuario(String usuario, String nombre, String email, RolUsuario rol, char[] password) {
        Contador creador = exigirSoporteTecnico();
        String usuarioNormalizado = usuario == null ? "" : usuario.trim().toLowerCase();
        String nombreNormalizado = nombre == null ? "" : nombre.trim();
        if (!usuarioNormalizado.matches("[a-z0-9._-]{3,64}")) {
            throw new DomainException("El usuario debe tener entre 3 y 64 caracteres: letras, números, punto, guion o guion bajo.");
        }
        if (nombreNormalizado.isBlank() || nombreNormalizado.length() > 120) {
            throw new DomainException("Ingrese un nombre completo de hasta 120 caracteres.");
        }
        if (password == null || password.length < 12 || password.length > 128) {
            throw new DomainException("La contraseña debe tener entre 12 y 128 caracteres.");
        }

        byte[] passwordSalt = PasswordHasher.generarSal();
        byte[] passwordHash = PasswordHasher.hash(Arrays.copyOf(password, password.length), passwordSalt);
        String recoveryCode = PasswordHasher.generarCodigoRecuperacion();
        byte[] recoverySalt = PasswordHasher.generarSal();
        byte[] recoveryHash = PasswordHasher.hash(recoveryCode.toCharArray(), recoverySalt);
        Contador usuarioNuevo = new Contador(UUID.randomUUID(), usuarioNormalizado, nombreNormalizado,
                passwordHash, passwordSalt, 0, null, recoveryHash, recoverySalt, true, null,
                rol == null ? RolUsuario.CONTADOR : rol, email == null ? null : email.trim());

        jdbc.runInTransaction(conn -> {
            if (contadorRepository.buscarPorUsuario(conn, usuarioNormalizado).isPresent()) {
                throw new DomainException("El nombre de usuario ya está registrado.");
            }
            contadorRepository.insertar(conn, usuarioNuevo);
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    creador.getId(), "CREAR_USUARIO", "CONTADOR", usuarioNuevo.getId().toString(),
                    null, usuarioNormalizado, "Usuario contable creado"));
        });
        return recoveryCode;
    }

    public void actualizarUsuario(UUID usuarioId, String nombre, String email, boolean activo) {
        Contador responsable = exigirSoporteTecnico();
        jdbc.runInTransaction(conn -> {
            Contador usuario = contadorRepository.buscarPorId(conn, usuarioId)
                    .orElseThrow(() -> new DomainException("Usuario no encontrado."));
            boolean activoAnterior = usuario.isActivo();
            if (activoAnterior != activo && !activo) {
                // Las mismas reglas que cambiarEstadoUsuario: no auto-desactivarse
                // ni dejar el sistema sin ningún usuario activo.
                if (usuarioId.equals(responsable.getId())) {
                    throw new DomainException("No puede desactivar la cuenta que está utilizando.");
                }
                if (contadorRepository.contarActivos(conn) <= 1) {
                    throw new DomainException("Debe mantenerse al menos un usuario activo.");
                }
            }
            usuario.setNombre(nombre == null ? "" : nombre.trim());
            usuario.setEmail(email == null ? null : email.trim());
            usuario.setActivo(activo);
            contadorRepository.actualizarPerfil(conn, usuario);
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    responsable.getId(),
                    activoAnterior == activo ? "EDITAR_USUARIO"
                            : (activo ? "ACTIVAR_USUARIO" : "DESACTIVAR_USUARIO"),
                    "CONTADOR", usuarioId.toString(),
                    Boolean.toString(activoAnterior), Boolean.toString(activo),
                    "Actualización de usuario desde soporte técnico"));
        });
    }

    public String desbloquearYEmitirClave(UUID usuarioId) {
        Contador responsable = exigirSoporteTecnico();
        return jdbc.inTransaction(conn -> {
            Contador usuario = contadorRepository.buscarPorId(conn, usuarioId)
                    .orElseThrow(() -> new DomainException("Usuario no encontrado."));
            String temporal = PasswordHasher.generarCodigoRecuperacion();
            byte[] salt = PasswordHasher.generarSal();
            byte[] hash = PasswordHasher.hash(temporal.toCharArray(), salt);
            usuario.setPasswordHash(hash);
            usuario.setPasswordSalt(salt);
            usuario.setIntentosFallidos(0);
            usuario.setBloqueadoHasta(null);
            usuario.setActivo(true);
            usuario.setClaveTemporalExpira(
                    LocalDateTime.now().plusMinutes(MINUTOS_VIGENCIA_CLAVE_TEMPORAL));
            contadorRepository.actualizarSeguridad(conn, usuario);
            contadorRepository.actualizarEstado(conn, usuarioId, true);
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    responsable.getId(), "CLAVE_TEMPORAL", "USUARIO", usuarioId.toString(),
                    usuario.getUsuario(), null, "Desbloqueo y clave temporal"));
            return temporal;
        });
    }

    public List<BitacoraEntrada> listarAccesos() {
        exigirSoporteTecnico();
        return jdbc.inTransaction(bitacoraRepository::listarAccesos);
    }

    public void cambiarEstadoUsuario(UUID usuarioId, boolean activo) {
        Contador responsable = exigirSoporteTecnico();
        if (usuarioId == null || usuarioId.equals(responsable.getId())) {
            throw new DomainException("No puede desactivar la cuenta que está utilizando.");
        }
        jdbc.runInTransaction(conn -> {
            Contador usuario = contadorRepository.buscarPorId(conn, usuarioId)
                    .orElseThrow(() -> new DomainException("Usuario no encontrado."));
            if (usuario.isActivo() == activo) {
                return;
            }
            if (!activo && contadorRepository.contarActivos(conn) <= 1) {
                throw new DomainException("Debe mantenerse al menos un usuario activo.");
            }
            contadorRepository.actualizarEstado(conn, usuarioId, activo);
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    responsable.getId(), activo ? "ACTIVAR_USUARIO" : "DESACTIVAR_USUARIO",
                    "CONTADOR", usuarioId.toString(), Boolean.toString(usuario.isActivo()),
                    Boolean.toString(activo), null));
        });
    }

    /**
     * Establece una clave propia para el usuario de la sesión activa. Se usa
     * para reemplazar la clave temporal emitida por soporte técnico; también
     * sirve como cambio de clave voluntario. Exige la contraseña actual para
     * impedir que una sesión abandonada cambie la clave sin conocerla. Limpia
     * la marca temporal para que la clave temporal deje de ser válida.
     *
     * @param claveActual contraseña vigente (la temporal si aún está vigente)
     * @param nuevaPassword nueva contraseña (12 a 128 caracteres)
     */
    public void cambiarClavePropia(char[] claveActual, char[] nuevaPassword) {
        Contador actual = sesionActual;
        if (actual == null) {
            throw new DomainException("No hay una sesión activa.");
        }
        if (nuevaPassword == null || nuevaPassword.length < 12 || nuevaPassword.length > 128) {
            throw new DomainException("La contraseña debe tener entre 12 y 128 caracteres.");
        }
        jdbc.runInTransaction(conn -> {
            Contador usuario = contadorRepository.buscarPorId(conn, actual.getId())
                    .orElseThrow(() -> new DomainException("Usuario no encontrado."));
            if (!PasswordHasher.verificar(
                    claveActual == null ? new char[0] : Arrays.copyOf(claveActual, claveActual.length),
                    usuario.getPasswordSalt(), usuario.getPasswordHash())) {
                throw new DomainException("La contraseña actual no es correcta.");
            }
            if (PasswordHasher.verificar(nuevaPassword, usuario.getPasswordSalt(),
                    usuario.getPasswordHash())) {
                throw new DomainException("La nueva contraseña debe ser distinta de la actual.");
            }
            byte[] salt = PasswordHasher.generarSal();
            usuario.setPasswordHash(PasswordHasher.hash(nuevaPassword, salt));
            usuario.setPasswordSalt(salt);
            usuario.setClaveTemporalExpira(null);
            usuario.setIntentosFallidos(0);
            usuario.setBloqueadoHasta(null);
            contadorRepository.actualizarSeguridad(conn, usuario);
            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    usuario.getId(), "CAMBIO_CLAVE", "CONTADOR", usuario.getId().toString(),
                    null, null, "Clave propia establecida; clave temporal invalidada"));
            sesionActual = usuario;
        });
    }

    /**
     * Cuenta los bloqueos por intentos fallidos registrados en las últimas 24 h
     * para calcular la espera progresiva del bloqueo de cuenta.
     */
    private long contarBloqueosRecientes(Connection conn, UUID usuarioId) {
        String sql = """
                SELECT COUNT(*) FROM bitacora
                WHERE accion = 'CUENTA_BLOQUEADA' AND entidad = 'CONTADOR'
                  AND entidad_id = ? AND fecha_hora > ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, usuarioId.toString());
            ps.setObject(2, LocalDateTime.now().minusHours(24));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Error al contar bloqueos recientes", e);
        }
    }

    /**
     * Recuperación local con código de un solo uso (H08.02).
     * @return nuevo código de recuperación
     */
    public String recuperarConCodigo(String usuario, String codigo, char[] nuevaPassword) {
        String usuarioNormalizado = usuario == null ? "" : usuario.trim().toLowerCase();
        if (usuarioNormalizado.isBlank()) {
            throw new DomainException("Ingrese el nombre de usuario.");
        }
        if (codigo == null || codigo.isBlank()) {
            throw new DomainException("Ingrese el código de recuperación.");
        }
        if (nuevaPassword == null || nuevaPassword.length < 12 || nuevaPassword.length > 128) {
            throw new DomainException("La contraseña debe tener entre 12 y 128 caracteres.");
        }
        return jdbc.inTransaction(conn -> {
            Contador contador = contadorRepository.buscarPorUsuario(conn, usuarioNormalizado)
                    .orElseThrow(() -> new DomainException("Usuario no encontrado."));
            if (contador.getCodigoRecuperacionHash() == null || contador.getCodigoRecuperacionSalt() == null) {
                throw new DomainException("No hay código de recuperación configurado.");
            }
            boolean ok = PasswordHasher.verificar(
                    codigo.toCharArray(),
                    contador.getCodigoRecuperacionSalt(),
                    contador.getCodigoRecuperacionHash());
            if (!ok) {
                throw new DomainException("Código de recuperación inválido.");
            }

            byte[] salt = PasswordHasher.generarSal();
            byte[] hash = PasswordHasher.hash(nuevaPassword, salt);
            String nuevoCodigo = PasswordHasher.generarCodigoRecuperacion();
            byte[] nuevoSalt = PasswordHasher.generarSal();
            byte[] nuevoHash = PasswordHasher.hash(nuevoCodigo.toCharArray(), nuevoSalt);

            contador.setPasswordHash(hash);
            contador.setPasswordSalt(salt);
            contador.setCodigoRecuperacionHash(nuevoHash);
            contador.setCodigoRecuperacionSalt(nuevoSalt);
            contador.setIntentosFallidos(0);
            contador.setBloqueadoHasta(null);
            contador.setClaveTemporalExpira(null);
            contadorRepository.actualizarSeguridad(conn, contador);

            bitacoraRepository.registrar(conn, BitacoraEntrada.de(
                    contador.getId(), "RECUPERACION", "CONTADOR", contador.getId().toString(),
                    null, null, "Contraseña restablecida; código anterior invalidado"));

            this.sesionActual = null;
            this.ultimoActividad = null;
            return nuevoCodigo;
        });
    }
}
