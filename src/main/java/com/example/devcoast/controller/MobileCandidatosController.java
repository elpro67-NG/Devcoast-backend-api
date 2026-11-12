
package com.example.devcoast.controller;

import com.example.devcoast.model.Empleo;
import com.example.devcoast.model.Solicitud;
import com.example.devcoast.repository.EmpleoRepository;
import com.example.devcoast.repository.SolicitudRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/mobile/v1/company/jobs")
public class MobileCandidatosController {

    private final EmpleoRepository empleoRepository;
    private final SolicitudRepository solicitudRepository;

    public MobileCandidatosController(
            EmpleoRepository empleoRepository,
            SolicitudRepository solicitudRepository) {

        this.empleoRepository = empleoRepository;
        this.solicitudRepository = solicitudRepository;
    }

    // Verificar que la empresa sea propietaria de la oferta.
    private ResponseEntity<?> verificarEmpresa(
            String empleoId,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null ||
                session.getAttribute("usuarioActualId") == null) {

            return ResponseEntity.status(401)
                    .body(Map.of(
                            "error",
                            "Debes iniciar sesión"
                    ));
        }

        Object tipo = session.getAttribute("usuarioActualTipo");

        // Reconoce empresa, Empresa y EMPRESA.
        if (tipo == null ||
                !"empresa".equalsIgnoreCase(tipo.toString())) {

            return ResponseEntity.status(403)
                    .body(Map.of(
                            "error",
                            "Solo para empresas"
                    ));
        }

        Empleo empleo = empleoRepository
                .findById(empleoId)
                .orElse(null);

        if (empleo == null) {
            return ResponseEntity.status(404)
                    .body(Map.of(
                            "error",
                            "Oferta no encontrada"
                    ));
        }

        String empresaId = session
                .getAttribute("usuarioActualId")
                .toString();

        if (!Objects.equals(empleo.getEmpresaId(), empresaId)) {

            return ResponseEntity.status(403)
                    .body(Map.of(
                            "error",
                            "No eres dueño de esta oferta"
                    ));
        }

        return null;
    }

    // ==========================================
    // 1. CONSULTAR CANDIDATOS DE UNA OFERTA
    // ==========================================

    @GetMapping("/{id}/applications")
    public ResponseEntity<?> listar(
            @PathVariable String id,
            HttpServletRequest request) {

        ResponseEntity<?> error = verificarEmpresa(id, request);

        if (error != null) {
            return error;
        }

        List<Map<String, Object>> resultado =
                solicitudRepository
                        .findByEmpleoIdOrderByFechaSolicitudDesc(id)
                        .stream()
                        .map(s -> {
                            Map<String, Object> candidato =
                                    new java.util.LinkedHashMap<>();

                            candidato.put("id", s.getId());

                            candidato.put(
                                    "candidato",
                                    Objects.toString(
                                            s.getUsuarioNombre(),
                                            "Talento"
                                    )
                            );

                            candidato.put(
                                    "estado",
                                    Objects.toString(
                                            s.getEstado(),
                                            "pendiente"
                                    )
                            );

                            candidato.put(
                                    "correo",
                                    Objects.toString(
                                            s.getUsuarioCorreo(),
                                            ""
                                    )
                            );

                            candidato.put(
                                    "mensaje",
                                    Objects.toString(
                                            s.getMensajeMotivacion(),
                                            ""
                                    )
                            );

                            candidato.put(
                                    "fechaSolicitud",
                                    s.getFechaSolicitud()
                            );

                            return candidato;
                        })
                        .toList();

        return ResponseEntity.ok(resultado);
    }

    // ==========================================
    // 2. CAMBIAR ESTADO DE UNA POSTULACIÓN
    // ==========================================

    @PatchMapping("/{id}/applications/{solicitudId}")
    public ResponseEntity<?> cambiarEstado(
            @PathVariable String id,
            @PathVariable String solicitudId,
            @RequestBody Map<String, String> datos,
            HttpServletRequest request) {

        ResponseEntity<?> error = verificarEmpresa(id, request);

        if (error != null) {
            return error;
        }

        Solicitud solicitud = solicitudRepository
                .findByIdAndEmpleoId(solicitudId, id)
                .orElse(null);

        if (solicitud == null) {

            return ResponseEntity.status(404)
                    .body(Map.of(
                            "error",
                            "Postulación no encontrada"
                    ));
        }

        String estado = datos.getOrDefault("estado", "")
                .trim()
                .toLowerCase();

        // Estados que utiliza nuestra API móvil.
        List<String> estadosValidos = List.of(
                "pendiente",
                "aprobada",
                "rechazada"
        );

        if (!estadosValidos.contains(estado)) {

            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error",
                            "Estado no válido"
                    ));
        }

        solicitud.setEstado(estado);

        solicitudRepository.save(solicitud);

        return ResponseEntity.ok(Map.of(
                "mensaje", "Estado actualizado correctamente",
                "id", solicitud.getId(),
                "estado", estado
        ));
    }
}
