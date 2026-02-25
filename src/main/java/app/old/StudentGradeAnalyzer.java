package app;

/**
 * Öğrenci Akademik Performans Analiz Sistemi
 * 
 * Tek bir metot ile kapsamlı öğrenci değerlendirmesi yapan sınıf.
 * Çoklu array işlemleri ve karmaşık hesaplama mantığı içerir.
 * 
 * Değerlendirme Kriterleri:
 * - Ders notları (0-100 arası)
 * - Devamsızlık oranı
 * - Ödev tamamlama oranı
 * - Proje notları
 * - Sınav ağırlıkları
 * - Bonus puanlar
 * - Disiplin cezaları
 */
public class StudentGradeAnalyzer {
    
    /**
     * Öğrenci final notunu ve akademik durumunu hesaplar
     * 
     * @param examScores Sınav notları dizisi [vize1, vize2, final] (0-100 arası)
     * @param homeworkScores Ödev notları dizisi (0-100 arası, değişken uzunlukta)
     * @param projectScores Proje notları dizisi (0-100 arası, değişken uzunlukta)
     * @param attendanceRates Her ders için devam oranları dizisi (%0-100)
     * @param extraActivities Ekstra aktivite puanları (konferans, yarışma vb.)
     * @param disciplinaryPoints Disiplin ceza puanları (negatif etki)
     * @param scholarshipApplicant Burs başvurusu var mı?
     * @param previousGPA Önceki dönem GPA (0.0-4.0 arası)
     * @return Final akademik not (0.0-100.0 arası)
     */
    public double calculateFinalGrade(double[] examScores, double[] homeworkScores, 
                                     double[] projectScores, double[] attendanceRates,
                                     int[] extraActivities, int disciplinaryPoints,
                                     boolean scholarshipApplicant, double previousGPA) {
        
        // ============ 1. INPUT VALIDASYONLARI ============
        
        // Exam scores kontrolü
        if (examScores == null || examScores.length == 0) {
            throw new IllegalArgumentException("Exam scores cannot be null or empty");
        }
        
        if (examScores.length != 3) {
            throw new IllegalArgumentException("Exactly 3 exam scores required (midterm1, midterm2, final)");
        }
        
        for (int i = 0; i < examScores.length; i++) {
            if (examScores[i] < 0 || examScores[i] > 100) {
                throw new IllegalArgumentException("Exam score must be between 0-100: " + examScores[i]);
            }
        }
        
        // Homework scores kontrolü
        if (homeworkScores == null) {
            throw new IllegalArgumentException("Homework scores cannot be null");
        }
        
        for (int i = 0; i < homeworkScores.length; i++) {
            if (homeworkScores[i] < 0 || homeworkScores[i] > 100) {
                throw new IllegalArgumentException("Homework score must be between 0-100: " + homeworkScores[i]);
            }
        }
        
        // Project scores kontrolü
        if (projectScores == null) {
            throw new IllegalArgumentException("Project scores cannot be null");
        }
        
        for (int i = 0; i < projectScores.length; i++) {
            if (projectScores[i] < 0 || projectScores[i] > 100) {
                throw new IllegalArgumentException("Project score must be between 0-100: " + projectScores[i]);
            }
        }
        
        // Attendance kontrolü
        if (attendanceRates == null || attendanceRates.length == 0) {
            throw new IllegalArgumentException("Attendance rates cannot be null or empty");
        }
        
        for (int i = 0; i < attendanceRates.length; i++) {
            if (attendanceRates[i] < 0 || attendanceRates[i] > 100) {
                throw new IllegalArgumentException("Attendance rate must be between 0-100: " + attendanceRates[i]);
            }
        }
        
        // Extra activities kontrolü
        if (extraActivities == null) {
            throw new IllegalArgumentException("Extra activities cannot be null");
        }
        
        for (int i = 0; i < extraActivities.length; i++) {
            if (extraActivities[i] < 0) {
                throw new IllegalArgumentException("Extra activity points cannot be negative: " + extraActivities[i]);
            }
        }
        
        // Disciplinary points kontrolü
        if (disciplinaryPoints < 0) {
            throw new IllegalArgumentException("Disciplinary points cannot be negative");
        }
        
        // Previous GPA kontrolü
        if (previousGPA < 0.0 || previousGPA > 4.0) {
            throw new IllegalArgumentException("Previous GPA must be between 0.0-4.0: " + previousGPA);
        }
        
        // ============ 2. SINAV NOTLARI HESAPLAMA ============
        
        double midterm1 = examScores[0];
        double midterm2 = examScores[1];
        double finalExam = examScores[2];
        
        // Final sınavı başarısız ise (< 40), direkt kalır
        if (finalExam < 40) {
            return finalExam * 0.5; // Düşük not döndür
        }
        
        // Sınav ortalaması hesapla (ağırlıklı)
        // Vize 1: %20, Vize 2: %25, Final: %55
        double examAverage = (midterm1 * 0.20) + (midterm2 * 0.25) + (finalExam * 0.55);
        
        // İki vize ortalaması 50'nin altındaysa, final ağırlığı artar
        double midtermAvg = (midterm1 + midterm2) / 2.0;
        if (midtermAvg < 50) {
            // Final %65'e çıkar, vizeler %35'e düşer
            examAverage = (midterm1 * 0.15) + (midterm2 * 0.20) + (finalExam * 0.65);
        }
        
        // ============ 3. ÖDEV NOTLARI HESAPLAMA ============
        
        double homeworkAverage = 0.0;
        double homeworkContribution = 0.0;
        
        if (homeworkScores.length > 0) {
            // En düşük iki ödevi at (eğer 3'ten fazla ödev varsa)
            if (homeworkScores.length > 3) {
                // Diziyi kopyala ve sırala
                double[] sortedHomeworks = new double[homeworkScores.length];
                for (int i = 0; i < homeworkScores.length; i++) {
                    sortedHomeworks[i] = homeworkScores[i];
                }
                
                // Basit bubble sort
                for (int i = 0; i < sortedHomeworks.length - 1; i++) {
                    for (int j = 0; j < sortedHomeworks.length - i - 1; j++) {
                        if (sortedHomeworks[j] > sortedHomeworks[j + 1]) {
                            double temp = sortedHomeworks[j];
                            sortedHomeworks[j] = sortedHomeworks[j + 1];
                            sortedHomeworks[j + 1] = temp;
                        }
                    }
                }
                
                // En düşük 2'yi atla, geri kalanın ortalamasını al
                double sum = 0;
                int count = 0;
                for (int i = 2; i < sortedHomeworks.length; i++) {
                    sum += sortedHomeworks[i];
                    count++;
                }
                homeworkAverage = count > 0 ? sum / count : 0;
                
            } else {
                // Tüm ödevlerin ortalaması
                double sum = 0;
                for (int i = 0; i < homeworkScores.length; i++) {
                    sum += homeworkScores[i];
                }
                homeworkAverage = sum / homeworkScores.length;
            }
            
            // Ödev katkısı: %15
            homeworkContribution = homeworkAverage * 0.15;
            
            // Bonus: Tüm ödevler 80+ ise ekstra %5 bonus
            boolean allHighScores = true;
            for (int i = 0; i < homeworkScores.length; i++) {
                if (homeworkScores[i] < 80) {
                    allHighScores = false;
                    break;
                }
            }
            
            if (allHighScores && homeworkScores.length >= 3) {
                homeworkContribution += 5.0; // +5 bonus puan
            }
            
        } else {
            // Ödev yoksa, sınav ağırlığı artar
            examAverage = examAverage * 1.15; // %15 artış
        }
        
        // ============ 4. PROJE NOTLARI HESAPLAMA ============
        
        double projectAverage = 0.0;
        double projectContribution = 0.0;
        
        if (projectScores.length > 0) {
            // En yüksek ve en düşük proje notlarını bul
            double minProject = projectScores[0];
            double maxProject = projectScores[0];
            double sum = 0;
            
            for (int i = 0; i < projectScores.length; i++) {
                sum += projectScores[i];
                if (projectScores[i] < minProject) {
                    minProject = projectScores[i];
                }
                if (projectScores[i] > maxProject) {
                    maxProject = projectScores[i];
                }
            }
            
            // 3'ten fazla proje varsa, en düşüğü at
            if (projectScores.length > 3) {
                sum -= minProject;
                projectAverage = sum / (projectScores.length - 1);
            } else {
                projectAverage = sum / projectScores.length;
            }
            
            // Proje katkısı: %10
            projectContribution = projectAverage * 0.10;
            
            // En yüksek proje 95+ ise bonus
            if (maxProject >= 95) {
                projectContribution += 3.0; // +3 bonus puan
            }
            
        } else {
            // Proje yoksa ceza
            projectContribution = -5.0; // -5 puan ceza
        }
        
        // ============ 5. DEVAMSIZLIK DEĞERLENDİRMESİ ============
        
        // Ortalama devam oranı hesapla
        double totalAttendance = 0;
        for (int i = 0; i < attendanceRates.length; i++) {
            totalAttendance += attendanceRates[i];
        }
        double averageAttendance = totalAttendance / attendanceRates.length;
        
        double attendanceImpact = 0.0;
        
        // Devam durumuna göre etki
        if (averageAttendance >= 95) {
            attendanceImpact = 5.0; // Mükemmel devam: +5 puan
        } else if (averageAttendance >= 90) {
            attendanceImpact = 3.0; // Çok iyi: +3 puan
        } else if (averageAttendance >= 85) {
            attendanceImpact = 2.0; // İyi: +2 puan
        } else if (averageAttendance >= 80) {
            attendanceImpact = 0.0; // Yeterli: etki yok
        } else if (averageAttendance >= 70) {
            attendanceImpact = -5.0; // Düşük: -5 puan
        } else if (averageAttendance >= 60) {
            attendanceImpact = -10.0; // Çok düşük: -10 puan
        } else {
            // %60'ın altında devam: Kalır
            return 25.0; // Direkt düşük not
        }
        
        // Herhangi bir derste %50'nin altında devam varsa ekstra ceza
        for (int i = 0; i < attendanceRates.length; i++) {
            if (attendanceRates[i] < 50) {
                attendanceImpact -= 10.0; // Her düşük devam için -10
            }
        }
        
        // ============ 6. EKSTRA AKTİVİTELER ============
        
        double extraPoints = 0.0;
        
        if (extraActivities.length > 0) {
            // Toplam aktivite puanı hesapla
            int totalExtraPoints = 0;
            for (int i = 0; i < extraActivities.length; i++) {
                totalExtraPoints += extraActivities[i];
            }
            
            // Maximum 10 puan bonus
            if (totalExtraPoints > 100) {
                extraPoints = 10.0;
            } else {
                extraPoints = totalExtraPoints / 10.0; // Her 10 puan = +1 not
            }
            
            // 5'ten fazla aktivite varsa ekstra bonus
            if (extraActivities.length > 5) {
                extraPoints += 2.0;
            }
        }
        
        // ============ 7. DİSİPLİN CEZALARI ============
        
        double disciplinaryImpact = 0.0;
        
        if (disciplinaryPoints > 0) {
            // Her ceza puanı için -2 puan
            disciplinaryImpact = disciplinaryPoints * -2.0;
            
            // 3'ten fazla ceza varsa ekstra ceza
            if (disciplinaryPoints > 3) {
                disciplinaryImpact -= 5.0;
            }
            
            // 5'ten fazla ceza varsa ağır ceza
            if (disciplinaryPoints > 5) {
                disciplinaryImpact -= 10.0;
            }
        }
        
        // ============ 8. BURS BAŞVURUSU DEĞERLENDİRMESİ ============
        
        double scholarshipBonus = 0.0;
        
        if (scholarshipApplicant) {
            // Burs için yüksek standartlar
            // Önceki GPA'ya göre bonus
            if (previousGPA >= 3.8) {
                scholarshipBonus = 5.0; // Mükemmel GPA: +5 puan
            } else if (previousGPA >= 3.5) {
                scholarshipBonus = 3.0; // Çok iyi GPA: +3 puan
            } else if (previousGPA >= 3.0) {
                scholarshipBonus = 1.0; // İyi GPA: +1 puan
            } else {
                scholarshipBonus = 0.0; // Düşük GPA: bonus yok
            }
            
            // Devam oranı düşükse burs bonusu iptal
            if (averageAttendance < 85) {
                scholarshipBonus = 0.0;
            }
            
            // Disiplin cezası varsa burs bonusu iptal
            if (disciplinaryPoints > 0) {
                scholarshipBonus = 0.0;
            }
            
            // Tüm sınav notları 70+ ise ekstra bonus
            if (midterm1 >= 70 && midterm2 >= 70 && finalExam >= 70) {
                scholarshipBonus += 2.0;
            }
        }
        
        // ============ 9. FINAL NOT HESAPLAMA ============
        
        double finalGrade = examAverage + homeworkContribution + projectContribution 
                          + attendanceImpact + extraPoints + disciplinaryImpact 
                          + scholarshipBonus;
        
        // ============ 10. PERFORMANS AYARLAMALARI ============
        
        // Final sınavı çok yüksekse (90+), genel notu yukarı çek
        if (finalExam >= 90) {
            double boost = (finalExam - 90) * 0.3; // Her puan için %30 katkı
            finalGrade += boost;
        }
        
        // İki vize de çok düşükse (<40), ceza uygula
        if (midterm1 < 40 && midterm2 < 40) {
            finalGrade *= 0.85; // %15 ceza
        }
        
        // Tutarlılık kontrolü: Tüm sınavlar birbirine yakınsa bonus
        // Standart sapma hesapla (inline)
        double examStdDev = 0.0;
        if (examScores.length > 0) {
            // Ortalama hesapla
            double examSum = 0;
            for (int i = 0; i < examScores.length; i++) {
                examSum += examScores[i];
            }
            double examMean = examSum / examScores.length;
            
            // Varyans hesapla
            double examVariance = 0;
            for (int i = 0; i < examScores.length; i++) {
                double diff = examScores[i] - examMean;
                examVariance += diff * diff;
            }
            examVariance = examVariance / examScores.length;
            
            // Standart sapma
            examStdDev = Math.sqrt(examVariance);
        }
        
        if (examStdDev < 10) { // Düşük standart sapma = tutarlı performans
            finalGrade += 2.0;
        }
        
        // Ödev ve proje ortalamaları arasında büyük fark varsa dikkat
        if (projectScores.length > 0 && homeworkScores.length > 0) {
            double difference = Math.abs(projectAverage - homeworkAverage);
            if (difference > 30) {
                // Tutarsızlık: muhtemelen kopya
                finalGrade *= 0.90; // %10 ceza
            }
        }
        
        // ============ 11. FINAL SINIRLANDIRMA ============
        
        // Not 0-100 arasında olmalı
        if (finalGrade < 0) {
            finalGrade = 0.0;
        } else if (finalGrade > 100) {
            finalGrade = 100.0;
        }
        
        // Hassasiyet: 2 ondalık basamak
        finalGrade = Math.round(finalGrade * 100.0) / 100.0;
        
        return finalGrade;
    }
}
