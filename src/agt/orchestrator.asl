/* Orchestrator - Deterministik Stratejik Karar Verici */
/* Reflexion Butce Yonetimi dahil */

path_attempts(0).
last_tried_path("none").
max_reflection_steps(100).      // Generator icin Reflexion butcesi
reflection_budget_used(0).    // Toplam kullanilan reflexion adimi
current_target_path("none").  // Şu anda hedeflenen path
last_input_tried("none").      // Son denenen input
miss_count(0).                 // Aynı hedef için MISS sayısı
failed_attempts([]).           // Başarısız denemeler listesi: [[Input1, Reason1], [Input2, Reason2], ...]

!start.

+!start : true <-
    .print("Orchestrator ready. Deterministic analysis mode active.");
    ?max_reflection_steps(MaxSteps);
    .print("Reflection budget: ", MaxSteps, " steps (max_reflection_steps)");
    makeArtifact("json_helper", "tools.JsonTool", [], JsonId);
    makeArtifact("condition_evaluator", "tools.ConditionEvaluatorArtifact", [], EvalId);
    focus(JsonId);
    focus(EvalId);
    !connect_to_llm.

+!connect_to_llm : true <-
    lookupArtifact("llm_connector", ArtId);
    focus(ArtId);
    .print("Orchestrator: LLM connection established.").

-!connect_to_llm : true <- .wait(500); !connect_to_llm.

// 1. Haritayı Kaydet (Artifact yardımıyla güvenli parçaşama)
+logic_map(PathListesi)[source(analyzer)] : true <-
    for ( .member(Entry, PathListesi) ) {
        parseMapEntry(Entry, LineNum, PathStr); // Java tarafında hatasız ayrıştırma
        +logic_path(LineNum, PathStr);
    };
    .count(logic_path(_, _), Total);
    +total_logic_paths(Total);
    .print("Logic map saved. Total paths: ", Total).

// 2. Rapor Analizi - INITIAL_EXPLORATION (Henüz hedef yok)
+analyze_coverage_report(Kod, RealReport, HitLines)[source(analyzer)] : 
    total_logic_paths(Total) & current_target_path("none") <-
    .print("🔎 Initial exploration test completed.");
    
    // Kapsanan yolları kaydet
    for ( logic_path(L, S) ) {
        if (.member(L, HitLines) & not covered_path(S)) {
            +covered_path(S);
            .print("✅ Covered in initial test: ", S)
        }
    };
        // İlk input herhangi bir line kapsamışsa başarılı say
    ?last_input_tried(InitInput);
    if (.list(HitLines) & not (HitLines == [])) {
        .send(analyzer, tell, good_input(InitInput))
    };
        .count(covered_path(_), C);
    .print("Status -> Covered: ", C, " | Total: ", Total);
    
    // Yeni hedef seç
    !select_next_target(Kod, RealReport).

// 2b. Rapor Analizi - HEDEFLİ (Geliştirilmiş: MISS Algılama ve Geri Bildirim)
+analyze_coverage_report(Kod, RealReport, HitLines)[source(analyzer)] : 
    total_logic_paths(Total) & current_target_path(CurrentTarget) & CurrentTarget \== "none" <-
    ?last_input_tried(LastInput);
    .print("🔍 DEBUG: Report received. CurrentTarget: ", CurrentTarget);
    
    // Hedeflenen path'in line numarasını bul
    ?logic_path(TargetLine, CurrentTarget);
    .print("🔍 DEBUG: TargetLine found: ", TargetLine);
    
    // HIT mi MISS mi kontrol et
    if (.member(TargetLine, HitLines)) {
        // HIT: Hedef satıra ulaşıldı
        .print("✅ HIT: Target line ", TargetLine, " covered! Path: ", CurrentTarget);
        +covered_path(CurrentTarget);
        -+miss_count(0);  // MISS sayacını sıfırla
        -+path_attempts(0); // Başarılı olduğu için attempt sayacını sıfırla
        -+failed_attempts([]); // Başarısız denemeler listesini sıfırla
        // Bu input kapsam artırdı -> başarılı listeye ekle
        .send(analyzer, tell, good_input(LastInput))
    } else {
        // MISS: Hedef satıra ulaşılamadı
        ?miss_count(MissCount);
        NewMissCount = MissCount + 1;
        -+miss_count(NewMissCount);
        
        .print("❌ MISS (", NewMissCount, "/5): Target line ", TargetLine, " not reached. Input: ", LastInput);
        
        if (NewMissCount < 5) {
            // Koşulları evaluate et ve gerçek sebep bul
            .concat(TargetLine, "|", CurrentTarget, FullPathStr);
            evaluateConditions(FullPathStr, LastInput, RealReason);
            
            // Başarısız denemeyi listeye ekle
            ?failed_attempts(OldAttempts);
            .concat([[LastInput, RealReason]], OldAttempts, NewAttempts);
            -+failed_attempts(NewAttempts);
            
            // Global reflection bütçesini kontrol et
            ?reflection_budget_used(BudgetUsed);
            ?max_reflection_steps(MaxSteps);
            .print("💡 Reflection Budget: ", BudgetUsed, "/", MaxSteps, " used.");
            
            if (BudgetUsed >= MaxSteps) {
                // Bütçe tükendi, path'i engelle
                .print("⛔ Reflection budget exhausted (", MaxSteps, " steps). Blocking: ", CurrentTarget);
                +blocked_path(CurrentTarget);
                -+miss_count(0);
                -+path_attempts(0);
                -+failed_attempts([]);
                !select_next_target(Kod, RealReport)
            } else {
                // Bütçeden 1 adım harca ve retry gönder
                NewBudget = BudgetUsed + 1;
                -+reflection_budget_used(NewBudget);
                .print("🔍 Reason: ", RealReason);
                .print("Sending feedback to Analyzer (Total ", NewMissCount, " failed attempts | Budget: ", NewBudget, "/", MaxSteps, ")...");
                .send(analyzer, achieve, retry_with_feedback(CurrentTarget, NewAttempts, TargetLine))
            }
        } else {
            // Miss limiti aşıldı, path'i engelle
            .print("!!! BLOCKING: ", CurrentTarget, " reached miss limit. Considered unreachable.");
            +blocked_path(CurrentTarget);
            -+miss_count(0);
            -+path_attempts(0);
            -+failed_attempts([]); // Başarısız denemeler listesini sıfırla
            !select_next_target(Kod, RealReport)
        }
    };
    
    // Diğer yolları da kontrol et (ek kazanımlar için)
    // HitLines'ın liste olduğunu doğrula
    .count(covered_path(_), CoveredBefore);
    if (.list(HitLines)) {
        for ( logic_path(L, S) ) {
            if (.member(L, HitLines) & not covered_path(S) & S \== CurrentTarget) {
                +covered_path(S);
                .print("🎁 Bonus: Additional path covered: ", S)
            }
        }
    };
    // MISS ama yeni bonus yol kazanıldıysa, bu input da faydalıydı
    .count(covered_path(_), CoveredAfter);
    if (not .member(TargetLine, HitLines) & CoveredAfter > CoveredBefore) {
        .send(analyzer, tell, good_input(LastInput))
    };
    
    .count(blocked_path(_), B);
    .count(covered_path(_), C);
    .print("Status -> Covered: ", C, " | Blocked: ", B, " | Total: ", Total);

    if (C + B >= Total) {
        .print(">>> ANALYSIS COMPLETE. <<<");
        .send(analyzer, tell, coverage_complete)
    } elif (NewMissCount == 0 | not .ground(NewMissCount)) {
        // HIT durumunda veya değişken set edilmemişse yeni hedef seç
        !select_next_target(Kod, RealReport)
    }.


// Analyzer'dan gelen input bilgisini kaydet
+input_being_tested(Input)[source(analyzer)] : true <-
    -+last_input_tried(Input);
    .print("📝 Input to be tested recorded: ", Input).

// 4. Hedef Belirleme (Rastgele Seçim)
+!select_next_target(Kod, Rapor) : true <-
    // Kapsanmamış ve engellenmemiş yolları bul
    .findall(Path, ( logic_path(_, Path) & not covered_path(Path) & not blocked_path(Path) ), AvailablePaths);

    .length(AvailablePaths, L);

    if (L == 0) {
        .print("No paths remaining.");
        .send(analyzer, tell, coverage_complete)
    } else {
        // --- Rastgele İndeks Seçimi ---
        // math.random * L -> 0 ile L arası double, floor -> 0..L-1
        Index = math.floor(math.random * L);
        .nth(Index, AvailablePaths, SelectedPath);

        // Yeni hedef belirlendi, tracking bilgilerini güncelle
        -+current_target_path(SelectedPath);
        -+last_tried_path(SelectedPath);
        -+last_input_tried("none");
        -+miss_count(0);
        -+path_attempts(1);
        -+failed_attempts([]); // Yeni hedef için başarısız denemeler listesini sıfırla

        .print("🎯 New Target (Randomly Selected): ", SelectedPath);
        .send(analyzer, achieve, achieve_path(SelectedPath))
    }.
