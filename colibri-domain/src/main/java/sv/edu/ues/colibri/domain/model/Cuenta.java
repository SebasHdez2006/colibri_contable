package sv.edu.ues.colibri.domain.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa una cuenta contable del catálogo institucional.
 *
 * La entidad concentra la información estructural de la cuenta, su naturaleza,
 * vigencia y si admite o no movimientos contables.
 */
public final class Cuenta {

    private final UUID id;
    private final String codigo;
    private String nombre;
    private String codigoPadre;
    private final int nivel;
    private NaturalezaCuenta naturaleza;
    private String rubro;
    private EstadoCuenta estado;
    private LocalDate vigenciaDesde;
    private LocalDate vigenciaHasta;
    private boolean admiteMovimiento;

    public Cuenta(UUID id, String codigo, String nombre, String codigoPadre, int nivel,
                  NaturalezaCuenta naturaleza, String rubro, EstadoCuenta estado,
                  LocalDate vigenciaDesde, LocalDate vigenciaHasta, boolean admiteMovimiento) {
        this.id = Objects.requireNonNull(id);
        this.codigo = Objects.requireNonNull(codigo);
        this.nombre = Objects.requireNonNull(nombre);
        this.codigoPadre = codigoPadre;
        this.nivel = nivel;
        this.naturaleza = Objects.requireNonNull(naturaleza);
        this.rubro = rubro;
        this.estado = Objects.requireNonNull(estado);
        this.vigenciaDesde = vigenciaDesde;
        this.vigenciaHasta = vigenciaHasta;
        this.admiteMovimiento = admiteMovimiento;
    }

    /**
     * Determina si un código pertenece al nivel de movimiento.
     *
     * En este modelo, los códigos de seis dígitos representan cuentas hojas.
     *
     * @param codigo código de cuenta a evaluar
     * @return {@code true} si el código tiene seis dígitos numéricos
     */
    public static boolean esCodigoMovimiento(String codigo) {
        return codigo != null && codigo.length() == 6 && codigo.chars().allMatch(Character::isDigit);
    }

    /**
     * Indica si la cuenta puede recibir registros de movimiento.
     *
     * @return {@code true} cuando la cuenta está activa, admite movimientos y su
     *         código corresponde a una cuenta hoja
     */
    public boolean puedeRecibirMovimiento() {
        return admiteMovimiento
                && esCodigoMovimiento(codigo)
                && estado == EstadoCuenta.ACTIVA;
    }

    public UUID getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = Objects.requireNonNull(nombre);
    }

    public String getCodigoPadre() {
        return codigoPadre;
    }

    public int getNivel() {
        return nivel;
    }

    public NaturalezaCuenta getNaturaleza() {
        return naturaleza;
    }

    public void setNaturaleza(NaturalezaCuenta naturaleza) {
        this.naturaleza = Objects.requireNonNull(naturaleza);
    }

    public String getRubro() {
        return rubro;
    }

    public void setRubro(String rubro) {
        this.rubro = rubro;
    }

    public EstadoCuenta getEstado() {
        return estado;
    }

    public void setEstado(EstadoCuenta estado) {
        this.estado = Objects.requireNonNull(estado);
    }

    public LocalDate getVigenciaDesde() {
        return vigenciaDesde;
    }

    public LocalDate getVigenciaHasta() {
        return vigenciaHasta;
    }

    public boolean isAdmiteMovimiento() {
        return admiteMovimiento;
    }

    public void setAdmiteMovimiento(boolean admiteMovimiento) {
        this.admiteMovimiento = admiteMovimiento;
    }
}
