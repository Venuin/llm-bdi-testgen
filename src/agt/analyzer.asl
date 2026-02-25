/* Analyzer - Stratejik Test Mühendisi (eski: Bob / sample_agent) */

// İnançlar
current_inputs([]).
successful_inputs([]).
visited_paths([]).
visual_coverage("").

!start.

+!start : true <-
    .print("Hello. Switching to Strategic Test Runner mode.");

    // 1. Artifact'leri Hazırla
    makeArtifact("llm_connector", "tools.LLMTool", [], LlmId);
    makeArtifact("file_reader", "tools.FileTool", [], FileId);
    makeArtifact("test_runner", "tools.JaCoCoGenericRunner", [], TestRunnerId);
    makeArtifact("logic_mapper", "tools.LogicMapArtifact", [], LogicId);

    focus(FileId); focus(LlmId); focus(TestRunnerId); focus(LogicId);

    // 2. Kodu Oku ve Mantıksal Haritayı Çıkar
    readSourceCode("TriangleClassifier", OkunanKod);
    +source_code(OkunanKod);

    extractLogicPaths(OkunanKod, PathListesi); // JavaParser ile güncel analiz
    +logic_map(PathListesi);

    .print("Logic Map Extracted: ", PathListesi);
    .send(orchestrator, tell, logic_map(PathListesi)); // Orchestrator'a haritayı bildir

    // 3. İlk Analiz
    analyzeCodeStructure(OkunanKod, MethodName, ParamCount, ParamTypes);
    +target_method(MethodName, ParamCount);
    +parameter_types(ParamTypes);

    // İlk rastgele testi başlat
    !generate_input("INITIAL_EXPLORATION").

// --- PLANLAR ---

// Orchestrator bu inputun en az bir line kapsadığını onayladı -> başarılı listeye ekle
+good_input(Input)[source(orchestrator)] : successful_inputs(Liste) <-
    if (not .member(Input, Liste)) {
        .concat([Input], Liste, YeniListe);
        -+successful_inputs(YeniListe);
        .print("✅ Successful input recorded: ", Input)
    }.

// Orchestrator'dan gelen spesifik mantıksal hedefi gerçekleştirme planı
+!achieve_path(TargetPath)[source(orchestrator)] : source_code(Kod) & current_inputs(EskiListe) <-
    .print("Target received from Orchestrator: ", TargetPath);
    !generate_input(TargetPath).

// Orchestrator'dan MISS sonrası geri bildirim ile yeniden deneme isteği
// FailedAttemptsList: [[Input1, Reason1], [Input2, Reason2], ...] formatında tüm başarısız denemeler
+!retry_with_feedback(TargetPath, FailedAttemptsList, TargetLine)[source(orchestrator)] : true <-
    ?source_code(Kod);
    ?current_inputs(EskiListe);
    .print("❌️ Feedback Received:");
    .print("   ➢ Target Line: ", TargetLine);
    .print("   ➢ Target Path: ", TargetPath);
    .length(FailedAttemptsList, AttemptCount);
    .print("   ➢ Failed Attempt Count: ", AttemptCount);
    
    // Tüm başarısız denemeleri göster
    for (.member([Input, Reason], FailedAttemptsList)) {
        .print("      • Input: ", Input, " → ", Reason)
    };
    
    .print("🔄 Generating new input with improved strategy...");
    
    // Geliştirilmiş strateji ile yeni girdi üret (TÜM GEÇMİŞ ile)
    !generate_input_with_feedback(TargetPath, FailedAttemptsList, TargetLine).

// Analyzer - generate_input planı
// --- ANA PLAN: Girdi Üretimi ---
+!generate_input(Strategy) : true <-
    ?source_code(Kod);
    ?current_inputs(EskiListe);
    ?target_method(M, P);
    .print("Searching for input for strategy: ", Strategy);

    // 1. Benzersiz Girdi Bulma Alt Planını Çağır (0. deneme ile başla)
    !fetch_unique_input(Strategy, Kod, EskiListe, 0, FinalInput);

    // 2. Gelen FinalInput boş değilse listeye ekle
    if (FinalInput \== "SKIP") {
        .concat([FinalInput], EskiListe, YeniListe);
        -+current_inputs(YeniListe);
        
        // Orchestrator'a test edilecek inputu bildir
        .send(orchestrator, tell, input_being_tested(FinalInput));

        // Testi Çalıştır (Timeout koruması Runner tarafında olmalı)
        runTestAndMeasureCoverage(Kod, YeniListe, Rapor, HitLines, NewVisualCov);
        -+visual_coverage(NewVisualCov);
        .print(Rapor);
        .send(orchestrator, tell, analyze_coverage_report(Kod, Rapor, HitLines));
    } else {
        .print("❌ Could not find unique input in 3 attempts. Continuing with current list.");
        // Listeyi değiştirmeden rapor gönder (Orchestrator strateji değiştirsin diye)
        runTestAndMeasureCoverage(Kod, EskiListe, Rapor, HitLines, NewVisualCov);
        -+visual_coverage(NewVisualCov);
        .send(orchestrator, tell, analyze_coverage_report(Kod, Rapor, HitLines));
    }.

// --- YENİ PLAN: Geri Bildirim ile Girdi Üretimi (TÜM BAŞARISIZ DENEMELER İLE) ---
+!generate_input_with_feedback(Strategy, FailedAttemptsList, TargetLine) : true <-
    ?source_code(Kod);
    ?current_inputs(EskiListe);
    ?target_method(M, P);
    ?parameter_types(ParamTypes);
    ?visual_coverage(VisualCov);
    
    // Tüm başarısız denemeleri recursive olarak birleştir
    !build_failure_text(FailedAttemptsList, "", FailedAttemptsText);
    
   
    .concat("Role: Java Test Data Expert.",
            "Source Code:\n", Kod,
            "\n\n--- VISUAL COVERAGE ---\n", VisualCov,
            "\n\n Target Method: ", M,
            "\n Parameter Types: ", ParamTypes,
            "\n Target Logic Path: ", Strategy,
            "\n Target Line Number: ", TargetLine,
            "\n\n === ALL PREVIOUS FAILED ATTEMPTS ===",
            FailedAttemptsText,
            "\n\n CRITICAL ANALYSIS:",
            "\n - Analyze WHY ALL previous inputs failed to reach the target line.",
            "\n - Look for patterns in the failures - are all conditions being violated?",
            "\n - If the Target Logic contains conditions like '< 0', '> 0', '== 0', ensure your input satisfies them.",
            "\n - If it involves loop conditions (LOOP_ENTER/LOOP_SKIP), adjust values to satisfy the loop entry/exit logic.",
            "\n - If it involves branches (IF/ELSE), ensure the condition evaluates correctly.",
            "\n - Learn from previous mistakes: If multiple attempts failed with similar reasons, try a completely different approach.",
            "\n\n HINT: If the Target Logic contains 'LOOP_ENTER', generate inputs that make the loop condition TRUE (iterate at least once).",
            "\n HINT: If the Target Logic contains 'LOOP_SKIP', generate inputs that make the loop condition FALSE (bypass loop immediately).",
            "\n HINT: If the Target Logic contains '< 0' or 'negative', generate NEGATIVE numbers.",
            "\n HINT: If the Target Logic contains '> 0' or 'positive', generate POSITIVE numbers.",
            "\n HINT: If the Target Logic contains '== 0' or 'zero', generate ZERO or numbers that equal the target value.",
            "\n\n IMPORTANT PARAMETER TYPES:",
            "\n - If parameter type is 'double[]' or 'int[]': Return JSON array of arrays, e.g., [[100.5, 200.0, 150.0]]",
            "\n - If parameter type is 'boolean[]': Return JSON array of boolean arrays, e.g., [[true, false, true]]",
            "\n - If parameter type is 'String': Return JSON array with quoted strings, e.g., [\"SUMMER20\"]",
            "\n - If parameter type is a custom class (e.g., 'Employee', 'Order'): Return a JSON object with public fields, e.g., [{\"name\":\"John\",\"age\":30}]",
            "\n - To pass NULL for an object parameter (to hit '== null' path): Use [null]",
            "\n - To set a field to null inside an object (to hit 'field == null' path): Use [{\"field\":null,...}]",
            "\n\n TASK: Provide ONE SINGLE flat JSON array containing exactly ", P, " arguments.",
            "\n The array must have EXACTLY ", P, " elements matching the parameter types.",
            "\n IMPORTANT: Generate a DIFFERENT input than ALL the failed ones listed above.",
            "\n IMPORTANT: If the Target Logic requires null, pass null directly: [null] or {\"field\":null}.",
            "\n Also avoid duplicates from this list: ", EskiListe,
            "\n OUTPUT ONLY THE JSON ARRAY (e.g., [-5] or [100, 200] or [{\"field1\":\"val\",\"field2\":42}]).",
            Prompt);
    
    askChatGPT(Prompt, RawInput);
    sanitizeLLMResponse(RawInput, CleanInput);
    
    // Kontrol: Benzersiz mi?
    if (.member(CleanInput, EskiListe)) {
        .print("⚠️ Generated input already in list, falling back to standard method...");
        !generate_input(Strategy)
    } else {
        .concat([CleanInput], EskiListe, YeniListe);
        -+current_inputs(YeniListe);
        .print("🆕 New input generated with feedback: ", CleanInput);
        
        // Orchestrator'a test edilecek inputu bildir
        .send(orchestrator, tell, input_being_tested(CleanInput));
        
        // Testi çalıştır 
        runTestAndMeasureCoverage(Kod, YeniListe, Rapor, HitLines, NewVisualCov);
        -+visual_coverage(NewVisualCov);
        .print(Rapor);
        .send(orchestrator, tell, analyze_coverage_report(Kod, Rapor, HitLines))
    }.

// Yardımcı plan: Başarısız denemeleri recursive olarak string'e çevir
+!build_failure_text([], CurrentText, Result) <-
    Result = CurrentText.

+!build_failure_text([[Input, Reason]|Rest], CurrentText, Result) <-
    .concat(CurrentText, "\n   • Input: ", Input, "\n     Reason: ", Reason, NewText);
    !build_failure_text(Rest, NewText, Result).

// --- YARDIMCI PLAN: Recursive (Özyinelemeli) Retry Mantığı ---

// DURUM A: Başarılı (Girdi listede YOKSA) -> Döndür
// Analyzer - Dinamik Prompt Güncellemesi
+!fetch_unique_input(Strategy, Kod, CurrentList, TryCount, Result)
    : target_method(MName, PCount) & parameter_types(ParamTypes) & visual_coverage(VisualCov) <-


    .concat("Role: Java Test Data Expert.",
            "\n\n--- VISUAL COVERAGE ---\n", VisualCov,
            "\n\n Target Method: ", MName,
            "\n Parameter Types: ", ParamTypes,
            "\n Target Logic: ", Strategy,

            // YENİ: Döngü mantığını LLM'e öğreten kısım
            "\n HINT: If the Target Logic contains 'LOOP_ENTER', generate inputs that make the loop condition TRUE (iterate at least once).",
            "\n HINT: If the Target Logic contains 'LOOP_SKIP', generate inputs that make the loop condition FALSE (bypass loop immediately).",

            // YENİ: Koşul ifadelerini parse etme (örn: 'n < 0' -> negatif sayı dene)
            "\n HINT: If the Target Logic contains '< 0' or 'negative', generate NEGATIVE numbers.",
            "\n HINT: If the Target Logic contains '> 0' or 'positive', generate POSITIVE numbers.",
            "\n HINT: If the Target Logic contains '== 0' or 'zero', generate ZERO or numbers that equal the target value.",
            "\n HINT: If the Target Logic contains '!=' or 'not equal', avoid the specified value.",
            
            // YENİ: Karmaşık parametre tipleri için özel talimatlar
            "\n IMPORTANT PARAMETER TYPES:",
            "\n - If parameter type is 'double[]' or 'int[]': Return JSON array of arrays, e.g., [[100.5, 200.0, 150.0]]",
            "\n - If parameter type is 'boolean[]': Return JSON array of boolean arrays, e.g., [[true, false, true]]",
            "\n - If parameter type is 'String': Return JSON array with quoted strings, e.g., [\"SUMMER20\"]",
            "\n - If parameter type is 'String' and can be null: Use either [\"SUMMER20\"] or [null]",
            "\n - If parameter type is a custom class (e.g., 'Employee', 'Order'): Return a JSON object with public fields inside the array, e.g., [{\"name\":\"John\",\"age\":30,\"active\":true}]",
            "\n - To pass NULL for an object parameter (e.g., to hit 'emp == null' path): Use [null]",
            "\n - To set a field to null inside an object (e.g., to hit 'emp.department == null' path): Use [{\"department\":null,\"baseSalary\":100,\"yearsOfService\":0,\"isFullTime\":true,\"performanceScore\":3}]",
            "\n - To set a String field to empty (e.g., to hit 'isEmpty()' path): Use [{\"department\":\"\",\"baseSalary\":100,...}]",
            "\n - Mix types carefully: For method(double[], boolean[], String), return: [[100.5, 200.0], [true, false], \"SUMMER20\"]",
            // --------------------------------------------------

            "\n TASK: Provide ONE SINGLE flat JSON array containing exactly ", PCount, " arguments for a SINGLE test execution.",
            "\n IMPORTANT: The array must have EXACTLY ", PCount, " elements matching the parameter types.",
            "\n IMPORTANT: If the method expects a String, provide a valid string in double quotes.",
            "\n IMPORTANT: If the parameter is a custom object, provide a JSON object with its public field names and appropriate values.",
            "\n IMPORTANT: If the Target Logic requires null (e.g., '== null'), pass null directly: [null] for object param or {\"field\":null} for a field.",
            "\n IMPORTANT: If ", CurrentList, " already contains some inputs, ensure the new input is unique and not in this list: ", CurrentList,
            "\n If it expects an int, provide a number.",
            "\n OUTPUT ONLY THE JSON ARRAY (e.g., [\"hello\"] or [100, 1] or [{\"field1\":\"val\",\"field2\":42}]).",
            Prompt);

    askChatGPT(Prompt, RawInput);
    sanitizeLLMResponse(RawInput, CleanInput);

    // Kontrol ve Retry Mantığı (Aynı kalıyor)
    if (.member(CleanInput, CurrentList)) {
        !fetch_unique_input(Strategy, Kod, CurrentList, TryCount + 1, Result);
    } else {
        Result = CleanInput;
    }.

// DURUM B: Başarısız -> "SKIP" döndür
+!fetch_unique_input(_, _, _, TryCount, Result) : true <-
    Result = "SKIP".

// Orchestrator'dan onay gelince Generator'a devret
+coverage_complete[source(orchestrator)] : source_code(Kod) & current_inputs(TumInputlar) & successful_inputs(BasariliInputlar) <-
    .print("Orchestrator approved. Handing off to Generator for final test.");
    .length(TumInputlar, NTum);
    .length(BasariliInputlar, NBasarili);
    .print("Total inputs: ", NTum, " | Coverage-contributing (successful): ", NBasarili);
    
    // Tum aktif retry ve generation intention'larini iptal et
    .drop_all_desires;
    
    // Executor'a yalnızca başarılı inputları bildir
    .send(executor, tell, successful_inputs_info(BasariliInputlar, BasariliInputlar));
    
    // Generator'a YALNIZCA başarılı inputları gönder
    .send(generator, tell, write_final_test(Kod, BasariliInputlar)).
