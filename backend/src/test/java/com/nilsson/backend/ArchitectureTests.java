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
 * Enforces architectural standards and best practices across the codebase.
 */
@AnalyzeClasses(packages = "com.nilsson.backend", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

    /**
     * Enforce Constructor Injection.
     * Field injection (@Autowired on fields) is considered harmful as it hides dependencies
     * and makes testing difficult.
     */
    @ArchTest
    static final ArchRule no_field_injection = fields()
            .should().notBeAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
            .because("Constructor injection should be used instead of field injection for better testability and immutability.");

    /**
     * Enforce SLF4J over java.util.logging.
     */
    @ArchTest
    static final ArchRule no_java_util_logging = NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

    /**
     * Prevent throwing raw RuntimeException or Exception.
     * Custom exceptions or specific standard exceptions should be used.
     */
    @ArchTest
    static final ArchRule no_generic_exceptions = NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS;

    @ArchTest
    static final ArchRule no_jodatime = noClasses()
            .should().dependOnClassesThat().resideInAPackage("joda.time..")
            .because("Java 21 Date/Time API (java.time) should be used instead of Joda Time.");
}