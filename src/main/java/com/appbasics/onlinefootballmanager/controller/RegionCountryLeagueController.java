package com.appbasics.onlinefootballmanager.controller;

import com.appbasics.onlinefootballmanager.dto.LeagueInstanceStatusDto;
import com.appbasics.onlinefootballmanager.dto.LeagueSelectionRequest;
import com.appbasics.onlinefootballmanager.model.Country;
import com.appbasics.onlinefootballmanager.model.LeagueInstance;
import com.appbasics.onlinefootballmanager.model.Region;
import com.appbasics.onlinefootballmanager.repository.mongo.CountryRepository;
import com.appbasics.onlinefootballmanager.repository.mongo.LeagueInstanceRepository;
import com.appbasics.onlinefootballmanager.repository.mongo.RegionRepository;
import com.appbasics.onlinefootballmanager.service.LeagueSelectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/selection")
@RequiredArgsConstructor
public class RegionCountryLeagueController {

    private final RegionRepository regionRepository;
    private final CountryRepository countryRepository;
    private final LeagueInstanceRepository leagueInstanceRepository;
    private final LeagueSelectionService leagueSelectionService;

    @PostMapping("/assign-league")
    public Mono<ResponseEntity<LeagueInstance>> assignLeague(@RequestBody LeagueSelectionRequest req) {
        return leagueSelectionService.selectOrCreateLeagueInstance(
                req.getRegionId(),
                req.getBaseLeagueId(),
                        req.getManagerId())
                .map(ResponseEntity::ok);
    }

    @GetMapping("/regions")
    public Flux<Region> getAllRegions() {
        return regionRepository.findAll();
    }

    @GetMapping("/countries/{regionId}")
    public Flux<Country> getCountriesByRegion(@PathVariable String regionId) {
        return countryRepository.findByRegionId(regionId);
    }

    @GetMapping("/leagues/{regionId}/{countryId}")
    public Flux<LeagueInstance> getLeaguesByRegionAndCountry(
            @PathVariable String regionId,
            @PathVariable String countryId) {
        return leagueInstanceRepository.findByRegionId(regionId)
                .filter(league -> league.getCountryId().equals(countryId));
    }

    @GetMapping("/league/{leagueId}")
    public Mono<LeagueInstance> getLeagueById(@PathVariable String leagueId) {
        return leagueInstanceRepository.findById(leagueId);
    }

    @GetMapping("/status/{countryId}")
    public Mono<LeagueInstanceStatusDto> getLeagueInstanceStatus(@PathVariable String countryId) {
        return countryRepository.findById(countryId)
                .flatMap(country -> {
                    String templateId = country.getBaseLeagueIds().get(0); // choose first for now
                    return leagueInstanceRepository.findAll()
                            .filter(inst -> inst.getCountryId().equals(countryId)
                                    && inst.getTemplateId().equals(templateId))
                            .sort(Comparator.comparing(LeagueInstance::getCurrentMatchday).reversed())
                            .next()
                            .map(instance -> {
                                List<String> managerIds = instance.getReservedTeamsList().stream()
                                        .map(LeagueInstance.ReservedTeam::getManagerId)
                                        .toList();

                                return new LeagueInstanceStatusDto(
                                        instance.getInstanceId(),
                                        instance.getLeagueId(),
                                        instance.getTemplateId(),
                                        instance.getRegionId(),
                                        instance.getCountryId(),
                                        instance.getSeason(),
                                        instance.getStatus(),
                                        instance.getCurrentMatchday(),
                                        instance.getReservedTeams(),
                                        instance.isAvailable(),
                                        managerIds,
                                        instance.getTeams() != null ? instance.getTeams().size() : 0
                                );
                            });
                });
    }

    @GetMapping("/league-status/{instanceId}")
    public Mono<LeagueInstance> getLeagueStatus(@PathVariable String instanceId) {
        return leagueInstanceRepository.findFirstByInstanceId(instanceId);
    }

}
