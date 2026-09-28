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

    private void resolver(TramiteCrotal fila, CrotalNormalizado normalizado, Tramite tramite, Long gestoriaId) {
        Explotacion explotacion = tramite.getExplotacion();
        if (explotacion == null) {
            sinEnlace(fila, normalizado, ResolucionCrotal.SIN_EXPLOTACION);
            return;
        }
        if (normalizado.tipo() == TipoCrotal.COMPLETO) {
            Optional<Animal> animal = animalRepository.findByExplotacionIdAndGestoriaIdAndCrotal(
                    explotacion.getId(), gestoriaId, normalizado.valor());
            if (animal.isPresent()) {
                enlazar(fila, animal.get(), tramite, gestoriaId);
            } else {
                sinEnlace(fila, normalizado, ResolucionCrotal.NO_ENCONTRADO);
            }
            return;
        }
        List<Animal> candidatos = animalRepository.findByExplotacionIdAndGestoriaIdAndCrotalEndingWithOrderByIdAsc(
                explotacion.getId(), gestoriaId, normalizado.valor());
        if (candidatos.size() == 1) {
            enlazar(fila, candidatos.get(0), tramite, gestoriaId);
        } else {
            sinEnlace(fila, normalizado, candidatos.isEmpty() ? ResolucionCrotal.NO_ENCONTRADO : ResolucionCrotal.AMBIGUO);
        }
    }

    private static void sinEnlace(TramiteCrotal fila, CrotalNormalizado normalizado, ResolucionCrotal resolucion) {
        fila.setCrotal(normalizado.valor());
        fila.setAnimal(null);
        fila.setResolucion(resolucion);
    }

    private static void enlazar(TramiteCrotal fila, Animal animal, Tramite tramite, Long gestoriaId) {
        Long explotacionAnimal = animal.getExplotacion() != null ? animal.getExplotacion().getId() : null;
        Long gestoriaAnimal = animal.getGestoria() != null ? animal.getGestoria().getId() : null;
        if (!Objects.equals(explotacionAnimal, tramite.getExplotacion().getId())
                || !Objects.equals(gestoriaAnimal, gestoriaId)) {
            // No deberia pasar nunca (los finders filtran por ambos); si pasa, no se enlaza nada.
            throw new RecursoNoEncontradoException();
        }
        fila.setCrotal(animal.getCrotal());
        fila.setAnimal(animal);
        fila.setResolucion(ResolucionCrotal.EN_INVENTARIO);
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
