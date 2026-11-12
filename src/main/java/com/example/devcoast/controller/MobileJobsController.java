
package com.example.devcoast.controller;

import com.example.devcoast.model.Empleo;
import com.example.devcoast.repository.EmpleoRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/mobile/v1/jobs")
public class MobileJobsController {

    private final MongoTemplate mongoTemplate;
    private final EmpleoRepository empleoRepository;

    public MobileJobsController(
            MongoTemplate mongoTemplate,
            EmpleoRepository empleoRepository) {

        this.mongoTemplate = mongoTemplate;
        this.empleoRepository = empleoRepository;
    }

    // ==========================================
    // 1. CONSULTAR OFERTAS LABORALES ACTIVAS
    // ==========================================

    @GetMapping
    public List<Map<String, Object>> listar(
            @RequestParam(defaultValue = "") String q) {

        Criteria activas = new Criteria().orOperator(
                Criteria.where("estado").is("ACTIVO"),
                Criteria.where("estado").exists(false)
        );

        List<Criteria> filtros = new ArrayList<>();
        filtros.add(activas);

        String busqueda = q == null ? "" : q.trim();

        if (!busqueda.isEmpty()) {

            // Limitar longitud de búsqueda
            busqueda = busqueda.substring(
                    0, Math.min(100, busqueda.length())
            );

            for (String palabra : busqueda.split("\\s+")) {

                if (palabra.length() < 2) {
                    continue;
                }

                String regex = Pattern.quote(palabra);

                filtros.add(
                        new Criteria().orOperator(
                                Criteria.where("titulo")
                                        .regex(regex, "i"),

                                Criteria.where("empresaNombre")
                                        .regex(regex, "i"),

                                Criteria.where("descripcionRol")
                                        .regex(regex, "i"),

                                Criteria.where("stack")
                                        .regex(regex, "i"),

                                Criteria.where("categoria")
                                        .regex(regex, "i"),

                                Criteria.where("seniority")
                                        .regex(regex, "i")
                        )
                );
            }
        }

        Criteria condiciones = new Criteria().andOperator(
                filtros.toArray(new Criteria[0])
        );

        Query consulta = new Query(condiciones);

        consulta.with(
                Sort.by(Sort.Direction.DESC, "_id")
        );

        // Máximo 100 ofertas por consulta
        consulta.limit(100);

        List<Empleo> empleos = mongoTemplate.find(
                consulta,
                Empleo.class
        );

        return convertirLista(empleos);
    }

    // ==========================================
    // 2. CONSULTAR OFERTAS DE UNA EMPRESA
    // ==========================================

    @GetMapping("/mine")
    public ResponseEntity<?> misOfertas(
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null
                || session.getAttribute("usuarioActualId") == null) {

            return ResponseEntity.status(401).body(
                    Map.of("error", "No hay una sesión activa")
            );
        }

        if (!"EMPRESA".equals(
                session.getAttribute("usuarioActualTipo"))) {

            return ResponseEntity.status(403).body(
                    Map.of(
                            "error",
                            "Esta sección es exclusiva para empresas"
                    )
            );
        }

        String empresaId = session
                .getAttribute("usuarioActualId")
                .toString();

        List<Empleo> ofertas = new ArrayList<>();

        // Ofertas activas
        ofertas.addAll(
                empleoRepository
                        .findByEmpresaIdAndEstadoOrderByIdDesc(
                                empresaId,
                                "ACTIVO"
                        )
        );

        // Ofertas cerradas
        ofertas.addAll(
                empleoRepository
                        .findByEmpresaIdAndEstadoOrderByIdDesc(
                                empresaId,
                                "CERRADO"
                        )
        );

        return ResponseEntity.ok(
                convertirLista(ofertas)
        );
    }

    // ==========================================
    // 3. CONSULTAR DETALLE DE UN EMPLEO
    // ==========================================

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerDetalle(
            @PathVariable String id) {

        return empleoRepository.findById(id)
                .map(empleo -> {
                    boolean activo =
                            "ACTIVO".equals(empleo.getEstado())
                            || empleo.getEstado() == null;

                    if (!activo) {
                        return ResponseEntity.status(404)
                                .body((Object) Map.of(
                                        "error",
                                        "Oferta no disponible"
                                ));
                    }

                    return ResponseEntity.ok(
                            (Object) convertir(empleo)
                    );
                })
                .orElseGet(() ->
                        ResponseEntity.status(404).body(
                                Map.of(
                                        "error",
                                        "Empleo no encontrado"
                                )
                        )
                );
    }

    // ==========================================
    // 4. CONVERTIR LISTA A JSON
    // ==========================================

    private List<Map<String, Object>> convertirLista(
            List<Empleo> empleos) {

        return empleos.stream()
                .map(this::convertir)
                .toList();
    }

    // ==========================================
    // 5. CONVERTIR EMPLEO A DATOS PARA FLUTTER
    // ==========================================

    private Map<String, Object> convertir(
            Empleo empleo) {

        Map<String, Object> datos =
                new LinkedHashMap<>();

        // Identificación
        datos.put("id", empleo.getId());

        // Información principal
        datos.put("titulo", empleo.getTitulo());
        datos.put(
                "empresaNombre",
                empleo.getEmpresaNombre()
        );
        datos.put("modalidad", empleo.getModalidad());
        datos.put("categoria", empleo.getCategoria());
        datos.put("seniority", empleo.getSeniority());
        datos.put("contrato", empleo.getContrato());
        datos.put("estado", empleo.getEstado());

        // Descripción
        datos.put(
                "descripcionRol",
                empleo.getDescripcionRol()
        );

        datos.put(
                "requisitosDeseables",
                empleo.getRequisitosDeseables()
        );

        // Tecnologías y habilidades
        datos.put("stack", empleo.getStack());
        datos.put(
                "softSkills",
                empleo.getSoftSkills()
        );

        // Datos adicionales
        datos.put("vacantes", empleo.getVacantes());
        datos.put(
                "fechaPublicacion",
                empleo.getFechaPublicacion()
        );

        // Salario, únicamente si la empresa
        // decidió hacerlo público
        if (empleo.isMostrarSalario()) {

            datos.put(
                    "salarioMin",
                    empleo.getSalarioMin()
            );

            datos.put(
                    "salarioMax",
                    empleo.getSalarioMax()
            );

            datos.put(
                    "moneda",
                    empleo.getMoneda()
            );

            datos.put(
                    "salarioPeriodo",
                    empleo.getSalarioPeriodo()
            );
        }

        return datos;
    }
}
