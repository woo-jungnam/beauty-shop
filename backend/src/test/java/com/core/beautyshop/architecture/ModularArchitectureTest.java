package com.core.beautyshop.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class ModularArchitectureTest {

    private static JavaClasses classes;
    private static Set<String> moduleNames;

    @BeforeAll
    public static void setUp() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                // Maven supports custom build directories; test fixtures never belong to the production architecture.
                .withImportOption(location -> !location.asURI().toString().replace('\\', '/').contains("/test-classes/"))
                .importPackages("com.core.beautyshop.modules");
        moduleNames = classes.stream()
                .map(JavaClass::getPackageName)
                .map(ModularArchitectureTest::extractModuleName)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Test
    @DisplayName("No module should directly access Repository of other modules")
    public void noCrossModuleRepositoryAccess() {
        for (String sourceModule : moduleNames) {
            for (String targetModule : moduleNames) {
                if (!sourceModule.equals(targetModule)) {
                    DescribedPredicate<JavaClass> isRepositoryInTarget = resideInAPackage("..modules." + targetModule + "..")
                            .and(simpleNameEndingWith("Repository"));

                    ArchRule rule = noClasses()
                            .that().resideInAPackage("..modules." + sourceModule + "..")
                            .should().dependOnClassesThat(isRepositoryInTarget)
                            .because(String.format("Module '%s' must not access Repositories of module '%s'", sourceModule, targetModule));

                    rule.check(classes);
                }
            }
        }
    }

    @Test
    @DisplayName("No module should directly access internal domain types of other modules")
    public void noCrossModuleDomainAccess() {
        for (String sourceModule : moduleNames) {
            for (String targetModule : moduleNames) {
                if (!sourceModule.equals(targetModule)) {
                    ArchRule rule = noClasses()
                            .that().resideInAPackage("..modules." + sourceModule + "..")
                            .should().dependOnClassesThat().resideInAPackage(
                                    "..modules." + targetModule + ".domain.."
                            )
                            .because(String.format(
                                    "Module '%s' must use the public API of module '%s'",
                                    sourceModule,
                                    targetModule
                            ));

                    rule.check(classes);
                }
            }
        }
    }

    private static String extractModuleName(String packageName) {
        String modulePackage = packageName.substring("com.core.beautyshop.modules.".length());
        int separatorIndex = modulePackage.indexOf('.');
        return separatorIndex >= 0 ? modulePackage.substring(0, separatorIndex) : modulePackage;
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

    @Test
    @DisplayName("Inventory module should not directly depend on internal domain entities or repositories of catalog")
    public void inventoryModuleShouldNotDependOnCatalogDomain() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..modules.inventory..")
                .should().dependOnClassesThat().resideInAPackage("..modules.catalog.domain..")
                .because("Inventory module must communicate with catalog solely through CatalogFacade");

        rule.check(classes);
    }
}
