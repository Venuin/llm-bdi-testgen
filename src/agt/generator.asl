/* Generator - Semantic Test Oracle */
/* ============================================
   2-Asamali Test Uretimi:
     Asama 1: Semantik Analiz - Kodun niyetini cikar
     Asama 2: Test Uretimi   - Oracle degerini belirle, JUnit uret
   ============================================ */

// --- INANC DURUMU ---
semantic_summary("").            // Kodun semantik beklenti ozeti

!start.

+!start : true <-
    .print("I am Generator. Waiting for final test writing...");
    !connect_to_llm.

+!connect_to_llm : true <-
    lookupArtifact("llm_connector", ArtId);
    focus(ArtId);
    lookupArtifact("json_helper", JsonId);
    focus(JsonId);
    .print("Generator: LLM and JSON connections established.").

-!connect_to_llm : true <- .wait(500); !connect_to_llm.

// =========================================================
//  ANA TETIKLEYICI: Analyzer'dan gelen write_final_test
// =========================================================
+write_final_test(Kod, FinalInputListesi)[source(analyzer)] : true <-
    .print("=== TEST ORACLE STARTED ===");
    .print("Inputs: ", FinalInputListesi);

    // --- ASAMA 1: SEMANTIK ANALIZ ---
    !build_semantic_hypothesis(Kod);

    // --- ASAMA 2: TEST URETIMI ---
    !decision_phase(Kod, FinalInputListesi, TestKodu, ExpectedOutputs, Gerekce);

    // --- EXECUTOR'A GONDER ---
    !send_to_executor(TestKodu).

// =========================================================
//  ASAMA 1: SEMANTIK ANALIZ - Kodun niyetini cikar
// =========================================================
+!build_semantic_hypothesis(Kod) : true <-
    .print("[PHASE-1] Semantic Analysis...");

   .concat("Role: Conservative Code Review Expert.",
        "\n\nYour task: Analyze if this code has PROVABLE logical errors.",
        "\n\n=== CONFIDENCE CALIBRATION RULES ===",
        "\nUse this strict scoring system:",
        "\n• CONFIDENCE = 95-100%: Code has MULTIPLE independent logical flaws that will ALWAYS fail",
        "\n• CONFIDENCE = 80-94%: Code has ONE clear logical error with concrete counter-example",
        "\n• CONFIDENCE = 60-79%: Code MIGHT have issues but works for most inputs",
        "\n• CONFIDENCE = 40-59%: Code looks unusual but may be intentional design",
        "\n• CONFIDENCE = 0-39%: Code appears correct or issues are ambiguous",
        "\n\n=== EVALUATION CRITERIA ===",
        "\n1. Does the code have syntax errors? (compile failure = 95%)",
        "\n2. Does it have impossible conditions? (if (x > 5 && x < 3) = 90%)",
        "\n3. Does it return wrong type? (should return int but returns String = 95%)",
        "\n4. Does it have off-by-one errors with PROOF? (provide counter-example = 85%)",
        "\n5. Does it contradict its own logic? (sets x=5 then assumes x=10 = 90%)",
        "\n\n=== CRITICAL RULES ===",
        "\n⚠️ START WITH CONFIDENCE = 0% and INCREASE only with solid evidence",
        "\n⚠️ If code is syntactically valid and logically coherent: CONFIDENCE < 80%",
        "\n⚠️ If you cannot provide a SPECIFIC failing test case: CONFIDENCE < 70%",
        "\n⚠️ Unusual patterns are NOT errors: CONFIDENCE < 60%",
        "\n⚠️ Missing edge case handling is NOT an error: CONFIDENCE < 50%",
        "\n⚠️ Different implementation style is NOT an error: CONFIDENCE < 40%",
        "\n\n=== OUTPUT FORMAT ===",
        "\n- INTENT: [what the code should do]",
        "\n- IMPLEMENTATION: [what the code actually does]",
        "\n- STATUS: CORRECT / INCORRECT",
        "\n- CONFIDENCE: [0-100]%",
        "\n- EVIDENCE: [specific counter-example input that will fail, or 'none']",
        "\n- NOTES: [only if STATUS=INCORRECT and CONFIDENCE>=80%]",
        "\n\n=== EXAMPLES ===",
        "\nExample 1 - LOW CONFIDENCE (Unusual but correct):",
        "Code: public int max(int a, int b) { return a > b ? a : b; }",
        "Analysis: STATUS=CORRECT, CONFIDENCE=0%, EVIDENCE=none",
        "\nExample 2 - HIGH CONFIDENCE (Provable error):",
        "Code: public int max(int a, int b) { return a > b ? b : a; } // returns MINIMUM",
        "Analysis: STATUS=INCORRECT, CONFIDENCE=95%, EVIDENCE=[5,3] returns 3 not 5",
        "\n\n=== SOURCE CODE TO ANALYZE ===\n", Kod,
        "\n\nREMEMBER: Be conservative. Most working code is CORRECT.",
        SemanticPrompt);

    askChatGPT(SemanticPrompt, SemanticOzet);
    -+semantic_summary(SemanticOzet);
    .print("================================================");
    .print("         SEMANTIC ANALYSIS RESULT               ");
    .print("================================================");
    .print(SemanticOzet);
    .print("================================================").

// =========================================================
//  ASAMA 2: TEST URETIMI - Oracle degeri belirle, JUnit uret
// =========================================================
+!decision_phase(Kod, InputListesi, TestKodu, ExpectedOutputs, Gerekce) : semantic_summary(SemanticOzet) <-
    .print("[PHASE-2] Test Generation...");
    
    // Input sayısını hesapla
    .length(InputListesi, InputCount);

    .concat("Role: Java Test Oracle Engineer with Reflexion capability.",
            "\n\n TASK: Write the FINAL JUnit 5 test class. You are the ORACLE - you decide the EXPECTED values.",
            "\n\n SOURCE CODE:\n", Kod,
            "\n\n VERIFIED INPUTS (", InputCount, " inputs): ", InputListesi,
            "\n\n SEMANTIC EXPECTATION (what the code SHOULD do):\n", SemanticOzet,
            "\n\n CRITICAL CONSTRAINT:",
            "\n ⚠️ Create EXACTLY ", InputCount, " test methods - ONE for EACH input in the VERIFIED INPUTS list above.",
            "\n ⚠️ DO NOT add extra test cases beyond the provided inputs.",
            "\n ⚠️ DO NOT skip any input from the list.",
            "\n\n ORACLE RULES:",
            "\n 1. For each input, decide the CORRECT expected output based on the CODE'S INTENT, not its implementation.",
            "\n 2. Your expected value should reflect the INTENDED behavior based on semantic analysis.",
            "\n 3. If the method returns void (prints output), capture output using ByteArrayOutputStream and compare strings.",
            "\n 4. If the method returns a value, use assertEquals with the ORACLE value you determined.",
            "\n 5. IMPORTANT: For each assertion, add a comment: // ORACLE_REASON: <why this value>",
            "\n 6. IMPORTANT: For each assertion, also add: // EXPECTED_OUTPUT: <the value>",
            "\n\n GENERAL RULES:",
            "\n 1. Create one @Test method for each input in the VERIFIED INPUTS list (total: ", InputCount, " tests).",
            "\n 2. Name the class 'HesaplamaTest'.",
            "\n 3. Output ONLY the Java code - no explanations.",
            "\n 4. Do NOT pass 'null' to primitive types (int, double).",
            "\n 5. CRITICAL: Ensure every method name is UNIQUE (testCase1, testCase2, testCase3...).",
            "\n 6. Consider the semantic analysis when designing tests.",
            "\n 7. For void methods that print, use ByteArrayOutputStream to capture output.",
            TestPrompt);

    askChatGPT(TestPrompt, RawTestKodu);
    
    // Markdown bloklarini temizle (```java ... ```)
    sanitizeLLMResponse(RawTestKodu, TestKodu).

// =========================================================
//  EXECUTOR'A GONDER
// =========================================================
+!send_to_executor(FinalKod) : true <-
    .print("================================================");
    .print("            FINAL TEST CODE                     ");
    .print("================================================");
    .print(FinalKod);
    .print("================================================");
    .print("Sending code to Executor for testing...");
    .send(executor, tell, run_generated_test(FinalKod)).


