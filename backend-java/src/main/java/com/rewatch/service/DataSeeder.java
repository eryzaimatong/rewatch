package com.rewatch.service;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.rewatch.model.Title;
import com.rewatch.repository.TitleRepository;

@Component
public class DataSeeder implements CommandLineRunner {

    private final TitleRepository titlerepo;

    public DataSeeder(TitleRepository titlerepo) {
        this.titlerepo = titlerepo;
    }

    @Override
    public void run(String... args) {
        // ADD THIS LINE: wipes out old flat movies so new traits always save!
        titlerepo.deleteAll(); 

        Title t1 = new Title();
        t1.setTitle("Interstellar");
        t1.setYear(2014);
        t1.setSynopsis("A team of explorers travel through a wormhole in space in an attempt to ensure humanity's survival.");
        t1.setComfort(30);
        t1.setRomance(20);
        t1.setSlowBurn(85);
        t1.setNostalgia(70);
        t1.setSadness(90);
        titlerepo.save(t1);

        Title t2 = new Title();
        t2.setTitle("Parasite");
        t2.setYear(2019);
        t2.setSynopsis("Greed and class discrimination threaten the newly formed symbiotic relationship between the wealthy Park family and the destitute Kim clan.");
        t2.setComfort(15);
        t2.setRomance(10);
        t2.setSlowBurn(75);
        t2.setNostalgia(40);
        t2.setSadness(85);
        titlerepo.save(t2);

        Title t3 = new Title();
        t3.setTitle("Arrival");
        t3.setYear(2016);
        t3.setSynopsis("A linguist works with the military to communicate with alien lifeforms after twelve mysterious spacecraft appear around the world.");
        t3.setComfort(40);
        t3.setRomance(30);
        t3.setSlowBurn(90);
        t3.setNostalgia(60);
        t3.setSadness(75);
        titlerepo.save(t3);

        Title t4 = new Title();
        t4.setTitle("Reply 1988");
        t4.setYear(2015);
        t4.setSynopsis("Follows the lives of five friends and their families living in the same neighborhood of Ssangmun-dong, Seoul.");
        t4.setComfort(95);
        t4.setRomance(85);
        t4.setSlowBurn(60);
        t4.setNostalgia(95);
        t4.setSadness(70);
        titlerepo.save(t4);

        Title t5 = new Title();
        t5.setTitle("Hospital Playlist");
        t5.setYear(2020);
        t5.setSynopsis("Five doctors who have been friends since medical school navigate the everyday challenges of working at the same hospital.");
        t5.setComfort(90);
        t5.setRomance(80);
        t5.setSlowBurn(50);
        t5.setNostalgia(75);
        t5.setSadness(40);
        titlerepo.save(t5);
    }
}