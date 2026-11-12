
package com.example.devcoast.controller;

import com.example.devcoast.model.Proyecto;
import com.example.devcoast.repository.ProyectoRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/mobile/v1/projects")
public class MobileProjectsController {

    private final ProyectoRepository proyectoRepository;

    public MobileProjectsController(
            ProyectoRepository proyectoRepository) {
        this.proyectoRepository = proyectoRepository;
    }

    private HttpSession sesionTalento(
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null ||
                session.getAttribute("usuarioActualId") == null) {
            return null;
        }

        Object tipo = session.getAttribute("usuarioActualTipo");

        if (tipo == null ||
                !"talento".equalsIgnoreCase(tipo.toString())) {
            return null;
        }

        return session;
    }

    private ResponseEntity<?> errorAcceso(
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null ||
                session.getAttribute("usuarioActualId") == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Debes iniciar sesión"));
        }

        return ResponseEntity.status(403)
                .body(Map.of("error", "Solo para talentos"));
    }

    // 1. LISTAR MIS PROYECTOS
    @GetMapping
    public ResponseEntity<?> listar(
            HttpServletRequest request) {

        HttpSession session = sesionTalento(request);

        if (session == null) {
            return errorAcceso(request);
        }

        String usuarioId = session
                .getAttribute("usuarioActualId").toString();

        List<Map<String, Object>> proyectos =
                proyectoRepository
                        .findByUsuarioIdOrderByCreadoEnDesc(usuarioId)
                        .stream()
                        .map(this::convertir)
                        .toList();

        return ResponseEntity.ok(proyectos);
    }

    // 2. CREAR PROYECTO
    @PostMapping
    public ResponseEntity<?> crear(
            @RequestBody Map<String, Object> datos,
            HttpServletRequest request) {

        HttpSession session = sesionTalento(request);

        if (session == null) {
            return errorAcceso(request);
        }

        String error = validar(datos);

        if (error != null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", error));
        }

        Proyecto proyecto = new Proyecto();

        proyecto.setUsuarioId(
                session.getAttribute("usuarioActualId").toString()
        );

        proyecto.setUsuarioCorreo(
                String.valueOf(
                    session.getAttribute("usuarioActualCorreo")
                )
        );

        proyecto.setCreadoEn(new Date());

        aplicarDatos(proyecto, datos);

        proyecto.setActualizadoEn(new Date());

        Proyecto guardado = proyectoRepository.save(proyecto);

        return ResponseEntity.status(201)
                .body(convertir(guardado));
    }

    // 3. EDITAR UN PROYECTO PROPIO
    @PutMapping("/{id}")
    public ResponseEntity<?> editar(
            @PathVariable String id,
            @RequestBody Map<String, Object> datos,
            HttpServletRequest request) {

        HttpSession session = sesionTalento(request);

        if (session == null) {
            return errorAcceso(request);
        }

        String usuarioId = session
                .getAttribute("usuarioActualId").toString();

        Proyecto proyecto = proyectoRepository
                .findByIdAndUsuarioId(id, usuarioId)
                .orElse(null);

        if (proyecto == null) {
            return ResponseEntity.status(404).body(
                    Map.of("error", "Proyecto no encontrado")
            );
        }

        String error = validar(datos);

        if (error != null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", error));
        }

        aplicarDatos(proyecto, datos);

        proyecto.setActualizadoEn(new Date());

        Proyecto actualizado = proyectoRepository.save(proyecto);

        return ResponseEntity.ok(convertir(actualizado));
    }

    // 4. ELIMINAR UN PROYECTO PROPIO
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(
            @PathVariable String id,
            HttpServletRequest request) {

        HttpSession session = sesionTalento(request);

        if (session == null) {
            return errorAcceso(request);
        }

        String usuarioId = session
                .getAttribute("usuarioActualId").toString();

        Proyecto proyecto = proyectoRepository
                .findByIdAndUsuarioId(id, usuarioId)
                .orElse(null);

        if (proyecto == null) {
            return ResponseEntity.status(404).body(
                    Map.of("error", "Proyecto no encontrado")
            );
        }

        proyectoRepository.delete(proyecto);

        return ResponseEntity.ok(
                Map.of("mensaje", "Proyecto eliminado correctamente")
        );
    }

    // 5. VALIDACIONES
    private String validar(Map<String, Object> datos) {

        String nombre = texto(datos, "nombre");
        String descripcion = texto(datos, "descripcion");
        String categoria = texto(datos, "categoria");
        String tipo = texto(datos, "tipo");
        String enlace = texto(datos, "enlace");

        if (nombre.isBlank() ||
                descripcion.isBlank() ||
                categoria.isBlank() ||
                tipo.isBlank()) {
            return "Completa nombre, descripción, categoría y tipo";
        }

        if (nombre.length() > 120 ||
                descripcion.length() > 200 ||
                categoria.length() > 80 ||
                tipo.length() > 80) {
            return "Uno de los campos supera la longitud permitida";
        }

        if (enlace.length() > 2048) {
            return "El enlace es demasiado largo";
        }

        if (!enlace.isBlank()) {
            try {
                URI uri = URI.create(enlace);

                String esquema = uri.getScheme();

                if (esquema == null ||
                        (!esquema.equalsIgnoreCase("http") &&
                         !esquema.equalsIgnoreCase("https")) ||
                        uri.getHost() == null) {
                    return "El enlace debe ser una URL HTTP o HTTPS válida";
                }
            } catch (IllegalArgumentException e) {
                return "El enlace del proyecto no es válido";
            }
        }

        if (texto(datos, "tecnologias").length() > 1000) {
            return "La lista de tecnologías es demasiado larga";
        }

        return null;
    }

    // 6. GUARDAR CAMPOS SIN CAMBIAR EL PROPIETARIO
    private void aplicarDatos(
            Proyecto proyecto,
            Map<String, Object> datos) {

        proyecto.setNombre(texto(datos, "nombre"));
        proyecto.setDescripcionCorta(texto(datos, "descripcion"));
        proyecto.setCategoria(texto(datos, "categoria"));
        proyecto.setTipo(texto(datos, "tipo"));

        String enlace = texto(datos, "enlace");

        proyecto.setLinkProyecto(
                enlace.isBlank() ? null : enlace
        );

        proyecto.setStack(
                Arrays.stream(
                    texto(datos, "tecnologias").split(",")
                )
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(Collectors.toList())
        );

        // Se guardan como borradores por defecto.
        // Una edición conserva la visibilidad si no se envía.
        Object publicar = datos.get("publicado");

        if (publicar instanceof Boolean) {
            proyecto.setPublicado((Boolean) publicar);
        }
    }

    // 7. RESPUESTA JSON PARA FLUTTER
    private Map<String, Object> convertir(Proyecto proyecto) {

        Map<String, Object> datos = new LinkedHashMap<>();

        datos.put("id", proyecto.getId());
        datos.put("nombre", proyecto.getNombre());
        datos.put("descripcion", proyecto.getDescripcionCorta());
        datos.put("categoria", proyecto.getCategoria());
        datos.put("tipo", proyecto.getTipo());
        datos.put("enlace", proyecto.getLinkProyecto());
        datos.put("tecnologias", proyecto.getStack());
        datos.put("publicado", proyecto.isPublicado());
        datos.put("creadoEn", proyecto.getCreadoEn());
        datos.put("actualizadoEn", proyecto.getActualizadoEn());

        return datos;
    }

    private String texto(
            Map<String, Object> datos,
            String clave) {

        Object valor = datos.get(clave);

        return valor == null ? "" : valor.toString().trim();
    }
}
