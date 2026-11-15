package sv.edu.ues.colibri.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa a un usuario del sistema con credenciales y perfil funcional.
 */
public final class Contador {

    private final UUID id;
    private String usuario;
    private String nombre;
    private byte[] passwordHash;
    private byte[] passwordSalt;
    private int intentosFallidos;
    private LocalDateTime bloqueadoHasta;
    private byte[] codigoRecuperacionHash;
    private byte[] codigoRecuperacionSalt;
    private boolean activo;
    private LocalDateTime ultimoAcceso;
    private RolUsuario rol;
    private String email;

    /**
     * Crea un usuario con rol predeterminado de contador.
     *
     * @param id identificador único del usuario
     * @param usuario nombre de usuario
     * @param nombre nombre completo
     * @param passwordHash hash de la contraseña
     * @param passwordSalt sal utilizado para la contraseña
     * @param intentosFallidos cantidad acumulada de fallos
     * @param bloqueadoHasta instante hasta el que permanece bloqueado
     * @param codigoRecuperacionHash hash del código de recuperación
     * @param codigoRecuperacionSalt sal del código de recuperación
     * @param activo indica si la cuenta está activa
     * @param ultimoAcceso último acceso registrado
     */
    public Contador(UUID id, String usuario, String nombre, byte[] passwordHash, byte[] passwordSalt,
                    int intentosFallidos, LocalDateTime bloqueadoHasta,
                    byte[] codigoRecuperacionHash, byte[] codigoRecuperacionSalt,
                    boolean activo, LocalDateTime ultimoAcceso) {
        this(id, usuario, nombre, passwordHash, passwordSalt, intentosFallidos, bloqueadoHasta,
                codigoRecuperacionHash, codigoRecuperacionSalt, activo, ultimoAcceso,
                RolUsuario.CONTADOR, null);
    }

    /**
     * Crea un usuario con rol y correo explícitos.
     *
     * @param id identificador único del usuario
     * @param usuario nombre de usuario
     * @param nombre nombre completo
     * @param passwordHash hash de la contraseña
     * @param passwordSalt sal utilizado para la contraseña
     * @param intentosFallidos cantidad acumulada de fallos
     * @param bloqueadoHasta instante hasta el que permanece bloqueado
     * @param codigoRecuperacionHash hash del código de recuperación
     * @param codigoRecuperacionSalt sal del código de recuperación
     * @param activo indica si la cuenta está activa
     * @param ultimoAcceso último acceso registrado
     * @param rol rol funcional asignado
     * @param email correo electrónico opcional
     */
    public Contador(UUID id, String usuario, String nombre, byte[] passwordHash, byte[] passwordSalt,
                    int intentosFallidos, LocalDateTime bloqueadoHasta,
                    byte[] codigoRecuperacionHash, byte[] codigoRecuperacionSalt,
                    boolean activo, LocalDateTime ultimoAcceso, RolUsuario rol, String email) {
        this.id = Objects.requireNonNull(id);
        this.usuario = Objects.requireNonNull(usuario);
        this.nombre = Objects.requireNonNull(nombre);
        this.passwordHash = Objects.requireNonNull(passwordHash);
        this.passwordSalt = Objects.requireNonNull(passwordSalt);
        this.intentosFallidos = intentosFallidos;
        this.bloqueadoHasta = bloqueadoHasta;
        this.codigoRecuperacionHash = codigoRecuperacionHash;
        this.codigoRecuperacionSalt = codigoRecuperacionSalt;
        this.activo = activo;
        this.ultimoAcceso = ultimoAcceso;
        this.rol = rol == null ? RolUsuario.CONTADOR : rol;
        this.email = email;
    }

    /**
     * Indica si el usuario pertenece al soporte técnico.
     *
     * @return {@code true} cuando el rol es soporte técnico
     */
    public boolean esSoporteTecnico() {
        return rol == RolUsuario.SOPORTE_TECNICO;
    }

    /**
     * Indica si el usuario pertenece al rol contable operativo.
     *
     * @return {@code true} cuando el rol es contador
     */
    public boolean esContador() {
        return rol == RolUsuario.CONTADOR;
    }

    /**
     * Determina si la cuenta sigue bloqueada al instante indicado.
     *
     * @param ahora instante de consulta
     * @return {@code true} si el bloqueo sigue vigente
     */
    public boolean estaBloqueado(LocalDateTime ahora) {
        return bloqueadoHasta != null && ahora.isBefore(bloqueadoHasta);
    }

    /**
     * Obtiene el identificador del usuario.
     *
     * @return UUID del usuario
     */
    public UUID getId() {
        return id;
    }

    /**
     * Obtiene el nombre de usuario.
     *
     * @return nombre de usuario
     */
    public String getUsuario() {
        return usuario;
    }

    /**
     * Obtiene el nombre completo.
     *
     * @return nombre visible del usuario
     */
    public String getNombre() {
        return nombre;
    }

    /**
     * Actualiza el nombre completo.
     *
     * @param nombre nuevo nombre
     */
    public void setNombre(String nombre) {
        this.nombre = Objects.requireNonNull(nombre);
    }

    /**
     * Obtiene el hash de la contraseña.
     *
     * @return hash almacenado
     */
    public byte[] getPasswordHash() {
        return passwordHash;
    }

    /**
     * Actualiza el hash de la contraseña.
     *
     * @param passwordHash nuevo hash
     */
    public void setPasswordHash(byte[] passwordHash) {
        this.passwordHash = Objects.requireNonNull(passwordHash);
    }

    /**
     * Obtiene la sal de la contraseña.
     *
     * @return sal almacenada
     */
    public byte[] getPasswordSalt() {
        return passwordSalt;
    }

    /**
     * Actualiza la sal de la contraseña.
     *
     * @param passwordSalt nueva sal
     */
    public void setPasswordSalt(byte[] passwordSalt) {
        this.passwordSalt = Objects.requireNonNull(passwordSalt);
    }

    /**
     * Obtiene la cantidad de intentos fallidos.
     *
     * @return número de fallos acumulados
     */
    public int getIntentosFallidos() {
        return intentosFallidos;
    }

    /**
     * Actualiza la cantidad de intentos fallidos.
     *
     * @param intentosFallidos nuevo contador de fallos
     */
    public void setIntentosFallidos(int intentosFallidos) {
        this.intentosFallidos = intentosFallidos;
    }

    /**
     * Obtiene el instante hasta el que la cuenta permanece bloqueada.
     *
     * @return fecha y hora de desbloqueo o {@code null}
     */
    public LocalDateTime getBloqueadoHasta() {
        return bloqueadoHasta;
    }

    /**
     * Actualiza el instante de bloqueo.
     *
     * @param bloqueadoHasta nueva fecha límite de bloqueo
     */
    public void setBloqueadoHasta(LocalDateTime bloqueadoHasta) {
        this.bloqueadoHasta = bloqueadoHasta;
    }

    /**
     * Obtiene el hash del código de recuperación.
     *
     * @return hash del código o {@code null}
     */
    public byte[] getCodigoRecuperacionHash() {
        return codigoRecuperacionHash;
    }

    /**
     * Actualiza el hash del código de recuperación.
     *
     * @param codigoRecuperacionHash nuevo hash
     */
    public void setCodigoRecuperacionHash(byte[] codigoRecuperacionHash) {
        this.codigoRecuperacionHash = codigoRecuperacionHash;
    }

    /**
     * Obtiene la sal del código de recuperación.
     *
     * @return sal del código o {@code null}
     */
    public byte[] getCodigoRecuperacionSalt() {
        return codigoRecuperacionSalt;
    }

    /**
     * Actualiza la sal del código de recuperación.
     *
     * @param codigoRecuperacionSalt nueva sal
     */
    public void setCodigoRecuperacionSalt(byte[] codigoRecuperacionSalt) {
        this.codigoRecuperacionSalt = codigoRecuperacionSalt;
    }

    /**
     * Indica si la cuenta está activa.
     *
     * @return {@code true} cuando el usuario puede autenticarse
     */
    public boolean isActivo() {
        return activo;
    }

    /**
     * Actualiza el estado activo de la cuenta.
     *
     * @param activo nuevo estado
     */
    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    /**
     * Obtiene el último acceso registrado.
     *
     * @return instante del último acceso
     */
    public LocalDateTime getUltimoAcceso() {
        return ultimoAcceso;
    }

    /**
     * Actualiza el último acceso.
     *
     * @param ultimoAcceso nuevo instante de acceso
     */
    public void setUltimoAcceso(LocalDateTime ultimoAcceso) {
        this.ultimoAcceso = ultimoAcceso;
    }

    /**
     * Obtiene el rol funcional asignado.
     *
     * @return rol del usuario
     */
    public RolUsuario getRol() {
        return rol;
    }

    /**
     * Actualiza el rol funcional, manteniendo contador como valor por defecto.
     *
     * @param rol nuevo rol
     */
    public void setRol(RolUsuario rol) {
        this.rol = rol == null ? RolUsuario.CONTADOR : rol;
    }

    /**
     * Obtiene el correo electrónico.
     *
     * @return correo o {@code null}
     */
    public String getEmail() {
        return email;
    }

    /**
     * Actualiza el correo electrónico.
     *
     * @param email correo nuevo o {@code null}
     */
    public void setEmail(String email) {
        this.email = email;
    }
}
