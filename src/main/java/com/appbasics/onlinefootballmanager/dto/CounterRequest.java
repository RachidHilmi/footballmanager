package com.appbasics.onlinefootballmanager.dto;

public class CounterRequest {
    private String originalOfferId;
    private double newPrice;

    // Getters and setters
    public String getOriginalOfferId() { return originalOfferId; }
    public void setOriginalOfferId(String originalOfferId) { this.originalOfferId = originalOfferId; }

    public double getNewPrice() { return newPrice; }
    public void setNewPrice(double newPrice) { this.newPrice = newPrice; }
}
