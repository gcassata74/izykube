package com.izylife.izykube.bootstrap;

import com.izylife.izykube.repositories.NamespaceRepository;
import com.izylife.izykube.services.NamespaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NamespaceInitializer implements ApplicationRunner {

    private final NamespaceService namespaceService;

    @Override
    public void run(ApplicationArguments args) {
        namespaceService.ensureNamespaceExists("default");
    }
}
