
package com.example.devcoast.controller;

import com.example.devcoast.config.RoleGuard;
import com.example.devcoast.model.Empleo;
import com.example.devcoast.model.Solicitud;
import com.example.devcoast.model.Usuario;
import com.example.devcoast.repository.EmpleoRepository;
import com.example.devcoast.repository.SolicitudRepository;
import com.example.devcoast.repository.UsuarioRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/mobile/v1")
public class MobilePostulacionesController {

    private final SolicitudRepository solicitudRepository;
    private final EmpleoRepository empleoRepository;
    private final UsuarioRepository usuarioRepository;

    public MobilePostulacionesController(
            SolicitudRepository solicitudRepository,
            EmpleoRepository empleoRepository,
            UsuarioRepository usuarioRepository) {

        this.solicitudRepository = solicitudRepository;
        this.empleoRepository = empleoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // 1. ENVIAR UNA POSTULACIÓN
    @PostMapping("/jobs/{id}/apply")
    public ResponseEntity<?> postularse(
            @PathVariable String id,
            @RequestBody Map<String, String> datos,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null ||
                session.getAttribute("usuarioActualId") == null) {
            return ResponseEntity.status(401).body(
                    Map.of("error", "Debes iniciar sesión")
            );
        }

        if (!RoleGuard.TIPO_TALENTO.equals(
                session.getAttribute("usuarioActualTipo"))) {
            return ResponseEntity.status(403).body(
                    Map.of("error",
                            "Solo los talentos pueden postularse")
            );
        }

        String usuarioId = session
                .getAttribute("usuarioActualId").toString();

        Usuario usuario = usuarioRepository.findById(
                usuarioId
        ).orElse(null);

        if (usuario == null) {
            return ResponseEntity.status(401).body(
                    Map.of("error", "Usuario no encontrado")
            );
        }

        Empleo empleo = empleoRepository.findById(id)
                .orElse(null);

        if (empleo == null) {
            return ResponseEntity.status(404).body(
                    Map.of("error", "Empleo no encontrado")
            );
        }

        if (!Objects.equals(empleo.getEstado(), "ACTIVO")
                && empleo.getEstado() != null) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "La oferta no está activa")
            );
        }

        boolean yaExiste = solicitudRepository
                .findByUsuarioIdAndEmpleoId(usuarioId, id)
                .isPresent();

        if (yaExiste) {
            return ResponseEntity.status(409).body(
                    Map.of("error",
                            "Ya te postulaste a este empleo")
            );
        }

        String mensaje = datos == null
                ? ""
                : Objects.toString(
                        datos.get("mensaje"), ""
                ).trim();

        if (mensaje.length() > 2000) {
            return ResponseEntity.badRequest().body(
                    Map.of("error",
                            "El mensaje supera los 2000 caracteres")
            );
        }

        Solicitud solicitud = new Solicitud();

        // Datos del talento
        solicitud.setUsuarioId(usuario.getId());
        solicitud.setUsuarioCorreo(usuario.getCorreo());
        solicitud.setUsuarioNombre(usuario.getNombre());

        // Datos de la oferta
        solicitud.setEmpleoId(empleo.getId());
        solicitud.setEmpleoTitulo(empleo.getTitulo());
        solicitud.setEmpleoCategoria(empleo.getCategoria());
        solicitud.setEmpleoSeniority(empleo.getSeniority());
        solicitud.setEmpleoModalidad(empleo.getModalidad());
        solicitud.setEmpleoContrato(empleo.getContrato());

        // Datos de la empresa
        solicitud.setEmpleoEmpresaNombre(
                empleo.getEmpresaNombre());
        solicitud.setEmpleoEmpresaSector(
                empleo.getEmpresaSector());
        solicitud.setEmpleoEmpresaTamano(
                empleo.getEmpresaTamano());
        solicitud.setEmpleoEmpresaWeb(
                empleo.getEmpresaWeb());
        solicitud.setEmpleoEmpresaDescripcion(
                empleo.getEmpresaDescripcion());

        // Información profesional
        solicitud.setEmpleoStack(empleo.getStack());
        solicitud.setEmpleoSoftSkills(
                empleo.getSoftSkills());
        solicitud.setEmpleoDescripcionRol(
                empleo.getDescripcionRol());
        solicitud.setEmpleoRequisitosDeseables(
                empleo.getRequisitosDeseables());

        // Información complementaria
        solicitud.setEmpleoIcono(empleo.getIcono());
        solicitud.setEmpleoColorIcono(
                empleo.getColorIcono());
        solicitud.setEmpleoTimezone(
                empleo.getTimezone());
        solicitud.setEmpleoIngles(empleo.getIngles());
        solicitud.setEmpleoVacantes(
                empleo.getVacantes());

        // Salario
        solicitud.setEmpleoMostrarSalario(
                empleo.isMostrarSalario());
        solicitud.setEmpleoMoneda(empleo.getMoneda());
        solicitud.setEmpleoSalarioMin(
                empleo.getSalarioMin());
        solicitud.setEmpleoSalarioMax(
                empleo.getSalarioMax());
        solicitud.setEmpleoSalarioPeriodo(
                empleo.getSalarioPeriodo());

        // Estado y fecha
        solicitud.setEstado("pendiente");
        solicitud.setFechaSolicitud(new Date());
        solicitud.setMensajeMotivacion(mensaje);

        try {
            Solicitud guardada =
                    solicitudRepository.save(solicitud);

            return ResponseEntity.status(201).body(
                    Map.of(
                            "mensaje", "Postulación enviada correctamente",
                            "id", guardada.getId(),
                            "estado", guardada.getEstado()
                    )
            );
        } catch (DuplicateKeyException e) {
            return ResponseEntity.status(409).body(
                    Map.of("error",
                            "Ya existe una postulación para este empleo")
            );
        }
    }

    // 2. CONSULTAR MIS POSTULACIONES
    @GetMapping("/applications/mine")
    public ResponseEntity<?> misPostulaciones(
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null ||
                session.getAttribute("usuarioActualId") == null) {
            return ResponseEntity.status(401).body(
                    Map.of("error", "Debes iniciar sesión")
            );
        }

        if (!RoleGuard.TIPO_TALENTO.equals(
                session.getAttribute("usuarioActualTipo"))) {
            return ResponseEntity.status(403).body(
                    Map.of("error", "Solo para talentos")
            );
        }

        String usuarioId = session
                .getAttribute("usuarioActualId").toString();

        List<Map<String, Object>> resultado =
                solicitudRepository
                        .findByUsuarioIdOrderByFechaSolicitudDesc(
                                usuarioId)
                        .stream()
                        .map(s -> {
                            Map<String, Object> item =
                                    new java.util.LinkedHashMap<>();

                            item.put("id", s.getId());
                            item.put("empleoId", s.getEmpleoId());
                            item.put("titulo", s.getEmpleoTitulo());
                            item.put("empresa",
                                    s.getEmpleoEmpresaNombre());
                            item.put("estado", s.getEstado());
                            item.put("fechaSolicitud",
                                    s.getFechaSolicitud());
                            item.put("mensaje",
                                    s.getMensajeMotivacion());

                            return item;
                        })
                        .toList();

        return ResponseEntity.ok(resultado);
    }
}
