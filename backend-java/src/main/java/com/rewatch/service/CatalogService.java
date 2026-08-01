package com.rewatch.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.rewatch.model.Title;
import com.rewatch.repository.TitleRepository;

import jakarta.annotation.PostConstruct;

@Service
public class CatalogService {

    @Autowired
    private TitleRepository titleRepo;

    @PostConstruct
    public void seedCatalog() {
        if (titleRepo.count() > 0) {
            return;
        }

        String[][] seedData = {
            {"157336", "Interstellar", "2014", "movie", "/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg", "A team of explorers travel through a wormhole in space.", "68,95,82,90,40,60,98,15,78,70", "4.8", "88.5", "0.30", "0.20", "0.70", "0.90", "0.85"},
            {"496243", "Parasite", "2019", "movie", "/7IiTTgloJzvGI1TAYymCfbfl3vT.jpg", "Greed and class discrimination threaten the newly formed symbiotic relationship.", "78,72,74,80,82,45,92,62,94,76", "4.6", "92.0", "0.10", "0.10", "0.80", "0.40", "0.70"},
            {"339788", "Arrival", "2016", "movie", "/pEFRzXtLmxYNjGd0XqJDHPDFKB2.jpg", "A linguist works with the military to communicate with alien lifeforms.", "50,85,88,85,45,30,95,10,70,80", "4.5", "79.4", "0.40", "0.10", "0.85", "0.60", "0.75"},
            {"76479", "The Boys", "2019", "series", "/2jnXKpx2L2n34bT83H26WcftM22.jpg", "A group of vigilantes set out to take down corrupt superheroes.", "88,60,65,75,85,90,70,75,88,68", "4.4", "95.1", "0.10", "0.20", "0.40", "0.20", "0.50"},
            {"93405", "Squid Game", "2021", "kdrama", "/dDlEmu3EZ0Pgg93K2SVNLCjCSvE.jpg", "Hundreds of cash-strapped players accept an invitation to deadly games.", "90,85,70,82,90,75,80,20,95,75", "4.5", "98.0", "0.10", "0.15", "0.60", "0.30", "0.80"},
            {"653346", "Our Beloved Summer", "2021", "kdrama", "/6d6c483b4e6d4c6d6c6d6c6d6c.jpg", "Ex-lovers are pulled back in front of the camera years after a documentary.", "40,80,50,85,30,10,75,60,35,92", "4.7", "84.2", "0.88", "0.95", "0.90", "0.85", "0.40"},
            {"37854", "One Piece", "1999", "anime", "/e3NBGiAifW9Xt8xD5tpARskjccO.jpg", "Pirates set off to find the legendary treasure.", "85,75,60,88,20,90,80,85,70,88", "4.9", "99.5", "0.80", "0.20", "0.30", "0.90", "0.40"},
            {"19995", "Avatar", "2009", "movie", "/kyeqWdyUXW608qlYkRqosgNaSGj.jpg", "A marine on an alien planet protects an indigenous civilization.", "75,70,55,98,35,88,65,25,78,60", "4.3", "91.0", "0.50", "0.60", "0.50", "0.70", "0.40"}
        };

        for (String[] row : seedData) {
            Title t = new Title();
            t.setTmdbId(Integer.valueOf(row[0]));
            t.setTitle(row[1]);
            t.setYear(Integer.valueOf(row[2]));
            t.setType(row[3]);
            t.setPoster(row[4]);
            t.setSynopsis(row[5]);
            t.setStoryprint(row[6]);
            t.setQuality(Double.valueOf(row[7]));
            t.setPopularity(Double.valueOf(row[8]));
            t.setComfort(Double.valueOf(row[9]));
            t.setRomance(Double.valueOf(row[10]));
            t.setSlowBurn(Double.valueOf(row[11]));
            t.setNostalgia(Double.valueOf(row[12]));
            t.setSadness(Double.valueOf(row[13]));
            titleRepo.save(t);
        }
    }

    public List<Title> getAllTitles() {
        return titleRepo.findAll();
    }

    public Title getTitleById(Long id) {
        return titleRepo.findById(id).orElse(null);
    }
}