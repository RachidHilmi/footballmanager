package com.appbasics.onlinefootballmanager.util;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterResponse {

    private String managerId;
    private String token;

    public RegisterResponse(String managerId, String token) {
        this.managerId = managerId;
        this.token = token;
    }
}
