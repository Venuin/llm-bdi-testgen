package app;

/**
 * E-Ticaret Sipariş Fiyat Hesaplama Motoru
 * 

 * Hesaplama Kuralları:
 * - Müşteri Tipleri: STANDARD, PREMIUM, VIP
 * - Vergi Oranı: %18
 * - Ücretsiz Kargo Eşiği: 150 TL
 * - Standart Kargo: 29.90 TL
 * - İndirim Uygulamaları müşteri tipine göre değişir
 * - Tatil günlerinde ekstra indirimler
 * - Toplu alımlarda ek indirimler
 */
public class OrderProcessor {
    
    /**
     * Kapsamlı sipariş fiyat hesaplama
     * 
     * @param customerType Müşteri tipi ("STANDARD", "PREMIUM", "VIP")
     * @param totalAmount Toplam ürün tutarı
     * @param itemCount Ürün adedi
     * @param isWeekend Hafta sonu siparişi mi?
     * @param isHoliday Tatil günü siparişi mi?
     * @param couponCode Kupon kodu (null olabilir)
     * @param loyaltyPoints Sadakat puanları (0-1000 arası)
     * @return Nihai sipariş tutarı (vergi ve kargo dahil)
     */
    public double calculateOrderPrice(String customerType, double totalAmount, int itemCount, 
                                     boolean isWeekend, boolean isHoliday, 
                                     String couponCode, int loyaltyPoints) {
        
        // Input validasyonları
        if (customerType == null || customerType.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer type cannot be null or empty");
        }
        
        if (totalAmount < 0) {
            throw new IllegalArgumentException("Total amount cannot be negative");
        }
        
        if (totalAmount == 0) {
            return 0.0;
        }
        
        if (itemCount <= 0) {
            throw new IllegalArgumentException("Item count must be positive");
        }
        
        if (loyaltyPoints < 0 || loyaltyPoints > 1000) {
            throw new IllegalArgumentException("Loyalty points must be between 0 and 1000");
        }
        
        // Müşteri tipi kontrolü
        String normalizedCustomerType = customerType.trim().toUpperCase();
        if (!normalizedCustomerType.equals("STANDARD") && 
            !normalizedCustomerType.equals("PREMIUM") && 
            !normalizedCustomerType.equals("VIP")) {
            throw new IllegalArgumentException("Invalid customer type: " + customerType);
        }
        
        // Hesaplamaya başla
        double currentAmount = totalAmount;
        double totalDiscount = 0.0;
        
        // 1. MÜŞTERİ TİPİNE GÖRE TEMEL İNDİRİM
        double customerDiscount = 0.0;
        
        if (normalizedCustomerType.equals("STANDARD")) {
            // Standard müşteri - sadece yüksek tutarlarda indirim
            if (currentAmount >= 5000) {
                customerDiscount = currentAmount * 0.05; // %5
            } else if (currentAmount >= 2000) {
                customerDiscount = currentAmount * 0.02; // %2
            }
            // Altındaki tutarlarda indirim yok
        } else if (normalizedCustomerType.equals("PREMIUM")) {
            // Premium müşteri - kademeli indirim
            if (currentAmount >= 5000) {
                customerDiscount = currentAmount * 0.12; // %12
            } else if (currentAmount >= 2000) {
                customerDiscount = currentAmount * 0.10; // %10
            } else if (currentAmount >= 1000) {
                customerDiscount = currentAmount * 0.08; // %8
            } else if (currentAmount >= 500) {
                customerDiscount = currentAmount * 0.05; // %5
            } else {
                customerDiscount = currentAmount * 0.03; // %3
            }
        } else if (normalizedCustomerType.equals("VIP")) {
            // VIP müşteri - yüksek indirimler
            if (currentAmount >= 10000) {
                customerDiscount = currentAmount * 0.25; // %25
            } else if (currentAmount >= 5000) {
                customerDiscount = currentAmount * 0.20; // %20
            } else if (currentAmount >= 2000) {
                customerDiscount = currentAmount * 0.15; // %15
            } else if (currentAmount >= 1000) {
                customerDiscount = currentAmount * 0.12; // %12
            } else {
                customerDiscount = currentAmount * 0.10; // %10
            }
        }
        
        totalDiscount += customerDiscount;
        currentAmount -= customerDiscount;
        
        // 2. TOPLU ALIMLARDA EK İNDİRİM
        double bulkDiscount = 0.0;
        
        if (itemCount >= 100) {
            bulkDiscount = currentAmount * 0.15; // %15 toplu alım indirimi
        } else if (itemCount >= 50) {
            bulkDiscount = currentAmount * 0.10; // %10
        } else if (itemCount >= 20) {
            bulkDiscount = currentAmount * 0.07; // %7
        } else if (itemCount >= 10) {
            bulkDiscount = currentAmount * 0.05; // %5
        } else if (itemCount >= 5) {
            bulkDiscount = currentAmount * 0.03; // %3
        }
        
        // VIP müşteriler için toplu alım indirimi %50 daha fazla
        if (normalizedCustomerType.equals("VIP") && bulkDiscount > 0) {
            bulkDiscount = bulkDiscount * 1.5;
        }
        
        totalDiscount += bulkDiscount;
        currentAmount -= bulkDiscount;
        
        // 3. ÖZEL GÜN İNDİRİMLERİ
        double specialDayDiscount = 0.0;
        
        if (isHoliday) {
            // Tatil günü özel indirimi
            if (normalizedCustomerType.equals("VIP")) {
                specialDayDiscount = currentAmount * 0.10; // %10
            } else if (normalizedCustomerType.equals("PREMIUM")) {
                specialDayDiscount = currentAmount * 0.08; // %8
            } else {
                specialDayDiscount = currentAmount * 0.05; // %5
            }
        } else if (isWeekend) {
            // Hafta sonu indirimi (tatil günü değilse)
            if (normalizedCustomerType.equals("VIP")) {
                specialDayDiscount = currentAmount * 0.05; // %5
            } else if (normalizedCustomerType.equals("PREMIUM")) {
                specialDayDiscount = currentAmount * 0.03; // %3
            }
            // Standard müşterilere hafta sonu indirimi yok
        }
        
        totalDiscount += specialDayDiscount;
        currentAmount -= specialDayDiscount;
        
        // 4. KUPON KODU
        double couponDiscount = 0.0;
        
        if (couponCode != null && !couponCode.trim().isEmpty()) {
            String normalizedCoupon = couponCode.trim().toUpperCase();
            
            if (normalizedCoupon.equals("WELCOME10")) {
                // Yeni müşteri kuponu - sadece STANDARD için
                if (normalizedCustomerType.equals("STANDARD")) {
                    couponDiscount = currentAmount * 0.10; // %10
                }
            } else if (normalizedCoupon.equals("SUMMER20")) {
                // Yaz kampanyası
                couponDiscount = currentAmount * 0.20; // %20
            } else if (normalizedCoupon.equals("FLASH30")) {
                // Flash kampanya - minimum 1000 TL
                if (totalAmount >= 1000) {
                    couponDiscount = currentAmount * 0.30; // %30
                }
            } else if (normalizedCoupon.equals("VIP50")) {
                // Sadece VIP müşteriler kullanabilir
                if (normalizedCustomerType.equals("VIP") && totalAmount >= 2000) {
                    couponDiscount = currentAmount * 0.50; // %50
                }
            } else if (normalizedCoupon.equals("PREMIUM25")) {
                // Premium ve VIP kullanabilir
                if (normalizedCustomerType.equals("PREMIUM") || normalizedCustomerType.equals("VIP")) {
                    couponDiscount = currentAmount * 0.25; // %25
                }
            } else if (normalizedCoupon.startsWith("SAVE")) {
                // Dinamik kupon - SAVE ile başlayan
                try {
                    String percentStr = normalizedCoupon.substring(4);
                    int percent = Integer.parseInt(percentStr);
                    if (percent > 0 && percent <= 15) {
                        couponDiscount = currentAmount * (percent / 100.0);
                    }
                } catch (Exception e) {
                    // Geçersiz kupon - indirim yok
                }
            }
            
            // Kupon kullanımı toplu alım indirimi ile birleştirilemez (en yüksek geçerli)
            if (bulkDiscount > 0 && couponDiscount > 0) {
                if (bulkDiscount > couponDiscount) {
                    couponDiscount = 0;
                } else {
                    // Kupon indirimini kullan, toplu alımı iptal et
                    currentAmount += bulkDiscount;
                    totalDiscount -= bulkDiscount;
                }
            }
        }
        
        totalDiscount += couponDiscount;
        currentAmount -= couponDiscount;
        
        // 5. SADAKAT PUANLARI İNDİRİMİ
        double loyaltyDiscount = 0.0;
        
        if (loyaltyPoints > 0) {
            // Her 100 puan = %1 indirim (max %10)
            double loyaltyPercent = (loyaltyPoints / 100.0) / 100.0;
            if (loyaltyPercent > 0.10) {
                loyaltyPercent = 0.10;
            }
            
            loyaltyDiscount = currentAmount * loyaltyPercent;
            
            // Premium ve VIP müşteriler için puan değeri 2x
            if (normalizedCustomerType.equals("PREMIUM") || normalizedCustomerType.equals("VIP")) {
                loyaltyDiscount *= 2;
                
                // Maximum %15'e çıkar
                if (loyaltyDiscount > currentAmount * 0.15) {
                    loyaltyDiscount = currentAmount * 0.15;
                }
            }
        }
        
        totalDiscount += loyaltyDiscount;
        currentAmount -= loyaltyDiscount;
        
        // 6. MİNİMUM TUTAR KONTROLÜ
        // İndirimler toplamı orijinal tutarın %80'ini geçemez
        if (totalDiscount > totalAmount * 0.80) {
            double excessDiscount = totalDiscount - (totalAmount * 0.80);
            totalDiscount = totalAmount * 0.80;
            currentAmount += excessDiscount;
        }
        
        // Negatif olmadığından emin ol
        if (currentAmount < 0) {
            currentAmount = 0;
        }
        
        // 7. VERGİ HESAPLAMA
        double tax = currentAmount * 0.18; // %18 KDV
        currentAmount += tax;
        
        // 8. KARGO ÜCRETİ
        double shippingCost = 0.0;
        
        // VIP müşterilere her zaman ücretsiz kargo
        if (normalizedCustomerType.equals("VIP")) {
            shippingCost = 0.0;
        } 
        // Ücretsiz kargo eşiği kontrolü (vergi öncesi)
        else if ((currentAmount - tax) >= 150.0) {
            shippingCost = 0.0;
        }
        // Premium müşteriler için indirimli kargo
        else if (normalizedCustomerType.equals("PREMIUM")) {
            shippingCost = 19.90;
        }
        // Standard kargo
        else {
            shippingCost = 29.90;
        }
        
        // Tatil günlerinde kargo ücretsiz (VIP zaten ücretsiz)
        if (isHoliday && !normalizedCustomerType.equals("VIP")) {
            shippingCost = 0.0;
        }
        
        currentAmount += shippingCost;
        
        // 9. YUVARLAMA
        // Kuruş yuvarlaması - en yakın 0.05'e yuvarla
        currentAmount = Math.round(currentAmount * 20.0) / 20.0;
        
        return currentAmount;
    }
}
