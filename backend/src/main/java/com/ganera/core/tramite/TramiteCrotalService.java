package com.ganera.core.tramite;

import com.ganera.core.contacto.RecursoNoEncontradoException;
import com.ganera.core.explotacion.Animal;
import com.ganera.core.explotacion.AnimalRepository;
import com.ganera.core.explotacion.Explotacion;
import com.ganera.core.tramite.CrotalNormalizador.CrotalNormalizado;
import com.ganera.core.tramite.CrotalNormalizador.TipoCrotal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Crotales de un Tramite (Prompt A1, decision 20). Unico punto que escribe TramiteCrotal.
 *
 * Resolucion de cada crotal indicado contra la Explotacion ACTUAL del Tramite:
 * - Tramite sin Explotacion -> SIN_EXPLOTACION, tal cual, sin enlace.
 * - COMPLETO -> igualdad exacta con Animal.crotal -> EN_INVENTARIO (enlazado) o NO_ENCONTRADO.
 * - INCOMPLETO -> Animales cuyo crotal termina en esos digitos: uno -> EN_INVENTARIO con el crotal
 *   completo y enlazado; varios -> AMBIGUO; ninguno -> NO_ENCONTRADO (los dos ultimos, tal cual).
 * Todas las busquedas de Animal llevan explotacionId Y gestoriaId explicitos: un Animal de otra
 * Explotacion o de otra Gestoria nunca se enlaza ni se usa para completar. Ademas, antes de
 * enlazar se comprueba de forma explicita (espiritu de la decision 15) que el Animal es de la
 * Explotacion del Tramite y de la Gestoria autenticada.
 */
@Service
public class TramiteCrotalService {

    private final TramiteCrotalRepository tramiteCrotalRepository;
    private final AnimalRepository animalRepository;

    public TramiteCrotalService(TramiteCrotalRepository tramiteCrotalRepository, AnimalRepository animalRepository) {
        this.tramiteCrotalRepository = tramiteCrotalRepository;
        this.animalRepository = animalRepository;
    }

    /**
     * Sustituye la lista completa de crotales del Tramite (decision 6). Valida y normaliza TODOS
     * antes de tocar la BD: si alguno es invalido lanza CrotalInvalidoException y no cambia nada.
     * Los duplicados (mismo crotal normalizado) se colapsan manteniendo el primer orden.
     *
     * @throws CrotalInvalidoException si algun crotal no es valido.
     * @throws RecursoNoEncontradoException si el Tramite (o su Explotacion) no es de gestoriaId.
     */
    @Transactional
    public List<TramiteCrotal> reemplazarCrotales(Tramite tramite, List<String> crotalesIndicados, Long gestoriaId) {
        comprobarTramiteDeLaGestoria(tramite, gestoriaId);
        Map<String, CrotalNormalizado> normalizados = new LinkedHashMap<>();
        for (String indicado : crotalesIndicados != null ? crotalesIndicados : List.<String>of()) {
            CrotalNormalizado normalizado = CrotalNormalizador.normalizar(indicado);
            normalizados.putIfAbsent(normalizado.valor(), normalizado);
        }

        // Borrado + flush ANTES de insertar: con IDENTITY los INSERT salen al momento pero los
        // DELETE esperan al flush, y chocarian con UNIQUE(tramite_id, crotal_indicado).
        List<TramiteCrotal> anteriores =
                tramiteCrotalRepository.findByTramiteIdAndGestoriaIdOrderByIdAsc(tramite.getId(), gestoriaId);
        tramiteCrotalRepository.deleteAll(anteriores);
        tramiteCrotalRepository.flush();

        List<TramiteCrotal> filas = new ArrayList<>();
        for (CrotalNormalizado normalizado : normalizados.values()) {
            TramiteCrotal fila = new TramiteCrotal();
            fila.setGestoria(tramite.getGestoria());
            fila.setTramite(tramite);
            fila.setCrotalIndicado(normalizado.valor());
            resolver(fila, normalizado, tramite, gestoriaId);
            filas.add(tramiteCrotalRepository.save(fila));
        }
        return filas;
    }

    /**
     * Vuelve a resolver cada crotal del Tramite contra su Explotacion actual (tras cambiarla),
     * SIEMPRE desde crotalIndicado, nunca desde el crotal completado para la Explotacion anterior.
     *
     * @throws RecursoNoEncontradoException si el Tramite (o su Explotacion) no es de gestoriaId.
     */
    @Transactional
    public void recalcularEnlaces(Tramite tramite, Long gestoriaId) {
        comprobarTramiteDeLaGestoria(tramite, gestoriaId);
        for (TramiteCrotal fila : tramiteCrotalRepository.findByTramiteIdAndGestoriaIdOrderByIdAsc(tramite.getId(), gestoriaId)) {
            // crotalIndicado ya se guardo normalizado; se reclasifica con las mismas reglas.
            resolver(fila, CrotalNormalizador.normalizar(fila.getCrotalIndicado()), tramite, gestoriaId);
            tramiteCrotalRepository.save(fila);
        }
    }

    /** Crotales de varios Tramites en UNA consulta (listado sin N+1), agrupados por Tramite y en
     * el orden en que se indicaron. Los Tramites sin crotales no aparecen en el mapa. */
    @Transactional(readOnly = true)
    public Map<Long, List<TramiteCrotalResponse>> crotalesPorTramite(Collection<Long> tramiteIds, Long gestoriaId) {
        Map<Long, List<TramiteCrotalResponse>> porTramite = new LinkedHashMap<>();
        if (tramiteIds == null || tramiteIds.isEmpty()) {
            return porTramite;
        }
        for (TramiteCrotal fila : tramiteCrotalRepository.findByTramiteIdInAndGestoriaIdOrderByIdAsc(tramiteIds, gestoriaId)) {
            porTramite.computeIfAbsent(fila.getTramite().getId(), id -> new ArrayList<>())
                    .add(TramiteCrotalResponse.from(fila));
        }
        return porTramite;
    }

    /**
     * Resuelve el crotal de la madre del Tramite (V30, plan 2026-11-11) contra su Explotacion actual,
     * con EXACTAMENTE el mismo mecanismo que los demas crotales (metodo compartido {@link #resolver}).
     * Lee crotalMadreIndicado: null/blank -> ambas columnas null; si no, normaliza, resuelve y fija
     * crotal_madre (crotal completado) + crotal_madre_resolucion. Un crotal de madre invalido no rompe
     * (el PATCH ya valida el formato): se queda sin resolver (ambas columnas null). No persiste el
     * Tramite: lo hace quien llama (extraccion, PATCH, alta) en su propia transaccion. El aislamiento
     * lo garantizan los finders de {@link #resolver}, que llevan explotacionId Y gestoriaId explicitos.
     */
    @Transactional
    public void resolverMadre(Tramite tramite, Long gestoriaId) {
        String indicado = tramite.getCrotalMadreIndicado();
        if (indicado == null || indicado.isBlank()) {
            tramite.setCrotalMadre(null);
            tramite.setCrotalMadreResolucion(null);
            return;
        }
        CrotalNormalizado normalizado;
        try {
            normalizado = CrotalNormalizador.normalizar(indicado);
        } catch (CrotalInvalidoException e) {
            tramite.setCrotalMadre(null);
            tramite.setCrotalMadreResolucion(null);
            return;
        }
        ResultadoResolucionCrotal resultado = resolver(normalizado, tramite.getExplotacion(), gestoriaId);
        tramite.setCrotalMadre(resultado.crotalCompletado());
        tramite.setCrotalMadreResolucion(resultado.resolucion());
    }

    private void resolver(TramiteCrotal fila, CrotalNormalizado normalizado, Tramite tramite, Long gestoriaId) {
        ResultadoResolucionCrotal resultado = resolver(normalizado, tramite.getExplotacion(), gestoriaId);
        fila.setCrotal(resultado.crotalCompletado());
        fila.setAnimal(resultado.animal());
        fila.setResolucion(resultado.resolucion());
    }

    /**
     * Mecanismo UNICO de resolucion de un crotal normalizado contra una Explotacion, compartido por
     * los crotales de la lista del Tramite y por el crotal de la madre (V30):
     * - Explotacion null -> SIN_EXPLOTACION, crotal completado = lo indicado, sin animal.
     * - COMPLETO -> igualdad exacta -> EN_INVENTARIO (crotal del inventario, animal) o NO_ENCONTRADO.
     * - INCOMPLETO -> por sufijo: uno -> EN_INVENTARIO; varios -> AMBIGUO; ninguno -> NO_ENCONTRADO.
     * Las dos busquedas llevan explotacionId Y gestoriaId explicitos: un Animal de otra Explotacion o
     * de otra Gestoria nunca se enlaza ni se usa para completar (aislamiento). Quitar cualquiera de
     * los dos filtros rompe el aislamiento tanto de los crotales normales como del de la madre.
     */
    ResultadoResolucionCrotal resolver(CrotalNormalizado normalizado, Explotacion explotacion, Long gestoriaId) {
        if (explotacion == null) {
            return new ResultadoResolucionCrotal(ResolucionCrotal.SIN_EXPLOTACION, normalizado.valor(), null);
        }
        if (normalizado.tipo() == TipoCrotal.COMPLETO) {
            Optional<Animal> animal = animalRepository.findByExplotacionIdAndGestoriaIdAndCrotal(
                    explotacion.getId(), gestoriaId, normalizado.valor());
            return animal.map(a -> enlazado(a, explotacion, gestoriaId))
                    .orElseGet(() -> new ResultadoResolucionCrotal(
                            ResolucionCrotal.NO_ENCONTRADO, normalizado.valor(), null));
        }
        List<Animal> candidatos = animalRepository.findByExplotacionIdAndGestoriaIdAndCrotalEndingWithOrderByIdAsc(
                explotacion.getId(), gestoriaId, normalizado.valor());
        if (candidatos.size() == 1) {
            return enlazado(candidatos.get(0), explotacion, gestoriaId);
        }
        ResolucionCrotal resolucion = candidatos.isEmpty() ? ResolucionCrotal.NO_ENCONTRADO : ResolucionCrotal.AMBIGUO;
        return new ResultadoResolucionCrotal(resolucion, normalizado.valor(), null);
    }

    private static ResultadoResolucionCrotal enlazado(Animal animal, Explotacion explotacion, Long gestoriaId) {
        Long explotacionAnimal = animal.getExplotacion() != null ? animal.getExplotacion().getId() : null;
        Long gestoriaAnimal = animal.getGestoria() != null ? animal.getGestoria().getId() : null;
        if (!Objects.equals(explotacionAnimal, explotacion.getId()) || !Objects.equals(gestoriaAnimal, gestoriaId)) {
            // No deberia pasar nunca (los finders filtran por ambos); si pasa, no se enlaza nada.
            throw new RecursoNoEncontradoException();
        }
        return new ResultadoResolucionCrotal(ResolucionCrotal.EN_INVENTARIO, animal.getCrotal(), animal);
    }

    /** Resultado de resolver un crotal normalizado: su resolucion, el crotal completado (el del
     * inventario si EN_INVENTARIO; si no, el propio indicado) y el Animal enlazado (solo EN_INVENTARIO). */
    record ResultadoResolucionCrotal(ResolucionCrotal resolucion, String crotalCompletado, Animal animal) {
    }

    /** El Tramite y su Explotacion (si tiene) deben ser de la Gestoria autenticada; si no, 404
     * como el resto (no se distingue "no existe" de "es de otra Gestoria"). */
    private static void comprobarTramiteDeLaGestoria(Tramite tramite, Long gestoriaId) {
        Long gestoriaTramite = tramite.getGestoria() != null ? tramite.getGestoria().getId() : null;
        if (gestoriaId == null || tramite.getId() == null || !Objects.equals(gestoriaTramite, gestoriaId)) {
            throw new RecursoNoEncontradoException();
        }
        Explotacion explotacion = tramite.getExplotacion();
        if (explotacion != null) {
            Long gestoriaExplotacion = explotacion.getGestoria() != null ? explotacion.getGestoria().getId() : null;
            if (!Objects.equals(gestoriaExplotacion, gestoriaId)) {
                throw new RecursoNoEncontradoException();
            }
        }
    }
}
