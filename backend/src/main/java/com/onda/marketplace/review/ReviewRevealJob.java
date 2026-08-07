package com.onda.marketplace.review;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Double-blind: se a contraparte nunca avalia, a avaliação escrita não pode ficar
 * oculta pra sempre — quem avaliou seria punido pelo silêncio do outro. Vencido o
 * prazo de reciprocidade ({@code marketplace.review.reveal-deadline-days}), ela é
 * publicada de qualquer forma.
 */
@Component
public class ReviewRevealJob {

    private static final Logger log = LoggerFactory.getLogger(ReviewRevealJob.class);

    private final ReviewService reviewService;

    public ReviewRevealJob(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Scheduled(fixedDelayString = "${marketplace.review.reveal-job-delay-ms:3600000}")
    public void revelarVencidas() {
        List<Review> vencidas = reviewService.buscarVencidas(Instant.now());
        if (vencidas.isEmpty()) return;

        reviewService.revelar(vencidas);
        log.info("Double-blind: {} avaliação(ões) revelada(s) por prazo vencido.", vencidas.size());
    }
}
