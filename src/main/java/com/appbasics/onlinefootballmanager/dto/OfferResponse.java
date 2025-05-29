package com.appbasics.onlinefootballmanager.dto;

public class OfferResponse {
    private String offerId;
    private boolean accepted;

    // Getters and setters
    public String getOfferId() { return offerId; }
    public void setOfferId(String offerId) { this.offerId = offerId; }

    public boolean isAccepted() { return accepted; }
    public void setAccepted(boolean accepted) { this.accepted = accepted; }
}
