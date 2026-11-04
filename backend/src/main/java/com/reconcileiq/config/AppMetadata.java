package com.reconcileiq.config;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_metadata")
public class AppMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_key", nullable = false, unique = true, length = 100)
    private String appKey;

    @Column(name = "app_value", nullable = false, length = 500)
    private String appValue;

    protected AppMetadata() {
    }

    public AppMetadata(String appKey, String appValue) {
        this.appKey = appKey;
        this.appValue = appValue;
    }

    public String getAppKey() {
        return appKey;
    }

    public String getAppValue() {
        return appValue;
    }

    public void setAppValue(String appValue) {
        this.appValue = appValue;
    }
}
