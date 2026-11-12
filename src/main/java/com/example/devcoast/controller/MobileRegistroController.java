
package com.example.devcoast.controller;

import com.example.devcoast.config.LimiteTasa;
import com.example.devcoast.model.Empresa;
import com.example.devcoast.model.Usuario;
import com.example.devcoast.repository.EmpresaRepository;
import com.example.devcoast.repository.UsuarioRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/mobile/v1/auth")
public class MobileRegistroController {

    private static final int MAX_REGISTROS = 40;
    private static final long VENTANA_MS = 15 * 60 * 1000L;

    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final LimiteTasa limiteTasa;

    public MobileRegistroController(
            UsuarioRepository usuarioRepository,
            EmpresaRepository empresaRepository,
            BCryptPasswordEncoder passwordEncoder,
            LimiteTasa limiteTasa) {

        this.usuarioRepository = usuarioRepository;
        this.empresaRepository = empresaRepository;
        this.passwordEncoder = passwordEncoder;
        this.limiteTasa = limiteTasa;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registrar(
            @RequestBody Map<String, Object> datos,
            HttpServletRequest request) {

        String nombre = texto(datos, "nombre");
        String correo = texto(datos, "correo")
                .toLowerCase(Locale.ROOT);
        String password = texto(datos, "password");
        String ubicacion = texto(datos, "ubicacion");
        String tipo = texto(datos, "tipo")
                .toLowerCase(Locale.ROOT);

        // Validar tipo de cuenta.
        if (!tipo.equals("talento") &&
                !tipo.equals("empresa")) {

            return ResponseEntity.badRequest().body(
                    Map.of("error", "Tipo de cuenta inválido")
            );
        }

        // Validar campos obligatorios.
        if (nombre.isBlank() ||
                correo.isBlank() ||
                password.isBlank()) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                            "error",
                            "Completa los campos obligatorios"
                    )
            );
        }

        if (nombre.length() > 120 ||
                ubicacion.length() > 150) {

            return ResponseEntity.badRequest().body(
                    Map.of("error", "Texto demasiado largo")
            );
        }

        // Validar correo.
        if (correo.length() > 254 ||
                !correo.matches(
                    "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {

            return ResponseEntity.badRequest().body(
                    Map.of("error", "Correo electrónico inválido")
            );
        }

        // Validar contraseña.
        if (password.trim().length() < 8 ||
                password.length() > 72) {

            return ResponseEntity.badRequest().body(
                    Map.of(
                        "error",
                        "La contraseña debe tener entre 8 y 72 caracteres"
                    )
            );
        }

        // Limitar registros desde la misma IP.
        if (limiteTasa.excedido(
                "registro|" + request.getRemoteAddr(),
                MAX_REGISTROS,
                VENTANA_MS)) {

            return ResponseEntity.status(429).body(
                    Map.of(
                        "error",
                        "Demasiados registros. Intenta más tarde."
                    )
            );
        }

        // Verificar correo en ambas colecciones.
        if (usuarioRepository.findByCorreo(correo) != null ||
                empresaRepository.findByCorreo(correo) != null) {

            return ResponseEntity.status(409).body(
                    Map.of(
                        "error",
                        "Este correo ya está registrado"
                    )
            );
        }

        try {
            String id;
            String nombreSesion;

            if (tipo.equals("talento")) {

                Usuario usuario = new Usuario();

                usuario.setTipo("talento");
                usuario.setNombre(nombre);
                usuario.setCorreo(correo);
                usuario.setPassword(
                        passwordEncoder.encode(password)
                );

                usuario.setUbicacion(ubicacion);
                usuario.setActivo(true);
                usuario.setPerfilPublico(false);
                usuario.setOnboardingCompleto(false);

                usuario.setCreadoEn(new Date());
                usuario.setActualizadoEn(new Date());

                usuario = usuarioRepository.save(usuario);

                id = usuario.getId();
                nombreSesion = usuario.getNombre();

            } else {

                Empresa empresa = new Empresa();

                empresa.setTipo("empresa");
                empresa.setNombreEmpresa(nombre);
                empresa.setCorreo(correo);
                empresa.setPassword(
                        passwordEncoder.encode(password)
                );

                empresa.setActivo(true);
                empresa.setCreadoEn(new Date());
                empresa.setActualizadoEn(new Date());

                empresa = empresaRepository.save(empresa);

                id = empresa.getId();
                nombreSesion = empresa.getNombreEmpresa();
            }

            // Crear una nueva sesión autenticada.
            HttpSession anterior = request.getSession(false);

            if (anterior != null) {
                anterior.invalidate();
            }

            HttpSession session = request.getSession(true);

            session.setAttribute("usuarioActualId", id);
            session.setAttribute(
                    "usuarioActualCorreo", correo
            );
            session.setAttribute(
                    "usuarioActualNombre", nombreSesion
            );
            session.setAttribute(
                    "usuarioActualTipo", tipo
            );

            Map<String, Object> respuesta =
                    new LinkedHashMap<>();

            respuesta.put("id", id);
            respuesta.put("nombre", nombreSesion);
            respuesta.put("correo", correo);
            respuesta.put("tipo", tipo);
            respuesta.put("fotoUrl", null);
            respuesta.put("perfilCompleto", false);

            return ResponseEntity.status(201).body(
                    respuesta
            );

        } catch (DuplicateKeyException e) {

            return ResponseEntity.status(409).body(
                    Map.of(
                        "error",
                        "Este correo ya está registrado"
                    )
            );
        }
    }

    private String texto(
            Map<String, Object> datos,
            String clave) {

        Object valor = datos.get(clave);

        return valor == null
                ? ""
                : valor.toString().trim();
    }
}
