package com.appbasics.onlinefootballmanager.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "countries")
public class Country {
    @Id
    private String _id;
    private String countryId; // This is also the _id in MongoDB
    private String name;
    private String flag;
    private String regionId;
    private List<String> baseLeagueIds;
}
