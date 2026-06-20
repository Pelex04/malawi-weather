package mw.pelex.weatherapi.config;

import mw.pelex.weatherapi.model.District;
import mw.pelex.weatherapi.repository.DistrictRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds Malawi's 28 districts exactly once.
 *
 * FIX S6: The original guard was `if (count > 0) return` — meaning a partially
 * seeded database (e.g. 10 rows after a first-run crash) would never be completed.
 *
 * Now uses a per-district saveIfAbsent pattern: for each district in the canonical
 * list, skip if a row with that name already exists, otherwise insert. This makes
 * seeding fully idempotent regardless of how many rows already exist.
 */
@Component
public class DistrictSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DistrictSeeder.class);

    private final DistrictRepository districtRepository;

    public DistrictSeeder(DistrictRepository districtRepository) {
        this.districtRepository = districtRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        int seeded = 0;
        for (DistrictDef def : DISTRICTS) {
            boolean exists = districtRepository
                .findByNameIgnoreCase(def.name()).isPresent();
            if (!exists) {
                District d = new District();
                d.setName(def.name());
                d.setRegion(def.region());
                d.setLatitude(def.lat());
                d.setLongitude(def.lon());
                districtRepository.save(d);
                seeded++;
            }
        }
        if (seeded > 0) {
            log.info("District seed: inserted {} new district(s)", seeded);
        } else {
            log.debug("District seed: all {} districts already present", DISTRICTS.size());
        }
    }

    // ── Canonical district data ───────────────────────────────────────────────

    private record DistrictDef(String name, String region, double lat, double lon) {}

    private static final List<DistrictDef> DISTRICTS = List.of(
        // Northern Region
        new DistrictDef("Chitipa",    "Northern", -9.7040,  33.2707),
        new DistrictDef("Karonga",    "Northern", -9.9333,  33.9333),
        new DistrictDef("Likoma",     "Northern", -12.0667, 34.7333),
        new DistrictDef("Mzimba",     "Northern", -11.8972, 33.5972),
        new DistrictDef("Nkhata Bay", "Northern", -11.6000, 34.3000),
        new DistrictDef("Rumphi",     "Northern", -11.0100, 33.8600),
        // Central Region
        new DistrictDef("Dedza",      "Central",  -14.3667, 34.3333),
        new DistrictDef("Dowa",       "Central",  -13.6600, 33.9300),
        new DistrictDef("Kasungu",    "Central",  -13.0333, 33.4833),
        new DistrictDef("Lilongwe",   "Central",  -13.9626, 33.7741),
        new DistrictDef("Mchinji",    "Central",  -13.8000, 32.9000),
        new DistrictDef("Nkhotakota", "Central",  -12.9256, 34.2958),
        new DistrictDef("Ntcheu",     "Central",  -14.8200, 34.6400),
        new DistrictDef("Ntchisi",    "Central",  -13.3800, 33.6300),
        new DistrictDef("Salima",     "Central",  -13.7800, 34.4600),
        // Southern Region
        new DistrictDef("Balaka",     "Southern", -14.9900, 34.9600),
        new DistrictDef("Blantyre",   "Southern", -15.7861, 35.0058),
        new DistrictDef("Chikwawa",   "Southern", -16.0300, 34.8000),
        new DistrictDef("Chiradzulu", "Southern", -15.7000, 35.1500),
        new DistrictDef("Machinga",   "Southern", -14.9700, 35.5200),
        new DistrictDef("Mangochi",   "Southern", -14.4784, 35.2645),
        new DistrictDef("Mulanje",    "Southern", -16.0333, 35.5000),
        new DistrictDef("Mwanza",     "Southern", -15.6200, 34.5200),
        new DistrictDef("Neno",       "Southern", -15.4100, 34.6500),
        new DistrictDef("Nsanje",     "Southern", -16.9200, 35.2700),
        new DistrictDef("Phalombe",   "Southern", -15.8100, 35.6600),
        new DistrictDef("Thyolo",     "Southern", -16.0700, 35.1400),
        new DistrictDef("Zomba",      "Southern", -15.3833, 35.3333)
    );
}
