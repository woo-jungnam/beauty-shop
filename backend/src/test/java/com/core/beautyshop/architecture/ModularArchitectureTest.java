package com.core.beautyshop.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class ModularArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    public static void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.core.beautyshop.modules");
    }

    @Test
    @DisplayName("No module should directly access Repository of other modules")
    public void noCrossModuleRepositoryAccess() {
        String[] moduleNames = {"identity", "catalog", "inventory", "cart", "order", "payment", "spa"};

        for (String sourceModule : moduleNames) {
            for (String targetModule : moduleNames) {
                if (!sourceModule.equals(targetModule)) {
                    ArchRule rule = noClasses()
                            .that().resideInAPackage("..modules." + sourceModule + "..")
                            .should().dependOnClassesThat().resideInAPackage("..modules." + targetModule + ".domain..")
                            .andShould().haveSimpleNameEndingWith("Repository")
                            .because(String.format("Module '%s' must not access Repositories of module '%s'", sourceModule, targetModule));

                    rule.check(classes);
                }
            }
        }
    }

    @Test
    @DisplayName("Order module should not depend directly on internal domain entities of other modules")
    public void orderModuleShouldNotDirectlyDependOnOtherModuleDomainEntities() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.order.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..modules.identity.domain..",
                        "..modules.catalog.domain..",
                        "..modules.inventory.domain..",
                        "..modules.spa.domain.."
                )
                .because("Order domain entities must be decoupled and only reference other modules by ID");

        rule.check(classes);
    }

    @Test
    @DisplayName("Cart module domain should not directly depend on internal domain entities of other modules")
    public void cartModuleDomainShouldNotDependOnOtherModuleEntities() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.cart.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..modules.identity.domain..",
                        "..modules.catalog.domain.."
                )
                .because("Cart domain entities must be decoupled");

        rule.check(classes);
    }

    @Test
    @DisplayName("Inventory module domain should not directly depend on internal domain entities of catalog")
    public void inventoryModuleDomainShouldNotDependOnCatalogEntities() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.inventory.domain..")
                .should().dependOnClassesThat().resideInAPackage("..modules.catalog.domain..")
                .because("Inventory domain entities must reference variants by ID");

        rule.check(classes);
    }
}
