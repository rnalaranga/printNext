package com.rr.secureprint;

import com.rr.secureprint.service.SimulationService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.InetAddress;
import java.util.logging.Logger;

@SpringBootApplication
@EnableScheduling
public class RrSecurePrintApplication {

    private static final Logger LOGGER = Logger.getLogger(RrSecurePrintApplication.class.getName());

    public static void main(String[] args) {
        SpringApplication.run(RrSecurePrintApplication.class, args);
    }

    @Bean
    public CommandLineRunner initData(SimulationService simulationService, Environment env) {
        return args -> {
            simulationService.seedInitialDemoJobs();

            String port = env.getProperty("server.port", "8080");
            String hostAddress = "127.0.0.1";
            try {
                hostAddress = InetAddress.getLocalHost().getHostAddress();
            } catch (Exception ignored) {}

            LOGGER.info("============================================================================");
            LOGGER.info("   nexPrint — ENTERPRISE PULL-PRINT SERVER STARTED SUCCESSFULLY            ");
            LOGGER.info("============================================================================");
            LOGGER.info(" >> Local EWB Interface:      http://localhost:" + port + "/");
            LOGGER.info(" >> Toshiba MFP LAN Address:  http://" + hostAddress + ":" + port + "/");
            LOGGER.info(" >> Administrator Portal:     http://localhost:" + port + "/admin.html");
            LOGGER.info(" >> REST API Health:          http://localhost:" + port + "/api/health");
            LOGGER.info("----------------------------------------------------------------------------");
            LOGGER.info(" Demo Touchscreen Credentials:");
            LOGGER.info("  - Engineer PIN: '1234' (User: John Doe - jdoe)");
            LOGGER.info("  - Director PIN: '1234' (User: Mary Smith - msmith)");
            LOGGER.info("  - Admin PIN:    '1234' (User: Administrator - admin)");
            LOGGER.info("============================================================================");
        };
    }
}
