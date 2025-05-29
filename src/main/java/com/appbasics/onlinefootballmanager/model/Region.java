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
@Document(collection = "regions")
public class Region {
    @Id
    private String _id;
    private String regionId; // This is also the _id in MongoDB
    private String name;
    private List<String> countries; // List of country names (String)
}
