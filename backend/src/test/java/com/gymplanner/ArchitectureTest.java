package com.gymplanner;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies the modular monolith boundaries of spec section 6.1:
 * allowed dependencies, no cycles, no access to other modules' {@code internal} packages.
 */
class ArchitectureTest {

    private static final String BASE = "com.gymplanner";
    private static final List<String> MODULES =
            List.of("identity", "catalog", "workoutplan", "assignment", "calendar", "execution");

    /** Allowed module -> module dependencies ({@code shared} is always allowed). */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "identity", Set.of(),
            "catalog", Set.of(),
            "workoutplan", Set.of("catalog"),
            "assignment", Set.of("identity", "workoutplan"),
            "calendar", Set.of("assignment", "workoutplan"),
            "execution", Set.of("calendar", "assignment", "workoutplan"));

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE);
    }

    @Test
    void modulesAreFreeOfCycles() {
        slices().matching(BASE + ".(*)..").should().beFreeOfCycles().check(classes);
    }

    @Test
    void modulesOnlyDependOnAllowedModules() {
        for (String module : MODULES) {
            List<String> forbidden = MODULES.stream()
                    .filter(other -> !other.equals(module) && !ALLOWED.get(module).contains(other))
                    .map(other -> BASE + "." + other + "..")
                    .toList();
            if (forbidden.isEmpty()) {
                continue;
            }
            noClasses().that().resideInAPackage(BASE + "." + module + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(forbidden.toArray(String[]::new))
                    .because("module '" + module + "' may only depend on " + ALLOWED.get(module))
                    .allowEmptyShould(true)
                    .check(classes);
        }
    }

    @Test
    void internalPackagesAreNotAccessedByOtherModules() {
        for (String module : MODULES) {
            noClasses().that().resideOutsideOfPackage(BASE + "." + module + "..")
                    .should().dependOnClassesThat().resideInAPackage(BASE + "." + module + ".internal..")
                    .because("only the 'api' package of a module is public")
                    .allowEmptyShould(true)
                    .check(classes);
        }
    }

    @Test
    void sharedDoesNotDependOnDomainModules() {
        noClasses().that().resideInAPackage(BASE + ".shared..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        MODULES.stream().map(m -> BASE + "." + m + "..").toArray(String[]::new))
                .check(classes);
    }

    @Test
    void jpaEntitiesStayInsideInternalPackages() {
        classes().that().areAnnotatedWith(jakarta.persistence.Entity.class)
                .should().resideInAPackage("..internal..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void controllersDoNotUseRepositoriesOfOtherModules() {
        noClasses().that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .should().dependOnClassesThat().areAssignableTo(org.springframework.data.repository.Repository.class)
                .because("controllers go through services")
                .allowEmptyShould(true)
                .check(classes);
    }
}
