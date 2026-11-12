
package com.example.devcoast.controller;

import com.example.devcoast.model.Empleo;
import com.example.devcoast.model.Empresa;
import com.example.devcoast.repository.EmpleoRepository;
import com.example.devcoast.repository.EmpresaRepository;
import com.example.devcoast.service.PlanService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mobile/v1/company/jobs")
public class MobileCompanyJobsController {

    private final EmpleoRepository empleoRepository;
    private final EmpresaRepository empresaRepository;
    private final PlanService planService;

    public MobileCompanyJobsController(
            EmpleoRepository empleoRepository,
            EmpresaRepository empresaRepository,
            PlanService planService) {

        this.empleoRepository = empleoRepository;
        this.empresaRepository = empresaRepository;
        this.planService = planService;
    }

    // ==========================================
    // 1. VERIFICAR SESIÓN DE EMPRESA
    // ==========================================

    private String empresaId(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null) {
            return null;
        }

        Object tipo = session.getAttribute(
                "usuarioActualTipo"
        );

        if (tipo == null ||
                !"empresa".equalsIgnoreCase(tipo.toString())) {
            return null;
        }

        Object id = session.getAttribute(
                "usuarioActualId"
        );

        if (id == null) {
            return null;
        }

        return id.toString();
    }

    // ==========================================
    // 2. CONSULTAR OFERTAS DE MI EMPRESA
    // ==========================================

    @GetMapping
    public ResponseEntity<?> listar(
            HttpServletRequest request) {

        String id = empresaId(request);

        if (id == null) {
            return ResponseEntity.status(401).body(
                    Map.of(
                            "error",
                            "Sesión de empresa requerida"
                    )
            );
        }

        if (!empresaRepository.existsById(id)) {
            return ResponseEntity.status(403).body(
                    Map.of(
                            "error",
                            "Empresa no válida"
                    )
            );
        }

        List<Empleo> ofertas = new ArrayList<>();

        ofertas.addAll(
                empleoRepository
                        .findByEmpresaIdAndEstadoOrderByIdDesc(
                                id, "ACTIVO"
                        )
        );

        ofertas.addAll(
                empleoRepository
                        .findByEmpresaIdAndEstadoOrderByIdDesc(
                                id, "CERRADO"
                        )
        );

        List<Map<String, Object>> resultado =
                ofertas.stream()
                        .map(this::convertir)
                        .toList();

        return ResponseEntity.ok(resultado);
    }

    // ==========================================
    // 3. CREAR UNA OFERTA LABORAL
    // ==========================================

    @PostMapping
    public ResponseEntity<?> crear(
            @RequestBody Map<String, Object> datos,
            HttpServletRequest request) {

        String id = empresaId(request);

        if (id == null) {
            return ResponseEntity.status(401).body(
                    Map.of(
                            "error",
                            "Sesión de empresa requerida"
                    )
            );
        }

        Empresa empresa = empresaRepository
                .findById(id)
                .orElse(null);

        if (empresa == null) {
            return ResponseEntity.status(403).body(
                    Map.of(
                            "error",
                            "Empresa no encontrada"
                    )
            );
        }

        // Datos enviados por Flutter.
        String titulo = texto(datos, "titulo");

        String descripcion = texto(
                datos, "descripcionRol"
        );

        String stack = texto(datos, "stack");
        String modalidad = texto(datos, "modalidad");
        String categoria = texto(datos, "categoria");
        String seniority = texto(datos, "seniority");
        String contrato = texto(datos, "contrato");

        // ======================================
        // 4. VALIDAR NÚMERO DE VACANTES
        // ======================================

        int vacantes = 1;

        Object valorVacantes = datos.get("vacantes");

        try {

            if (valorVacantes != null &&
                    !valorVacantes.toString().trim().isEmpty()) {

                String numero = valorVacantes
                        .toString()
                        .trim();

                vacantes = Integer.parseInt(numero);
            }

        } catch (NumberFormatException e) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error",
                            "El número de vacantes debe ser un entero válido"
                    )
            );
        }

        if (vacantes < 1) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error",
                            "Debes indicar al menos una vacante"
                    )
            );
        }

        // ======================================
        // 5. VALIDAR CAMPOS OBLIGATORIOS
        // ======================================

        if (titulo.isBlank() ||
                descripcion.isBlank() ||
                stack.isBlank() ||
                modalidad.isBlank() ||
                categoria.isBlank() ||
                seniority.isBlank() ||
                contrato.isBlank()) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error",
                            "Completa todos los campos obligatorios"
                    )
            );
        }

        // ======================================
        // 6. VALIDAR PERFIL DE EMPRESA
        // ======================================

        if (vacio(empresa.getNombreEmpresa()) ||
                vacio(empresa.getSector()) ||
                vacio(empresa.getTamano()) ||
                vacio(empresa.getDescripcion())) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error",
                            "Completa primero el perfil de tu empresa"
                    )
            );
        }

        // ======================================
        // 7. VALIDAR LÍMITE DE OFERTAS DEL PLAN
        // ======================================

        long activas =
                empleoRepository.countByEmpresaIdAndEstado(
                        id,
                        "ACTIVO"
                );

        int limite =
                planService.limiteVacantesActivas(empresa);

        if (activas >= limite) {

            return ResponseEntity.status(409).body(
                    Map.of(
                            "error",
                            "Alcanzaste el límite de ofertas activas de tu plan"
                    )
            );
        }

        // ======================================
        // 8. CREAR EL EMPLEO
        // ======================================

        Empleo empleo = new Empleo();

        // Datos de la empresa autenticada.
        // Nunca confiar en un ID enviado por Flutter.

        empleo.setEmpresaId(id);

        empleo.setEmpresaNombre(
                empresa.getNombreEmpresa()
        );

        empleo.setEmpresaCorreo(
                empresa.getCorreo()
        );

        empleo.setEmpresaSector(
                empresa.getSector()
        );

        empleo.setEmpresaTamano(
                empresa.getTamano()
        );

        empleo.setEmpresaDescripcion(
                empresa.getDescripcion()
        );

        empleo.setEmpresaWeb(
                empresa.getSitioWeb()
        );

        empleo.setLogoUrl(
                empresa.getFotoUrl()
        );

        // Datos profesionales del empleo.

        empleo.setTitulo(titulo);
        empleo.setDescripcionRol(descripcion);
        empleo.setStack(stack);
        empleo.setModalidad(modalidad);
        empleo.setCategoria(categoria);
        empleo.setSeniority(seniority);
        empleo.setContrato(contrato);
        empleo.setVacantes(vacantes);

        // Estado inicial.

        empleo.setEstado("ACTIVO");
        empleo.setFechaPublicacion(new Date());

        // ======================================
        // 9. GUARDAR EN MONGODB
        // ======================================

        Empleo guardado =
                empleoRepository.save(empleo);

        // ======================================
        // 10. RESPUESTA PARA FLUTTER
        // ======================================

        return ResponseEntity.status(201).body(
                convertir(guardado)
        );
    }

    // ==========================================
    // 11. OBTENER TEXTO DEL JSON
    // ==========================================

    private String texto(
            Map<String, Object> datos,
            String clave) {

        Object valor = datos.get(clave);

        return valor == null
                ? ""
                : valor.toString().trim();
    }

    // ==========================================
    // 12. COMPROBAR TEXTO VACÍO
    // ==========================================

    private boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    // ==========================================
    // 13. CONVERTIR EMPLEO A JSON
    // ==========================================

    private Map<String, Object> convertir(
            Empleo empleo) {

        Map<String, Object> resultado =
                new LinkedHashMap<>();

        resultado.put("id", empleo.getId());

        resultado.put(
                "titulo",
                empleo.getTitulo()
        );

        resultado.put(
                "empresaNombre",
                empleo.getEmpresaNombre()
        );

        resultado.put(
                "modalidad",
                empleo.getModalidad()
        );

        resultado.put(
                "categoria",
                empleo.getCategoria()
        );

        resultado.put(
                "seniority",
                empleo.getSeniority()
        );

        resultado.put(
                "contrato",
                empleo.getContrato()
        );

        resultado.put(
                "descripcionRol",
                empleo.getDescripcionRol()
        );

        resultado.put(
                "stack",
                empleo.getStack()
        );

        resultado.put(
                "estado",
                empleo.getEstado()
        );

        resultado.put(
                "vacantes",
                empleo.getVacantes()
        );

        resultado.put(
                "fechaPublicacion",
                empleo.getFechaPublicacion()
        );

        // Mostrar salario solo si es público.

        if (empleo.isMostrarSalario()) {

            resultado.put(
                    "salarioMin",
                    empleo.getSalarioMin()
            );

            resultado.put(
                    "salarioMax",
                    empleo.getSalarioMax()
            );

            resultado.put(
                    "moneda",
                    empleo.getMoneda()
            );

            resultado.put(
                    "salarioPeriodo",
                    empleo.getSalarioPeriodo()
            );
        }

        return resultado;
    }
}
