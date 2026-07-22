package com.iuims.registrar;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.iuims.registrar")
class LayeredArchitectureTests {

    @ArchTest
    static final ArchRule controllers_do_not_depend_on_persistence = noClasses()
        .that().resideInAPackage("..controller..")
        .should().dependOnClassesThat().resideInAnyPackage("..repository..", "..entity..");

    @ArchTest
    static final ArchRule entities_do_not_depend_on_web_or_services = noClasses()
        .that().resideInAPackage("..entity..")
        .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..service..");

    @ArchTest
    static final ArchRule repositories_do_not_depend_on_controllers = noClasses()
        .that().resideInAPackage("..repository..")
        .should().dependOnClassesThat().resideInAPackage("..controller..");
}


