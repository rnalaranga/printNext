package com.rr.secureprint.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "technicians")
public class TechnicianEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 32)
    private String pin;

    @Column(length = 40)
    private String phone;

    @Column(length = 120)
    private String vendor;

    @Column(nullable = false)
    private boolean active = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPin() { return pin; }
    public void setPin(String pin) { this.pin = pin; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
