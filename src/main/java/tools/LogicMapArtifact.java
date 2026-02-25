package tools;

import cartago.*;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.ast.NodeList;
import java.util.*;
import java.util.stream.Collectors;

public class LogicMapArtifact extends Artifact {
    
    // Sub-logic tracking: Değişkenlerin input parametrelerine olan bağımlılıklarını tutar
    private Map<String, List<String>> variableDependencies = new HashMap<>();
    
    // Expression tracking: Değişkenlerin tam ifadelerini tutar (input parametreleri cinsinden genişletilebilir)
    private Map<String, Expression> variableExpressions = new HashMap<>();
    
    private Set<String> inputParameters = new HashSet<>();

    @OPERATION
    public void extractLogicPaths(String sourceCode, OpFeedbackParam<Object[]> paths) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(sourceCode);
            List<String> foundPaths = new ArrayList<>();
            
            // İlk önce input parametrelerini ve değişken bağımlılıklarını topla
            extractInputParameters(cu);
            buildVariableDependencyMap(cu);
            extractInputParameters(cu);
            buildVariableDependencyMap(cu);

            // Ajanın anlayacağı şekilde hiyerarşiyi takip eden Visitor
            cu.accept(new VoidVisitorAdapter<List<String>>() {
                
                // --- 1. IF BLOKLARI (Mevcut Mantık) ---
                @Override
                public void visit(IfStmt n, List<String> currentPath) {
                    String condition = n.getCondition().toString();
                    String subLogic = buildSubLogic(condition);

                    // --- IF (THEN) DALI ---
                    List<String> thenPath = new ArrayList<>(currentPath);
                    String thenPathStr = "(" + condition + ")";
                    if (!subLogic.isEmpty()) {
                        thenPathStr += " [SUB_LOGIC: " + subLogic + "]";
                    }
                    thenPath.add(thenPathStr);
                    
                    Statement thenStmt = n.getThenStmt();
                    int thenTargetLine = getFirstLineInside(thenStmt);

                    if (thenTargetLine != -1) {
                        foundPaths.add(thenTargetLine + "|" + "PATH_TO_IF: " + String.join(" && ", thenPath));
                    }
                    
                    // Then bloğunda return var mı kontrol et
                    boolean thenHasReturn = hasReturnStatement(thenStmt);
                    
                    // Recursive: İçeriye girerken thenPath (condition=TRUE) ile devam et
                    n.getThenStmt().accept(this, thenPath);

                    // --- ELSE DALI ---
                    if (n.getElseStmt().isPresent()) {
                        List<String> elsePath = new ArrayList<>(currentPath);
                        String elsePathStr = "!(" + condition + ")";
                        if (!subLogic.isEmpty()) {
                            elsePathStr += " [SUB_LOGIC: " + subLogic + "]";
                        }
                        elsePath.add(elsePathStr);
                        Statement elseStmt = n.getElseStmt().get();
                        
                        int elseTargetLine = getFirstLineInside(elseStmt);
                        
                        if (!(elseStmt instanceof IfStmt) && elseTargetLine != -1) {
                            foundPaths.add(elseTargetLine + "|" + "PATH_TO_ELSE: " + String.join(" && ", elsePath));
                        }
                        // Recursive: Else içine girerken elsePath (condition=FALSE) ile devam et
                        elseStmt.accept(this, elsePath);
                    }
                    
                    // ÖNEMLİ: Eğer then bloğunda return varsa ve else bloğu yoksa,
                    // if'ten sonra gelen kodlar sadece condition=FALSE olduğunda çalışır.
                    // Bu durumda, currentPath'e negated condition'ı ekliyoruz.
                    if (thenHasReturn && !n.getElseStmt().isPresent()) {
                        String negatedCondition = "!(" + condition + ")";
                        if (!subLogic.isEmpty()) {
                            negatedCondition += " [SUB_LOGIC: " + subLogic + "]";
                        }
                        currentPath.add(negatedCondition);
                    }
                }

                // --- 2. WHILE DÖNGÜLERİ (YENİ - İKİYE BÖLME STRATEJİSİ) ---
                @Override
                public void visit(WhileStmt n, List<String> currentPath) {
                    String condition = n.getCondition().toString();
                    String subLogic = buildSubLogic(condition);

                    // YOL A: Döngüye Girme (Condition == TRUE)
                    List<String> enterPath = new ArrayList<>(currentPath);
                    String enterPathStr = "(WHILE_ENTER: " + condition + ")";
                    if (!subLogic.isEmpty()) {
                        enterPathStr += " [SUB_LOGIC: " + subLogic + "]";
                    }
                    enterPath.add(enterPathStr);
                    
                    int insideLine = getFirstLineInside(n.getBody());
                    if (insideLine != -1) {
                        foundPaths.add(insideLine + "|" + "TARGET_LOOP_ENTER: " + String.join(" && ", enterPath));
                    }

                    // YOL B: Döngüyü Pas Geçme (Condition == FALSE)
                    List<String> skipPath = new ArrayList<>(currentPath);
                    String skipPathStr = "!(WHILE_ENTER: " + condition + ")";
                    if (!subLogic.isEmpty()) {
                        skipPathStr += " [SUB_LOGIC: " + subLogic + "]";
                    }
                    skipPath.add(skipPathStr);
                    
                    // Hedef satır olarak döngünün başladığı satırı veriyoruz (Kapsama raporunda burası işaretlenmeli)
                    foundPaths.add(n.getBegin().get().line + "|" + "TARGET_LOOP_SKIP: " + String.join(" && ", skipPath));

                    // Recursive: Döngünün İÇİNİ analiz ederken "enterPath" kullanıyoruz.
                    // Çünkü içerdeki kodlara ulaşmak için döngüye girmiş olmamız şart.
                    super.visit(n, enterPath); 
                }

                // --- 3. FOR DÖNGÜLERİ (YENİ - İKİYE BÖLME STRATEJİSİ) ---
                @Override
                public void visit(ForStmt n, List<String> currentPath) {
                    // For döngüsünde koşul kısmı opsiyonel olabilir (örn: for(;;)). Yoksa "true" varsay.
                    String condition = n.getCompare().isPresent() ? n.getCompare().get().toString() : "true";
                    String subLogic = buildSubLogic(condition);

                    // YOL A: Döngüye Girme
                    List<String> enterPath = new ArrayList<>(currentPath);
                    String enterPathStr = "(FOR_ENTER: " + condition + ")";
                    if (!subLogic.isEmpty()) {
                        enterPathStr += " [SUB_LOGIC: " + subLogic + "]";
                    }
                    enterPath.add(enterPathStr);
                    
                    int insideLine = getFirstLineInside(n.getBody());
                    if (insideLine != -1) {
                        foundPaths.add(insideLine + "|" + "TARGET_LOOP_ENTER: " + String.join(" && ", enterPath));
                    }

                    // YOL B: Döngüyü Pas Geçme
                    List<String> skipPath = new ArrayList<>(currentPath);
                    String skipPathStr = "!(FOR_ENTER: " + condition + ")";
                    if (!subLogic.isEmpty()) {
                        skipPathStr += " [SUB_LOGIC: " + subLogic + "]";
                    }
                    skipPath.add(skipPathStr);
                    
                    foundPaths.add(n.getBegin().get().line + "|" + "TARGET_LOOP_SKIP: " + String.join(" && ", skipPath));

                    // Recursive: İçerisi için enterPath kullan
                    super.visit(n, enterPath);
                }

                // --- YARDIMCI METOD (AYNI KALDI) ---
                private int getFirstLineInside(Statement stmt) {
                    if (stmt.isBlockStmt()) {
                        var statements = stmt.asBlockStmt().getStatements();
                        if (!statements.isEmpty()) {
                            return statements.get(0).getBegin().get().line;
                        }
                    } else {
                        return stmt.getBegin().get().line;
                    }
                    return -1;
                }
                
                // --- YENİ YARDIMCI METOD: RETURN STATEMENT KONTROLÜ ---
                private boolean hasReturnStatement(Statement stmt) {
                    // Statement içinde herhangi bir return ifadesi var mı kontrol et
                    return !stmt.findAll(ReturnStmt.class).isEmpty();
                }
            }, new ArrayList<>()); 

            paths.set(foundPaths.toArray());
        } catch (Exception e) {
            failed("Logic extraction failed: " + e.getMessage());
        }
    }
    
    /**
     * Method parametrelerini (input parametreleri) çıkarır
     */
    private void extractInputParameters(CompilationUnit cu) {
        inputParameters.clear();
        cu.findAll(MethodDeclaration.class).forEach(method -> {
            for (Parameter param : method.getParameters()) {
                inputParameters.add(param.getNameAsString());
            }
        });
    }
    
    /**
     * Değişken atamaları ve bunların input parametrelerine olan bağımlılıklarını analiz eder.
     * Örnek: sum = a + b -> sum, [a, b]'ye bağımlıdır
     */
    private void buildVariableDependencyMap(CompilationUnit cu) {
        variableDependencies.clear();
        variableExpressions.clear();
        
        cu.accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(ExpressionStmt n, Void arg) {
                Expression expr = n.getExpression();
                
                // Atama işlemlerini bul: x = ... veya x += ... veya arr[0] = ...
                if (expr.isAssignExpr()) {
                    AssignExpr assignExpr = expr.asAssignExpr();
                    Expression targetExpr = assignExpr.getTarget();
                    String target = targetExpr.toString();
                    Expression value = assignExpr.getValue();
                    AssignExpr.Operator operator = assignExpr.getOperator();
                    
                    // Compound assignment'ları normalize et (+=, -=, *=, /=, etc.)
                    Expression normalizedValue = value;
                    if (operator != AssignExpr.Operator.ASSIGN) {
                        // counter += n  ->  counter + n
                        normalizedValue = expandCompoundAssignment(target, operator, value);
                    }
                    
                    // Array element assignment mı kontrol et: arr[0] = value
                    if (targetExpr.isArrayAccessExpr()) {
                        // BACKWARD TRACKING: Sağ taraftaki BAŞKA array access'leri expand et
                        // arr[1] = arr[0] * 2 durumunda arr[0]'ın EN SON değerini kullan
                        // ama arr[0] = arr[0] + arr[1] durumunda sağ taraftaki arr[0]'ı EXPAND ETME!
                        Expression fullyExpandedValue = normalizedValue.clone();
                        expandArrayAccessesInExpression(fullyExpandedValue, target);
                        
                        // Array element'i track et: "arr[0]" -> expanded value
                        variableDependencies.put(target, new ArrayList<>(extractVariablesFromExpression(fullyExpandedValue)));
                        variableExpressions.put(target, fullyExpandedValue);
                    } else if (targetExpr.isFieldAccessExpr()) {
                        // Field access assignment: p.age = value veya emp.address.zipCode = value
                        // BACKWARD TRACKING: Sağ taraftaki field access'leri expand et
                        Expression fullyExpandedValue = normalizedValue.clone();
                        expandFieldAccessesInExpression(fullyExpandedValue, target);
                        
                        // Field'i track et: "p.age" -> expanded value
                        variableDependencies.put(target, new ArrayList<>(extractVariablesFromExpression(fullyExpandedValue)));
                        variableExpressions.put(target, fullyExpandedValue);
                    } else {
                        // Normal variable assignment: x = value
                        // Sağ taraftaki değişkenleri topla
                        Set<String> dependencies = extractVariablesFromExpression(normalizedValue);
                        
                        if (!dependencies.isEmpty()) {
                            variableDependencies.put(target, new ArrayList<>(dependencies));
                        }
                        
                        // Expression'ı da sakla (daha sonra substitution için)
                        variableExpressions.put(target, normalizedValue.clone());
                    }
                }
                
                // Variable declaration with initializer: int sum = a + b
                if (expr.isVariableDeclarationExpr()) {
                    VariableDeclarationExpr varDecl = expr.asVariableDeclarationExpr();
                    varDecl.getVariables().forEach(variable -> {
                        if (variable.getInitializer().isPresent()) {
                            String varName = variable.getNameAsString();
                            Expression initializer = variable.getInitializer().get();
                            
                            // Unary expressions (++x, x++, --x, x--) normalize et
                            Expression normalizedInit = normalizeUnaryExpression(initializer);
                            
                            Set<String> dependencies = extractVariablesFromExpression(normalizedInit);
                            
                            if (!dependencies.isEmpty()) {
                                variableDependencies.put(varName, new ArrayList<>(dependencies));
                            }
                            
                            // Expression'ı da sakla
                            variableExpressions.put(varName, normalizedInit.clone());
                        }
                    });
                }
                
                super.visit(n, arg);
            }
        }, null);
    }
    
    /**
     * Compound assignment operatörlerini normal binary expression'a dönüştürür.
     * Örnek: counter += n -> counter + n
     *        counter *= 2 -> counter * 2
     */
    private Expression expandCompoundAssignment(String target, AssignExpr.Operator operator, Expression value) {
        // Eğer target zaten bir expression ise (önceki değeri varsa), onu kullan
        Expression leftSide = variableExpressions.containsKey(target) 
            ? variableExpressions.get(target).clone() 
            : new NameExpr(target);
        
        BinaryExpr.Operator binaryOp;
        switch (operator) {
            case PLUS:           // +=
                binaryOp = BinaryExpr.Operator.PLUS;
                break;
            case MINUS:          // -=
                binaryOp = BinaryExpr.Operator.MINUS;
                break;
            case MULTIPLY:       // *=
                binaryOp = BinaryExpr.Operator.MULTIPLY;
                break;
            case DIVIDE:         // /=
                binaryOp = BinaryExpr.Operator.DIVIDE;
                break;
            case REMAINDER:      // %=
                binaryOp = BinaryExpr.Operator.REMAINDER;
                break;
            case BINARY_AND:     // &=
                binaryOp = BinaryExpr.Operator.BINARY_AND;
                break;
            case BINARY_OR:      // |=
                binaryOp = BinaryExpr.Operator.BINARY_OR;
                break;
            case XOR:            // ^=
                binaryOp = BinaryExpr.Operator.XOR;
                break;
            case LEFT_SHIFT:     // <<=
                binaryOp = BinaryExpr.Operator.LEFT_SHIFT;
                break;
            case SIGNED_RIGHT_SHIFT:   // >>=
                binaryOp = BinaryExpr.Operator.SIGNED_RIGHT_SHIFT;
                break;
            case UNSIGNED_RIGHT_SHIFT: // >>>=
                binaryOp = BinaryExpr.Operator.UNSIGNED_RIGHT_SHIFT;
                break;
            default:
                // Normal assignment, bu duruma düşmemeli
                return value;
        }
        
        // Parantez içine al eğer leftSide karmaşıksa
        if (leftSide instanceof BinaryExpr) {
            leftSide = new EnclosedExpr(leftSide);
        }
        
        return new BinaryExpr(leftSide, value.clone(), binaryOp);
    }
    
    /**
     * Unary expression'ları (++, --) normalize eder.
     * Örnek: ++x -> x + 1
     *        x++ -> x + 1 (side-effect için not eklenebilir)
     *        --x -> x - 1
     */
    private Expression normalizeUnaryExpression(Expression expr) {
        if (expr.isUnaryExpr()) {
            UnaryExpr unaryExpr = expr.asUnaryExpr();
            UnaryExpr.Operator op = unaryExpr.getOperator();
            
            if (op == UnaryExpr.Operator.PREFIX_INCREMENT || op == UnaryExpr.Operator.POSTFIX_INCREMENT) {
                // ++x veya x++ -> x + 1
                Expression operand = unaryExpr.getExpression();
                Expression expandedOperand = variableExpressions.containsKey(operand.toString())
                    ? variableExpressions.get(operand.toString()).clone()
                    : operand.clone();
                    
                if (expandedOperand instanceof BinaryExpr) {
                    expandedOperand = new EnclosedExpr(expandedOperand);
                }
                
                return new BinaryExpr(expandedOperand, new IntegerLiteralExpr("1"), BinaryExpr.Operator.PLUS);
            } else if (op == UnaryExpr.Operator.PREFIX_DECREMENT || op == UnaryExpr.Operator.POSTFIX_DECREMENT) {
                // --x veya x-- -> x - 1
                Expression operand = unaryExpr.getExpression();
                Expression expandedOperand = variableExpressions.containsKey(operand.toString())
                    ? variableExpressions.get(operand.toString()).clone()
                    : operand.clone();
                    
                if (expandedOperand instanceof BinaryExpr) {
                    expandedOperand = new EnclosedExpr(expandedOperand);
                }
                
                return new BinaryExpr(expandedOperand, new IntegerLiteralExpr("1"), BinaryExpr.Operator.MINUS);
            }
        }
        
        return expr;
    }
    
    /**
     * Bir expressiondaki tüm değişkenleri çıkarır
     */
    private Set<String> extractVariablesFromExpression(Expression expr) {
        Set<String> variables = new HashSet<>();
        
        expr.findAll(NameExpr.class).forEach(nameExpr -> {
            variables.add(nameExpr.getNameAsString());
        });
        
        return variables;
    }
    
    /**
     * Expression içindeki array access'leri expand eder (backward tracking için).
     * arr[1] = arr[0] * 2 durumunda, sağ taraftaki arr[0]'ın EN SON değerini TAMAMEN expand eder.
     * 
     * BACKTRACKING YAKLAŞIMI: Her array element referansı bir "fonksiyon çağrısı" gibi düşünülür:
     * result = arr[0] + arr[1]
     * → arr[0] çağrıldı: arr[0] = arr[0] + arr[1] → arr[0] = {a,b}[0] + {a,b}[1]
     * → arr[1] çağrıldı: arr[1] = arr[0] * 2 → arr[0] çağrıldı: ... → arr[1] = ({a,b}[0] + {a,b}[1]) * 2
     * → result = ({a,b}[0] + {a,b}[1]) + (({a,b}[0] + {a,b}[1]) * 2)
     * 
     * EĞER array element için explicit assignment yoksa (arr[0] map'te yoksa),
     * array variable'ı kullanır: arr → {a,b} → {a,b}[0]
     */
    private void expandArrayAccessesInExpression(Expression expr, String targetVariable) {
        List<ArrayAccessExpr> arrayAccesses = new ArrayList<>(expr.findAll(ArrayAccessExpr.class));
        for (ArrayAccessExpr arrayAccess : arrayAccesses) {
            String arrayAccessStr = arrayAccess.toString();
            
            // Target variable ile aynı ise skip et (arr[0] = arr[0] + ... durumu için)
            if (arrayAccessStr.equals(targetVariable)) {
                continue;
            }
            
            //Bu array access için kayıtlı bir değer var mı?
            Expression arrayValue = variableExpressions.get(arrayAccessStr);
            Expression fullyExpanded = null;
            
            if (arrayValue != null) {
                // CASE 1: Array element için explicit assignment var (örn: arr[0] = ...)
                // ÖNEMLİ: FULL EXPANSION! Input parameters'a kadar expand et
                // arr[0] → arr[0] + arr[1] → {a,b}[0] + {a,b}[1]
                fullyExpanded = expandToInputs(arrayAccessStr, new HashSet<>());
            } else {
                // CASE 2: Array element için explicit assignment YOK
                // Array variable'ı kullan: arr[0] → {a,b}[0]
                Expression arrayNameExpr = arrayAccess.getName();
                String arrayName = arrayNameExpr.toString();
                Expression baseArrayValue = variableExpressions.get(arrayName);
                
                if (baseArrayValue != null) {
                    // Array variable var: arr → {a,b}
                    // Yeni expression: {a,b}[0]
                    fullyExpanded = new ArrayAccessExpr(
                        baseArrayValue.clone(),
                        arrayAccess.getIndex().clone()
                    );
                }
            }
            
            if (fullyExpanded != null) {
                // Binary expression ise ve parent da binary ise, parantezle
                if (fullyExpanded instanceof BinaryExpr && 
                    arrayAccess.getParentNode().isPresent() && 
                    arrayAccess.getParentNode().get() instanceof BinaryExpr) {
                    fullyExpanded = new EnclosedExpr(fullyExpanded);
                }
                
                arrayAccess.replace(fullyExpanded);
            }
        }
    }
    
    /**
     * Expression içindeki field access'leri expand eder (backward tracking için).
     * p.height = b; sum = p.age + p.height; durumunda, sağ taraftaki p.height'ın EN SON değerini expand eder.
     * 
     * BACKTRACKING YAKLAŞIMI: Her field access referansı bir "fonksiyon çağrısı" gibi düşünülür:
     * sum = p.age + p.height
     * → p.age çağrıldı: p.age = a → sum = a + p.height
     * → p.height çağrıldı: p.height = b → sum = a + b
     * 
     * EĞER field için explicit assignment yoksa (p.age map'te yoksa),
     * object variable'ı kullanır: p → new Person() → new Person().age
     */
    private void expandFieldAccessesInExpression(Expression expr, String targetVariable) {
        List<FieldAccessExpr> fieldAccesses = new ArrayList<>(expr.findAll(FieldAccessExpr.class));
        for (FieldAccessExpr fieldAccess : fieldAccesses) {
            String fieldAccessStr = fieldAccess.toString();
            
            // Target variable ile aynı ise skip et (p.age = p.age + ... durumu için)
            if (fieldAccessStr.equals(targetVariable)) {
                continue;
            }
            
            // Bu field access için kayıtlı bir değer var mı?
            Expression fieldValue = variableExpressions.get(fieldAccessStr);
            Expression fullyExpanded = null;
            
            if (fieldValue != null) {
                // CASE 1: Field için explicit assignment var (örn: p.age = a)
                // ÖNEMLİ: FULL EXPANSION! Input parameters'a kadar expand et
                // p.age → a
                fullyExpanded = expandToInputs(fieldAccessStr, new HashSet<>());
            } else {
                // CASE 2: Field için explicit assignment YOK
                // Object variable'ı kullan: p.age → new Person().age
                Expression scope = fieldAccess.getScope();
                String scopeName = scope.toString();
                Expression baseObjectValue = variableExpressions.get(scopeName);
                
                if (baseObjectValue != null) {
                    // Object variable var: p → new Person()
                    // Yeni expression: new Person().age
                    fullyExpanded = new FieldAccessExpr(
                        baseObjectValue.clone(),
                        fieldAccess.getNameAsString()
                    );
                }
            }
            
            if (fullyExpanded != null) {
                // Binary expression ise ve parent da binary ise, parantezle
                if (fullyExpanded instanceof BinaryExpr && 
                    fieldAccess.getParentNode().isPresent() && 
                    fieldAccess.getParentNode().get() instanceof BinaryExpr) {
                    fullyExpanded = new EnclosedExpr(fullyExpanded);
                }
                
                fieldAccess.replace(fullyExpanded);
            }
        }
    }
    
    /**
     * Bir koşul için sub-logic bilgisi oluşturur.
     * Koşuldaki değişkenlerin input parametreleri cinsinden tam ifadesini oluşturur.
     * 
     * Örnek:
     * Condition: diff > 0
     * Input params: a, b
     * Variable definitions: sum = a + b, product = a * b, diff = sum - product
     * Output: "diff = (a + b) - (a * b)"
     */
    private String buildSubLogic(String condition) {
        Expression conditionExpr = StaticJavaParser.parseExpression(condition);
        
        Set<String> conditionVars = extractVariablesFromExpression(conditionExpr);
        
        // Field access'leri de ekle (p.age, box.area gibi)
        List<FieldAccessExpr> fieldAccesses = conditionExpr.findAll(FieldAccessExpr.class);
        for (FieldAccessExpr fieldAccess : fieldAccesses) {
            conditionVars.add(fieldAccess.toString());
        }
        
        // Array access'leri de ekle (arr[0] gibi)
        List<ArrayAccessExpr> arrayAccesses = conditionExpr.findAll(ArrayAccessExpr.class);
        for (ArrayAccessExpr arrayAccess : arrayAccesses) {
            conditionVars.add(arrayAccess.toString());
        }
        
        List<String> subLogicParts = new ArrayList<>();
        
        for (String var : conditionVars) {
            // Eğer değişken direkt input parametresi ise, sub-logic gerekmez
            if (inputParameters.contains(var)) {
                continue;
            }
            
            // Değişkenin expression'ını bul ve input parametrelerine kadar genişlet
            Expression expandedExpr = expandToInputs(var, new HashSet<>());
            if (expandedExpr != null) {
                subLogicParts.add(var + " = " + expandedExpr.toString());
            }
        }
        
        return String.join("; ", subLogicParts);
    }
    
    /**
     * Bir değişkenin expression'ını input parametreleri cinsinden recursive olarak genişletir.
     * Örnek: diff -> (sum - product) -> ((a + b) - (a * b))
     * Ayrıca array access'leri de handle eder: arr[0] -> değeri
     */
    private Expression expandToInputs(String variable, Set<String> visited) {
        // Circular dependency önleme
        if (visited.contains(variable)) {
            return new NameExpr(variable);
        }
        
        visited.add(variable);
        
        // Eğer bu bir input parametresi ise, olduğu gibi dön
        if (inputParameters.contains(variable)) {
            return new NameExpr(variable);
        }
        
        // Değişkenin expression'ını al
        Expression expr = variableExpressions.get(variable);
        if (expr == null) {
            // Expression bulunamadı, değişken adını dön
            return new NameExpr(variable);
        }
        
        // ÖNEMLİ: Eğer expression'ın kendisi bir array access ise (örn: first = arr[0])
        // ve bu array access için ayrı bir assignment var ise (örn: arr[0] = value),
        // o array access'in değerini kullan (backward tracking!)
        String exprStr = expr.toString();
        if (expr instanceof ArrayAccessExpr) {
            Expression directArrayValue = variableExpressions.get(exprStr);
            if (directArrayValue != null && !visited.contains(exprStr)) {
                // arr[0]'ın direkt değerini bulduk, onu expand et
                return expandToInputs(exprStr, visited);
            }
        }
        
        // ÖNEMLİ: Eğer expression'ın kendisi bir field access ise (örn: age = p.age)
        // ve bu field access için ayrı bir assignment var ise (örn: p.age = a),
        // o field access'in değerini kullan (backward tracking!)
        if (expr instanceof FieldAccessExpr) {
            Expression directFieldValue = variableExpressions.get(exprStr);
            if (directFieldValue != null && !visited.contains(exprStr)) {
                // p.age'ın direkt değerini bulduk, onu expand et
                return expandToInputs(exprStr, visited);
            }
        }
        
        // Expression'ı clone et (orijinali değiştirmemek için)
        Expression expanded = expr.clone();
        
        // ÖNCELİKLE Array access expression'ları substitute et (arr[0] gibi)
        // BEFORE: name expressions - çünkü arr'ı substitute edersek arr[0] kaybolur!
        List<ArrayAccessExpr> arrayAccesses = new ArrayList<>(expanded.findAll(ArrayAccessExpr.class));
        for (ArrayAccessExpr arrayAccess : arrayAccesses) {
            String arrayAccessStr = arrayAccess.toString();
            
            // Bu array access için kayıtlı bir değer var mı kontrol et
            Expression arrayReplacement = variableExpressions.get(arrayAccessStr);
            if (arrayReplacement != null && !visited.contains(arrayAccessStr)) {
                // Recursive olarak genişlet (yeni visited set ile)
                Set<String> newVisited = new HashSet<>(visited);
                newVisited.add(arrayAccessStr);
                Expression expandedArrayValue = expandToInputs(arrayAccessStr, newVisited);
                
                // Parantez gerekiyor mu kontrol et
                if (expandedArrayValue instanceof BinaryExpr && 
                    arrayAccess.getParentNode().isPresent() && 
                    arrayAccess.getParentNode().get() instanceof BinaryExpr) {
                    expandedArrayValue = new EnclosedExpr(expandedArrayValue);
                }
                
                // Array access'i genişletilmiş değerle değiştir
                arrayAccess.replace(expandedArrayValue);
            }
        }
        
        // İKİNCİ: Field access expression'ları substitute et (p.age gibi)
        // BEFORE: name expressions - çünkü p'yi substitute edersek p.age kaybolur!
        List<FieldAccessExpr> fieldAccesses = new ArrayList<>(expanded.findAll(FieldAccessExpr.class));
        for (FieldAccessExpr fieldAccess : fieldAccesses) {
            String fieldAccessStr = fieldAccess.toString();
            
            // Bu field access için kayıtlı bir değer var mı kontrol et
            Expression fieldReplacement = variableExpressions.get(fieldAccessStr);
            if (fieldReplacement != null && !visited.contains(fieldAccessStr)) {
                // Recursive olarak genişlet (yeni visited set ile)
                Set<String> newVisited = new HashSet<>(visited);
                newVisited.add(fieldAccessStr);
                Expression expandedFieldValue = expandToInputs(fieldAccessStr, newVisited);
                
                // Parantez gerekiyor mu kontrol et
                if (expandedFieldValue instanceof BinaryExpr && 
                    fieldAccess.getParentNode().isPresent() && 
                    fieldAccess.getParentNode().get() instanceof BinaryExpr) {
                    expandedFieldValue = new EnclosedExpr(expandedFieldValue);
                }
                
                // Field access'i genişletilmiş değerle değiştir
                fieldAccess.replace(expandedFieldValue);
            }
        }
        
        // SONRA: Expression içindeki normal değişkenleri (NameExpr) substitute et
        List<NameExpr> nameExprs = expanded.findAll(NameExpr.class);
        for (NameExpr nameExpr : nameExprs) {
            String varName = nameExpr.getNameAsString();
            
            // Bu değişkeni genişlet
            Expression replacement = expandToInputs(varName, new HashSet<>(visited));
            
            // Eğer replacement tek bir değişken değilse ve bir binary expression'ın parçası ise,
            // parantez ekle
            if (shouldWrapInParentheses(replacement, nameExpr)) {
                replacement = new EnclosedExpr(replacement);
            }
            
            // Replace et
            nameExpr.replace(replacement);
        }
        
        return expanded;
    }
    
    /**
     * Bir expression'ın parantez içine alınıp alınmaması gerektiğini belirler.
     */
    private boolean shouldWrapInParentheses(Expression replacement, NameExpr original) {
        // Eğer replacement bir binary/unary expression ise ve parent bir binary expression ise
        if (replacement instanceof BinaryExpr || replacement instanceof UnaryExpr) {
            if (original.getParentNode().isPresent() && 
                original.getParentNode().get() instanceof BinaryExpr) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Bir değişkenin input parametrelerine olan bağımlılığını recursive olarak izler.
     * Circular dependency'leri önlemek için visited set kullanır.
     * 
     * @deprecated Bu metod artık eski bağımlılık sistemi için kullanılıyor. 
     * Yeni sistemde expandToInputs kullanılıyor.
     */
    private Set<String> traceToInputs(String variable, Set<String> visited) {
        Set<String> inputs = new HashSet<>();
        
        if (visited.contains(variable)) {
            return inputs; // Circular dependency
        }
        
        visited.add(variable);
        
        // Eğer bu değişken direkt input parametresi ise
        if (inputParameters.contains(variable)) {
            inputs.add(variable);
            return inputs;
        }
        
        // Değişkenin bağımlılıklarını kontrol et
        List<String> deps = variableDependencies.get(variable);
        if (deps != null) {
            for (String dep : deps) {
                inputs.addAll(traceToInputs(dep, visited));
            }
        }
        
        return inputs;
    }
}