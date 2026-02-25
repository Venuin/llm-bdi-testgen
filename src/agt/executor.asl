/* Executor - Kalite Kontrol (JUnit Runner) (eski: Dave) */

// İnançlar
successful_inputs_info([], []).   // (TumInputlar, BasariliInputlar)

!start.

+!start : true <-
    .print("I am Executor. Ready to run tests.");
    makeArtifact("junit_runner", "tools.JUnitRunnerArtifact", [], JunitId);
    focus(JunitId);
    lookupArtifact("output_parser", ParseId);
    focus(ParseId).

// Analyzer'dan gelen successful_inputs bilgisini sakla
+successful_inputs_info(TumInputlar, BasariliInputlar)[source(analyzer)] <-
    -+successful_inputs_info(TumInputlar, BasariliInputlar);
    .length(TumInputlar, NTum);
    .length(BasariliInputlar, NBasarili);
    .print("Coverage info received: ", NTum, " total inputs, ", NBasarili, " successful").

// Generator'dan gelen mesajı dinle
+run_generated_test(TestKodu)[source(generator)] : successful_inputs_info(TumInputlar, BasariliInputlar) <-
    .print("Test code received from Generator. Running with JUnit...");
    .print("------------------------------------------------");

    // Artifact'i çağır
    runDynamicJUnitTest(TestKodu, Rapor);

    .print(Rapor);
    .print("------------------------------------------------");

    // Coverage katkısı olan inputları raporla
    .print("=== COVERAGE REPORT ===");
    .length(TumInputlar, NTum);
    .length(BasariliInputlar, NBasarili);
    .print("Total tests: ", NTum, " | New line coverage contributors: ", NBasarili);
    .print("Coverage-contributing inputs (✅) and non-contributors (⚪):");
    !print_input_coverage(TumInputlar, BasariliInputlar, 1, Rapor, 0, 0);
    .print("=======================");

    if (.substring("RESULT: SUCCESS", Rapor)) {
        .print(">>> ALL TESTS PASSED! PROJECT COMPLETED SUCCESSFULLY. <<<");
        .send(generator, tell, test_feedback("SUCCESS", Rapor));
    } else {
        .print("!!! SOME TESTS FAILED. THERE MAY BE BUGS IN THE SOFTWARE !!!");
        .send(generator, tell, test_feedback("FAILURE", Rapor));
    }.

// Boş liste -> pass özetini yazdır
+!print_input_coverage([], _, _, _, PassToplam, PassSuccessful) : true <-
    .print("--- Pass Summary ---");
    .print("  Total passed       : ", PassToplam);
    .print("  Passed (successful): ", PassSuccessful).

// Her input için yazdır ve pass sayısını güncelle
+!print_input_coverage([H|T], BasariliInputlar, N, Rapor, PT, PS) : true <-
    .concat("TESTPASS:testCase", N, "|", PassStr);
    if (.substring(PassStr, Rapor)) {
        PT1 = PT + 1;
        PassLabel = "PASS"
    } else {
        PT1 = PT;
        PassLabel = "FAIL"
    };
    if (.member(H, BasariliInputlar)) {
        if (.substring(PassStr, Rapor)) { PS1 = PS + 1 } else { PS1 = PS };
        .print("  ✅ testCase", N, " [", PassLabel, "]: ", H, " (contributed new coverage)")
    } else {
        PS1 = PS;
        .print("  ⚪ testCase", N, " [", PassLabel, "]: ", H, " (no coverage contribution)")
    };
    N1 = N + 1;
    !print_input_coverage(T, BasariliInputlar, N1, Rapor, PT1, PS1).
