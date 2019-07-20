package sansonetti;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import sansonetti.controller.Crawler;
@SpringBootApplication
public class TwitterApplication implements CommandLineRunner {
    @Autowired
    Crawler crawler;
    public static void main(String[] args) {
        SpringApplication.run(TwitterApplication.class, args);
    }
    public void run(String... args) throws InterruptedException {
        crawler.readConfigFile("config.txt");
        crawler.readCSVFileVocabulary("nuovo_lessico_mappa_con_plurale.csv");
        crawler.start();
        Thread.currentThread().join();
    }
}
