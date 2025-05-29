package com.appbasics.onlinefootballmanager.controller;

import com.appbasics.onlinefootballmanager.model.Country;
import com.appbasics.onlinefootballmanager.model.Region;
import com.appbasics.onlinefootballmanager.repository.mongo.CountryRepository;
import com.appbasics.onlinefootballmanager.repository.mongo.RegionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/regions")
public class RegionController {

    private final RegionRepository regionRepository;
    private final CountryRepository countryRepository;

    @Autowired
    public RegionController(RegionRepository regionRepository, CountryRepository countryRepository) {
        this.regionRepository = regionRepository;
        this.countryRepository = countryRepository;
    }

    @GetMapping
    public Flux<Region> getAllRegions() {
        return regionRepository.findAll();
    }

    @GetMapping("/api/region/{regionId}/countries")
    public Mono<List<Country>> getCountryDetailsByRegion(@PathVariable String regionId) {
        return regionRepository.findById(regionId)
                .flatMapMany(region ->
                        countryRepository.findAllById(region.getCountries())  // assuming IDs match countryId/_id
                ).collectList();
    }

}
