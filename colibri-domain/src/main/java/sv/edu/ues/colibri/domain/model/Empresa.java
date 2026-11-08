package sv.edu.ues.colibri.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa la información maestra de la empresa configurada en el sistema.
 */
public final class Empresa {

    private final UUID id;
    private String razonSocial;
    private String nombreComercial;
    private String nit;
    private String nrc;
    private String actividadEconomica;
    private String direccionFiscal;
    private String logoRuta;
    private String representanteLegal;
    private String duiRepresentante;
    private String contadorNombre;
    private String registroCvpcpa;
    private String moneda;
    private String zonaHoraria;
    private LocalDateTime actualizadoEn;

    /**
     * Crea una empresa con los datos mínimos de identificación y configuración.
     *
     * @param id identificador único
     * @param razonSocial razón social
     * @param nombreComercial nombre comercial opcional
     * @param nit número de identificación tributaria
     * @param nrc número de registro de contribuyente
     * @param moneda código ISO de moneda
     * @param zonaHoraria zona horaria operativa
     * @param actualizadoEn fecha de última actualización
     */
    public Empresa(UUID id, String razonSocial, String nombreComercial, String nit, String nrc,
                   String moneda, String zonaHoraria, LocalDateTime actualizadoEn) {
        this(id, razonSocial, nombreComercial, nit, nrc, null, null, null, null, null, null, null,
                moneda, zonaHoraria, actualizadoEn);
    }

    /**
     * Crea una empresa con el detalle extendido de perfil institucional.
     *
     * @param id identificador único
     * @param razonSocial razón social
     * @param nombreComercial nombre comercial opcional
     * @param nit número de identificación tributaria
     * @param nrc número de registro de contribuyente
     * @param actividadEconomica actividad económica principal
     * @param direccionFiscal dirección fiscal
     * @param logoRuta ruta del logo corporativo
     * @param representanteLegal nombre del representante legal
     * @param duiRepresentante DUI del representante legal
     * @param contadorNombre nombre del contador responsable
     * @param registroCvpcpa registro profesional del contador
     * @param moneda código ISO de moneda
     * @param zonaHoraria zona horaria operativa
     * @param actualizadoEn fecha de última actualización
     */
    public Empresa(UUID id, String razonSocial, String nombreComercial, String nit, String nrc,
                   String actividadEconomica, String direccionFiscal, String logoRuta,
                   String representanteLegal, String duiRepresentante, String contadorNombre,
                   String registroCvpcpa, String moneda, String zonaHoraria, LocalDateTime actualizadoEn) {
        this.id = Objects.requireNonNull(id);
        this.razonSocial = Objects.requireNonNull(razonSocial);
        this.nombreComercial = nombreComercial;
        this.nit = nit;
        this.nrc = nrc;
        this.actividadEconomica = actividadEconomica;
        this.direccionFiscal = direccionFiscal;
        this.logoRuta = logoRuta;
        this.representanteLegal = representanteLegal;
        this.duiRepresentante = duiRepresentante;
        this.contadorNombre = contadorNombre;
        this.registroCvpcpa = registroCvpcpa;
        this.moneda = moneda == null ? "USD" : moneda;
        this.zonaHoraria = zonaHoraria == null ? "America/El_Salvador" : zonaHoraria;
        this.actualizadoEn = actualizadoEn;
    }

    /**
     * Obtiene el identificador de la empresa.
     *
     * @return UUID de la empresa
     */
    public UUID getId() {
        return id;
    }

    /**
     * Obtiene la razón social.
     *
     * @return razón social
     */
    public String getRazonSocial() {
        return razonSocial;
    }

    /**
     * Actualiza la razón social.
     *
     * @param razonSocial nueva razón social
     */
    public void setRazonSocial(String razonSocial) {
        this.razonSocial = Objects.requireNonNull(razonSocial);
    }

    /**
     * Obtiene el nombre comercial.
     *
     * @return nombre comercial o {@code null}
     */
    public String getNombreComercial() {
        return nombreComercial;
    }

    /**
     * Actualiza el nombre comercial.
     *
     * @param nombreComercial nuevo nombre comercial
     */
    public void setNombreComercial(String nombreComercial) {
        this.nombreComercial = nombreComercial;
    }

    /**
     * Obtiene el NIT.
     *
     * @return NIT de la empresa
     */
    public String getNit() {
        return nit;
    }

    /**
     * Actualiza el NIT.
     *
     * @param nit nuevo NIT
     */
    public void setNit(String nit) {
        this.nit = nit;
    }

    /**
     * Obtiene el NRC.
     *
     * @return NRC de la empresa
     */
    public String getNrc() {
        return nrc;
    }

    /**
     * Actualiza el NRC.
     *
     * @param nrc nuevo NRC
     */
    public void setNrc(String nrc) {
        this.nrc = nrc;
    }

    /**
     * Obtiene la actividad económica.
     *
     * @return actividad económica o {@code null}
     */
    public String getActividadEconomica() {
        return actividadEconomica;
    }

    /**
     * Actualiza la actividad económica.
     *
     * @param actividadEconomica nueva actividad
     */
    public void setActividadEconomica(String actividadEconomica) {
        this.actividadEconomica = actividadEconomica;
    }

    /**
     * Obtiene la dirección fiscal.
     *
     * @return dirección fiscal o {@code null}
     */
    public String getDireccionFiscal() {
        return direccionFiscal;
    }

    /**
     * Actualiza la dirección fiscal.
     *
     * @param direccionFiscal nueva dirección fiscal
     */
    public void setDireccionFiscal(String direccionFiscal) {
        this.direccionFiscal = direccionFiscal;
    }

    /**
     * Obtiene la ruta del logo.
     *
     * @return ruta del logo o {@code null}
     */
    public String getLogoRuta() {
        return logoRuta;
    }

    /**
     * Actualiza la ruta del logo.
     *
     * @param logoRuta nueva ruta del logo
     */
    public void setLogoRuta(String logoRuta) {
        this.logoRuta = logoRuta;
    }

    /**
     * Obtiene el representante legal.
     *
     * @return nombre del representante legal o {@code null}
     */
    public String getRepresentanteLegal() {
        return representanteLegal;
    }

    /**
     * Actualiza el representante legal.
     *
     * @param representanteLegal nuevo nombre del representante legal
     */
    public void setRepresentanteLegal(String representanteLegal) {
        this.representanteLegal = representanteLegal;
    }

    /**
     * Obtiene el DUI del representante legal.
     *
     * @return DUI o {@code null}
     */
    public String getDuiRepresentante() {
        return duiRepresentante;
    }

    /**
     * Actualiza el DUI del representante legal.
     *
     * @param duiRepresentante nuevo DUI
     */
    public void setDuiRepresentante(String duiRepresentante) {
        this.duiRepresentante = duiRepresentante;
    }

    /**
     * Obtiene el nombre del contador responsable.
     *
     * @return nombre del contador o {@code null}
     */
    public String getContadorNombre() {
        return contadorNombre;
    }

    /**
     * Actualiza el nombre del contador responsable.
     *
     * @param contadorNombre nuevo nombre del contador
     */
    public void setContadorNombre(String contadorNombre) {
        this.contadorNombre = contadorNombre;
    }

    /**
     * Obtiene el registro CVPCPA.
     *
     * @return registro profesional o {@code null}
     */
    public String getRegistroCvpcpa() {
        return registroCvpcpa;
    }

    /**
     * Actualiza el registro CVPCPA.
     *
     * @param registroCvpcpa nuevo registro profesional
     */
    public void setRegistroCvpcpa(String registroCvpcpa) {
        this.registroCvpcpa = registroCvpcpa;
    }

    /**
     * Obtiene la moneda operativa.
     *
     * @return código de moneda
     */
    public String getMoneda() {
        return moneda;
    }

    /**
     * Obtiene la zona horaria operativa.
     *
     * @return zona horaria
     */
    public String getZonaHoraria() {
        return zonaHoraria;
    }

    /**
     * Obtiene la fecha de última actualización.
     *
     * @return fecha y hora de actualización
     */
    public LocalDateTime getActualizadoEn() {
        return actualizadoEn;
    }

    /**
     * Actualiza la fecha de última modificación.
     *
     * @param actualizadoEn nuevo instante de actualización
     */
    public void setActualizadoEn(LocalDateTime actualizadoEn) {
        this.actualizadoEn = actualizadoEn;
    }
}
