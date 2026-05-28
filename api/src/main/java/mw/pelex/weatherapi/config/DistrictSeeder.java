package mw.pelex.weatherapi.config;

import mw.pelex.weatherapi.model.District;
import mw.pelex.weatherapi.repository.DistrictRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DistrictSeeder implements CommandLineRunner {

    private final DistrictRepository districtRepository;

    public DistrictSeeder(DistrictRepository districtRepository) {
        this.districtRepository = districtRepository;
    }

    @Override
    public void run(String... args) {
        if (districtRepository.count() > 0) return; // Already seeded

        List<District> districts = List.of(
            // Northern Region
            new District(null, "Chitipa",    "Northern", -9.7000,  33.2667, 163_000L),
            new District(null, "Karonga",    "Northern", -9.9333,  33.9333, 380_000L),
            new District(null, "Rumphi",     "Northern", -11.0167, 33.8667, 254_000L),
            new District(null, "Mzimba",     "Northern", -11.9000, 33.6000, 745_000L),
            new District(null, "Mzuzu",      "Northern", -11.4656, 34.0207, 221_000L),
            new District(null, "Likoma",     "Northern", -12.0667, 34.7333, 12_000L),
            new District(null, "Nkhata Bay", "Northern", -11.6000, 34.3000, 225_000L),

            // Central Region
            new District(null, "Kasungu",    "Central",  -13.0333, 33.4833, 740_000L),
            new District(null, "Nkhotakota", "Central",  -12.9167, 34.2833, 368_000L),
            new District(null, "Ntchisi",    "Central",  -13.3833, 33.8667, 240_000L),
            new District(null, "Dowa",       "Central",  -13.6500, 33.9333, 679_000L),
            new District(null, "Salima",     "Central",  -13.7833, 34.4333, 461_000L),
            new District(null, "Lilongwe",   "Central",  -13.9669, 33.7873, 1_500_000L),
            new District(null, "Mchinji",    "Central",  -13.8000, 32.8833, 528_000L),
            new District(null, "Dedza",      "Central",  -14.3667, 34.3333, 706_000L),
            new District(null, "Ntcheu",     "Central",  -14.8167, 34.6333, 614_000L),

            // Southern Region
            new District(null, "Mangochi",   "Southern", -14.4667, 35.2667, 1_100_000L),
            new District(null, "Machinga",   "Southern", -15.1333, 35.5167, 676_000L),
            new District(null, "Zomba",      "Southern", -15.3833, 35.3333, 680_000L),
            new District(null, "Chiradzulu", "Southern", -15.6833, 35.1500, 347_000L),
            new District(null, "Blantyre",   "Southern", -15.7861, 35.0058, 1_200_000L),
            new District(null, "Mwanza",     "Southern", -15.6167, 34.5167, 138_000L),
            new District(null, "Thyolo",     "Southern", -16.0667, 35.1333, 646_000L),
            new District(null, "Mulanje",    "Southern", -16.0333, 35.5000, 691_000L),
            new District(null, "Phalombe",   "Southern", -15.8167, 35.6500, 346_000L),
            new District(null, "Chikwawa",   "Southern", -16.0333, 34.8000, 509_000L),
            new District(null, "Nsanje",     "Southern", -16.9167, 35.2667, 267_000L),
            new District(null, "Balaka",     "Southern", -14.9833, 34.9667, 397_000L)
        );

        districtRepository.saveAll(districts);
        System.out.println("✅ Seeded all 28 Malawi districts.");
    }
}
