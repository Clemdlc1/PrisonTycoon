package fr.prisontycoon.data;

import java.util.UUID;

/**
 * Données de l'avant-poste
 * Contient les informations sur le contrôleur, le skin actuel, etc.
 */
public class OutpostData {

    private UUID controller;
    private String controllerName;
    private long captureTime;
    private String currentSkin;
    private long totalCoinsGenerated;
    private long totalTokensGenerated;
    private int totalCapturesCount;

    public OutpostData() {
        this.controller = null;
        this.controllerName = null;
        this.captureTime = 0;
        this.currentSkin = "default";
    }

    public OutpostData(UUID controller, String controllerName, long captureTime, String currentSkin) {
        this.controller = controller;
        this.controllerName = controllerName;
        this.captureTime = captureTime;
        this.currentSkin = currentSkin;
    }

    // Getters et Setters
    public UUID getController() {
        return controller;
    }

    public void setController(UUID controller) {
        this.controller = controller;
        if (controller != null) {
            this.totalCapturesCount++;
        }
    }

    public String getControllerName() {
        return controllerName;
    }

    public void setControllerName(String controllerName) {
        this.controllerName = controllerName;
    }

    public long getCaptureTime() {
        return captureTime;
    }

    public void setCaptureTime(long captureTime) {
        this.captureTime = captureTime;
    }

    public String getCurrentSkin() {
        return currentSkin;
    }

    public void setCurrentSkin(String currentSkin) {
        this.currentSkin = currentSkin;
    }

    public long getTotalCoinsGenerated() {
        return totalCoinsGenerated;
    }

    public void addCoinsGenerated(long coins) {
        this.totalCoinsGenerated += coins;
    }

    public long getTotalTokensGenerated() {
        return totalTokensGenerated;
    }

    public void addTokensGenerated(long tokens) {
        this.totalTokensGenerated += tokens;
    }

    public int getTotalCapturesCount() {
        return totalCapturesCount;
    }

    /**
     * Vérifie si l'avant-poste est contrôlé
     */
    public boolean isControlled() {
        return controller != null;
    }

    /**
     * Remet à zéro le contrôle de l'avant-poste
     */
    public void resetControl() {
        this.controller = null;
        this.controllerName = null;
        this.captureTime = 0;
    }

    /**
     * Obtient le temps depuis la dernière capture en secondes
     */
    public long getTimeSinceCapture() {
        if (captureTime == 0) return 0;
        return (System.currentTimeMillis() - captureTime) / 1000;
    }

    @Override
    public String toString() {
        return "OutpostData{" +
                "controller=" + controller +
                ", controllerName='" + controllerName + '\'' +
                ", captureTime=" + captureTime +
                ", currentSkin='" + currentSkin + '\'' +
                '}';
    }
}