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
 * Architectural guardrails for the backend application, enforced via ArchUnit.
 * <p>
 * This class defines and executes automated rules to ensure the codebase adheres to
 * Clean Architecture principles and modern Java best practices. It monitors:
 * <ul>
 *   <li><b>Dependency Injection:</b> Mandates constructor injection over field injection
 *   to promote immutability and testability.</li>
 *   <li><b>Logging Standards:</b> Prevents the use of legacy {@code java.util.logging}
 *   in favor of the SLF4J facade.</li>
 *   <li><b>Exception Handling:</b> Discourages throwing generic exceptions (e.g., {@code Exception},
 *   {@code RuntimeException}) to ensure precise error reporting.</li>
 *   <li><b>API Modernization:</b> Restricts legacy libraries like Joda-Time, ensuring
 *   exclusive use of the Java 8+ {@code java.time} API.</li>
 * </ul>
 * These tests act as a continuous audit of the system's structural integrity.
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
