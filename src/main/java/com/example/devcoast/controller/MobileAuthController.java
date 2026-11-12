package com.example.devcoast.controller;

import com.example.devcoast.config.LimiteIntentosLogin;
import com.example.devcoast.config.RoleGuard;
import com.example.devcoast.model.Empresa;
import com.example.devcoast.model.Usuario;
import com.example.devcoast.repository.EmpresaRepository;
import com.example.devcoast.repository.UsuarioRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/mobile/v1/auth")
public class MobileAuthController {

    private final UsuarioRepository usuarioRepository;
    private final EmpresaRepository empresaRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final LimiteIntentosLogin limiteIntentos;

    public MobileAuthController(
            UsuarioRepository usuarioRepository,
            EmpresaRepository empresaRepository,
            BCryptPasswordEncoder passwordEncoder,
            LimiteIntentosLogin limiteIntentos) {

        this.usuarioRepository = usuarioRepository;
        this.empresaRepository = empresaRepository;
        this.passwordEncoder = passwordEncoder;
        this.limiteIntentos = limiteIntentos;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody Map<String, String> datos,
            HttpServletRequest request) {

        String correo = datos.getOrDefault("correo", "")
                .trim().toLowerCase();

        String password = datos.getOrDefault("password", "");

        if (correo.isBlank() || password.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Correo y contraseña obligatorios")
            );
        }

        String ip = request.getRemoteAddr();

        if (limiteIntentos.bloqueado(ip, correo)) {
            return ResponseEntity.status(
                    HttpStatus.TOO_MANY_REQUESTS
            ).body(Map.of(
                    "error", "Demasiados intentos. Intenta más tarde."
            ));
        }

        Usuario usuario = usuarioRepository.findByCorreo(correo);

        if (usuario != null
                && usuario.getPassword() != null
                && passwordEncoder.matches(
                        password, usuario.getPassword())) {

            limiteIntentos.exito(ip, correo);

            String tipo = usuario.getTipo() != null
                    ? usuario.getTipo()
                    : RoleGuard.TIPO_TALENTO;

            return autenticar(
                    request,
                    usuario.getId(),
                    usuario.getCorreo(),
                    usuario.getNombre(),
                    tipo,
                    usuario.getFotoUrl()
            );
        }

        Empresa empresa = empresaRepository.findByCorreo(correo);

        if (empresa != null
                && empresa.getPassword() != null
                && passwordEncoder.matches(
                        password, empresa.getPassword())) {

            limiteIntentos.exito(ip, correo);

            return autenticar(
                    request,
                    empresa.getId(),
                    empresa.getCorreo(),
                    empresa.getNombreEmpresa(),
                    RoleGuard.TIPO_EMPRESA,
                    empresa.getFotoUrl()
            );
        }

        limiteIntentos.fallo(ip, correo);

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of(
                        "error", "Correo o contraseña incorrectos"
                ));
    }

    private ResponseEntity<?> autenticar(
            HttpServletRequest request,
            String id,
            String correo,
            String nombre,
            String tipo,
            String fotoUrl) {

        HttpSession session = request.getSession(true);

        // Cambia el ID de sesión para evitar fijación de sesión.
        request.changeSessionId();

        session.removeAttribute("mostrarOnboarding");

        session.setAttribute("usuarioActualId", id);
        session.setAttribute("usuarioActualCorreo", correo);
        session.setAttribute(
                "usuarioActualNombre",
                nombre != null ? nombre : "Usuario"
        );
        session.setAttribute("usuarioActualTipo", tipo);

        if (fotoUrl != null && !fotoUrl.isBlank()) {
            session.setAttribute("usuarioActualFotoUrl", fotoUrl);
        } else {
            session.removeAttribute("usuarioActualFotoUrl");
        }

        return ResponseEntity.ok(datosSesion(session));
    }

    @GetMapping("/me")
    public ResponseEntity<?> usuarioActual(
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null
                || session.getAttribute("usuarioActualId") == null) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "No hay sesión activa"));
        }

        return ResponseEntity.ok(datosSesion(session));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        return ResponseEntity.ok(Map.of(
                "mensaje", "Sesión cerrada correctamente"
        ));
    }

    private Map<String, Object> datosSesion(
            HttpSession session) {

        Map<String, Object> datos = new HashMap<>();

        datos.put("id",
                session.getAttribute("usuarioActualId"));
        datos.put("correo",
                session.getAttribute("usuarioActualCorreo"));
        datos.put("nombre",
                session.getAttribute("usuarioActualNombre"));
        datos.put("tipo",
                session.getAttribute("usuarioActualTipo"));
        datos.put("fotoUrl",
                session.getAttribute("usuarioActualFotoUrl"));

        return datos;
    }
}
