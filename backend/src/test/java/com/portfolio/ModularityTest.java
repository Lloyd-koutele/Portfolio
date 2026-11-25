package com.portfolio;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Fails when a module reaches into another module's internals or when modules form a cycle.
 */
class ModularityTest
{
    private final ApplicationModules modules = ApplicationModules.of(PortfolioApplication.class);

    @Test
    void modulesRespectTheirBoundaries()
    {
        modules.verify();
    }

    @Test
    void writesModuleDocumentation()
    {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
