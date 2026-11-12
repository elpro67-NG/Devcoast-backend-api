
package com.example.devcoast.controller;

import com.example.devcoast.config.RoleGuard;
import com.example.devcoast.model.Usuario;

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
@RequestMapping("/api/mobile/v1/talents")
public class MobileTalentsController {

    private final MongoTemplate mongoTemplate;

    public MobileTalentsController(
            MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    // 1. Consultar talentos públicos y activos
    @GetMapping
    public ResponseEntity<?> listar(
            @RequestParam(defaultValue = "") String q,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null ||
                session.getAttribute("usuarioActualId") == null) {
            return ResponseEntity.status(401).body(
                    Map.of("error", "Debes iniciar sesión")
            );
        }

        if (!RoleGuard.TIPO_EMPRESA.equals(
                session.getAttribute("usuarioActualTipo"))) {
            return ResponseEntity.status(403).body(
                    Map.of("error", "Solo para empresas")
            );
        }

        List<Criteria> filtros = new ArrayList<>();

        filtros.add(
                Criteria.where("tipo").is(
                        RoleGuard.TIPO_TALENTO)
        );

        filtros.add(
                Criteria.where("activo").is(true)
        );

        filtros.add(
                Criteria.where("perfil_publico").is(true)
        );

        String busqueda = q == null ? "" : q.trim();

        if (!busqueda.isEmpty()) {
            busqueda = busqueda.substring(
                    0,
                    Math.min(busqueda.length(), 100)
            );

            for (String palabra : busqueda.split("\\s+")) {
                if (palabra.length() < 2) continue;

                String regex = Pattern.quote(palabra);

                filtros.add(
                        new Criteria().orOperator(
                                Criteria.where("nombre")
                                        .regex(regex, "i"),
                                Criteria.where("rol_titulo")
                                        .regex(regex, "i"),
                                Criteria.where("especialidad")
                                        .regex(regex, "i"),
                                Criteria.where("ubicacion")
                                        .regex(regex, "i"),
                                Criteria.where("stack")
                                        .regex(regex, "i")
                        )
                );
            }
        }

        Query consulta = new Query(
                new Criteria().andOperator(
                        filtros.toArray(new Criteria[0])
                )
        );

        consulta.with(
                Sort.by(Sort.Direction.DESC, "_id")
        );

        consulta.limit(100);

        List<Usuario> usuarios =
                mongoTemplate.find(
                        consulta,
                        Usuario.class
                );

        List<Map<String, Object>> resultado =
                new ArrayList<>();

        for (Usuario usuario : usuarios) {
            resultado.add(convertir(usuario));
        }

        return ResponseEntity.ok(resultado);
    }

    // 2. Convertir los perfiles a JSON seguro
    private Map<String, Object> convertir(Usuario usuario) {

        Map<String, Object> datos =
                new LinkedHashMap<>();

        datos.put("id", usuario.getId());

        datos.put(
                "nombre",
                usuario.getNombre() != null
                        ? usuario.getNombre()
                        : "Talento"
        );

        datos.put(
                "titulo",
                usuario.getRolTitulo() != null
                        ? usuario.getRolTitulo()
                        : "Talento tecnológico"
        );

        datos.put(
                "ubicacion",
                usuario.getUbicacion() != null
                        ? usuario.getUbicacion()
                        : ""
        );

        datos.put(
                "stack",
                usuario.getStack() != null
                        ? usuario.getStack()
                        : List.of()
        );

        datos.put("especialidad",
                usuario.getEspecialidad());

        datos.put("modalidad",
                usuario.getModalidad());

        datos.put("bio", usuario.getBio());

        datos.put("fotoUrl",
                usuario.getFotoUrl());

        datos.put("disponibilidad",
                usuario.getDisponibilidad());

        return datos;
    }
}
