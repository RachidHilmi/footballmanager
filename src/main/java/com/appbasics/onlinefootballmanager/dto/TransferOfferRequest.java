package com.appbasics.onlinefootballmanager.dto;

public class TransferOfferRequest {
    private String fromTeamId;
    private String toTeamId;
    private String playerId;
    private double offerPrice;
    private String regionId;
    private String leagueId;

    // Getters and setters
    public String getFromTeamId() { return fromTeamId; }
    public void setFromTeamId(String fromTeamId) { this.fromTeamId = fromTeamId; }

    public String getToTeamId() { return toTeamId; }
    public void setToTeamId(String toTeamId) { this.toTeamId = toTeamId; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public double getOfferPrice() { return offerPrice; }
    public void setOfferPrice(double offerPrice) { this.offerPrice = offerPrice; }

    public String getRegionId() { return regionId; }
    public void setRegionId(String regionId) { this.regionId = regionId; }

    public String getLeagueId() { return leagueId; }
    public void setLeagueId(String leagueId) { this.leagueId = leagueId; }
}
