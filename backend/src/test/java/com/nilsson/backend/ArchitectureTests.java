package com.nilsson.backend;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

/**
 * ArchitectureTests is responsible for validating the structural integrity and coding standards of the backend application.
 * It utilizes ArchUnit to enforce rules such as dependency injection patterns, logging frameworks, exception handling,
 * and the use of modern Java APIs. These tests ensure that the codebase remains maintainable, testable, and consistent
 * with the defined architectural principles.
 */
@AnalyzeClasses(packages = "com.nilsson.backend", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

    @ArchTest
    static final ArchRule no_field_injection = fields()
            .should().notBeAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .because("Constructor injection should be used instead of field injection for better testability and immutability.");

    @ArchTest
    static final ArchRule no_java_util_logging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    @ArchTest
    static final ArchRule no_generic_exceptions = NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

    @ArchTest
    static final ArchRule no_jodatime = noClasses()
            .should().dependOnClassesThat().resideInAPackage("joda.time..")
            .because("Java 21 Date/Time API (java.time) should be used instead of Joda Time.");
}