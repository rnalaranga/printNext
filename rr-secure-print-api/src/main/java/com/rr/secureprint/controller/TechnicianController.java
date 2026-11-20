package com.rr.secureprint.controller;

import com.rr.secureprint.entity.TechnicianEntity;
import com.rr.secureprint.repository.TechnicianRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/technicians")
public class TechnicianController {

    private final TechnicianRepository technicianRepository;

    public TechnicianController(TechnicianRepository technicianRepository) {
        this.technicianRepository = technicianRepository;
    }

    @GetMapping
    public List<TechnicianEntity> list() {
        return technicianRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<TechnicianEntity> create(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "").trim();
        String pin = body.getOrDefault("pin", "").trim();
        if (name.isBlank() || pin.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        TechnicianEntity t = new TechnicianEntity();
        t.setName(name);
        t.setPin(pin);
        t.setPhone(body.getOrDefault("phone", "").trim());
        t.setVendor(body.getOrDefault("vendor", "").trim());
        t.setActive(true);
        return ResponseEntity.ok(technicianRepository.save(t));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        if (!technicianRepository.existsById(id)) return ResponseEntity.notFound().build();
        technicianRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth")
    public ResponseEntity<Map<String, Object>> authenticate(@RequestBody Map<String, String> body) {
        String pin = body != null ? body.getOrDefault("pin", "").trim() : "";
        return technicianRepository.findFirstByPinAndActiveTrue(pin)
                .<ResponseEntity<Map<String, Object>>>map(t -> ResponseEntity.ok(Map.of(
                        "authenticated", true,
                        "id", t.getId(),
                        "name", t.getName(),
                        "vendor", t.getVendor() != null ? t.getVendor() : ""
                )))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("authenticated", false, "error", "Invalid Technician PIN")));
    }
}
